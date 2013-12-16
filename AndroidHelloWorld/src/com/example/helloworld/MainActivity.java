package com.example.helloworld;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class MainActivity extends Activity {

	static {
		//	TouchDB Initialization
		TDURLStreamHandlerFactory.registerSelfIgnoreError();
	}
	
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
	    CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

	    // create a local database
	    CouchDbConnector dbConnector = dbInstance.createConnector("testdb", true);

	    // push this database to the test replication server
	    ReplicationCommand pushCommand = new ReplicationCommand.Builder()
        	.source("testdb")
	        .target("http://samsungdemo.cloudant.net:5984/a_user1")
	        .continuous(true)
	        .build();

	    ReplicationStatus status = dbInstance.replicate(pushCommand);
	    
		ObjectMapper om = new ObjectMapper();
		ObjectNode newDoc = om.createObjectNode();
		newDoc.put("fileName", "file_" + System.currentTimeMillis());
		dbConnector.create(newDoc);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
