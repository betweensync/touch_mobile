package com.example.helloworld;

import java.io.IOException;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class MainActivity extends Activity {

	static {
		//	TouchDB Initialization
		TDURLStreamHandlerFactory.registerSelfIgnoreError();
	}
	
	EditText editText1, editText2;
	Button button1, button2;
	CouchDbInstance dbInstance;
	CouchDbConnector dbConnector;
	String ignoreId = "";
	ReplicationCommand pushCommand, pullCommand;
	ReplicationStatus pushStatus, pullStatus;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//	TouchDB Connection
		// start TouchDB
	    TDServer server = null;
	    String filesDir = getFilesDir().getAbsolutePath();
	    try {
	      server = new TDServer(filesDir);
	    } catch (IOException e) {
	      Log.e("tag", "Error starting TDServer", e);
	    }

	    // start TouchDB-Ektorp adapter
	    HttpClient httpClient = new TouchDBHttpClient(server);
	    dbInstance = new StdCouchDbInstance(httpClient);

	    // create a local database
	    dbConnector = dbInstance.createConnector("testdb", true);

	    // push this database to the test replication server
	    pushCommand = new ReplicationCommand.Builder()
        	.source("testdb")
	        .target("http://jerryj3.cloudant.com/a_user1")
	        .continuous(true)
	        .build();

	    pushStatus = dbInstance.replicate(pushCommand);
	    
	    // pull this database from the test replication server
	    pullCommand = new ReplicationCommand.Builder()
        	.source("http://jerryj3.cloudant.com/a_user1")
        	.target("testdb")
	        .continuous(true)
	        .build();

	    pullStatus = dbInstance.replicate(pullCommand);
	    
	    ChangesCommand cmd = new ChangesCommand.Builder().includeDocs(true).build();
	    ChangeEventTask task = new ChangeEventTask(dbConnector, cmd);
	    task.execute();
	    
	    //	Create event halder for button1
		editText1 = (EditText)findViewById(R.id.editText1);
		editText2 = (EditText)findViewById(R.id.editText2);
		button1 = (Button)findViewById(R.id.button1);
		button2 = (Button)findViewById(R.id.button2);
		button1.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}
				
//				editText1.append("STATUS: " + pushStatus.isOk() + "\n");
				
				ObjectMapper om = new ObjectMapper();
				ObjectNode newDoc = om.createObjectNode();
				String id = editText2.getText() + "_" + System.currentTimeMillis();
				ignoreId = id;
				newDoc.put("fileName", "file_" + System.currentTimeMillis());
				dbConnector.create(id, newDoc);
				
				editText1.append("UP : " + id + "\n");
				
				return false;
			}
		});
		
		button2.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				dbInstance.replicate(pushCommand);
				dbInstance.replicate(pullCommand);
				return false;
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public class ChangeEventTask extends ChangesFeedAsyncTask {
		public ChangeEventTask(CouchDbConnector couchDbConnector, ChangesCommand changesCommand) {
			super(couchDbConnector, changesCommand);
		}

		@Override
		protected void handleDocumentChange(DocumentChange change) {
			JsonNode doc = change.getDocAsNode();
			String id = doc.get("_id").asText();
			if(!id.equals(ignoreId)) {
				editText1.append("DOWN: " + id + "\n");
			}
		}
	}
}
