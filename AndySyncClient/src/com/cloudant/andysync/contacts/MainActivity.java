package com.cloudant.andysync.contacts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ektorp.AttachmentInputStream;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.ViewResult.Row;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import caramel.macc.andysync.SFile;
import caramel.macc.andysync.ScanEvent;
import caramel.macc.andysync.ScanEventListener;

import com.cloudant.andysync.sync.SyncEvent;
import com.cloudant.andysync.sync.SyncEventListener;
import com.cloudant.andysync.sync.SyncManager;
import com.cloudant.andysync.sync.SyncManagerFactory;
import com.cloudant.andysync.util.ConsoleLogger;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;
import com.example.helloworld.R;

public class MainActivity extends Activity {

	TextView textView1;
	Button buttonStoM, buttonMerge, buttonMtoS, buttonViewDB;
	Account myAccount;
	ContentResolver mResolver;
	ContactsObserver observer;
	boolean scanCompleted= false;
	SyncManager syncManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Log.i(C.ID, "init");
		
		myAccount = createSyncAccount(this);
		Intent intent = new Intent(this, ContactsSyncAdapterService.class);
		startService(intent);

		mResolver = getContentResolver();
		observer = new ContactsObserver();
		mResolver.registerContentObserver(RawContacts.CONTENT_URI, true, observer);

		// Create event halder for button1
		textView1 = (TextView) findViewById(R.id.textView1);
//		textView1.setMaxLines(1000);
		textView1.setMovementMethod(new ScrollingMovementMethod());
		buttonStoM = (Button) findViewById(R.id.buttonStoM);
		buttonMerge = (Button) findViewById(R.id.buttonMerge);
		buttonMtoS = (Button) findViewById(R.id.buttonMtoS);
		buttonViewDB = (Button) findViewById(R.id.buttonViewDB);

		// create SyncManaqger..
		syncManager = SyncManagerFactory.factory.getAndySyncManager();
		syncManager.setScanEventListener(new MyScanEventListener());
		syncManager.setSyncEventListener(new MySyncEventListener());
		
//		syncManager.start("/mnt/extSdCard/DCIM/Camera");
		syncManager.start("/mnt/sdcard/DCIM/Camera");
//		syncManager.start("/mnt");
		
		buttonStoM.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}

				textView1.setText("");

				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				bundle.putInt("syncMode", C.SYNC_SERVER_TO_MOBILE);
				ContentResolver.requestSync(myAccount, AUTHORITY, bundle);

				return false;
			}
		});

		buttonMerge.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}
				
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				bundle.putInt("syncMode", C.SYNC_MERGE);
				ContentResolver.requestSync(myAccount, AUTHORITY, bundle);
				return false;
			}
		});

		buttonMtoS.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}

				textView1.setText("");

				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				bundle.putInt("syncMode", C.SYNC_MOBILE_TO_SERVER);
				ContentResolver.requestSync(myAccount, AUTHORITY, bundle);
				
				return false;
			}
		});

		buttonViewDB.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}

				textView1.setText("");

				// List all documents: contacts
				{
					CouchDbConnector dbConnector = ContactsSyncAdapterService.init(getApplicationContext(), myAccount, false);
					ViewQuery view = new ViewQuery().allDocs().includeDocs(true);
					ViewResult allRows = dbConnector.queryView(view);
					Iterator<Row> it = allRows.iterator();
					while(it.hasNext()) {
						Row node = it.next();
						textView1.append("CONTACT: " + node.getDocAsNode() + "\n");
					}
				}
				
				// List all documents: files
				{
					ViewQuery view = new ViewQuery().allDocs().includeDocs(true);
					ViewResult allRows = MainActivity.this.dbConnector.queryView(view);
					Iterator<Row> it = allRows.iterator();
					while(it.hasNext()) {
						Row node = it.next();
						textView1.append("FILE: " + node.getDocAsNode() + "\n");
					}
				}
				
				return false;
			}
		});

		//	File Metadata µø±‚»≠
		dbInit(getApplicationContext());
		
		File dir = new File("/mnt/sdcard/DCIM/Camera");
		File[] files = dir.listFiles();
		uploadFiles(files, SyncEvent.FILE_CREATED);
		
		Bundle bundle = new Bundle();
		bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		bundle.putInt("syncMode", C.SYNC_INIT);
		ContentResolver.requestSync(myAccount, AUTHORITY, bundle);
	}

	protected void managedQuery(Object object, Object object2, Object object3, Object object4) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Create a new dummy account for the sync adapter
	 * 
	 * @param context
	 *            The application context
	 */
	// The authority for the sync adapter's content provider
	public static final String AUTHORITY = "com.android.contacts";
	// An account type, in the form of a domain name
	public static final String ACCOUNT_TYPE = "com.example.helloworld";
	// The account name
	public static final String ACCOUNT = "dummyaccount";

	public static Account createSyncAccount(Context context) {
		// Create the account type and default account
		Account newAccount = new Account(ACCOUNT, ACCOUNT_TYPE);
		// Get an instance of the Android account manager
		AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);
		/*
		 * Add the account and account type, no password or user data If
		 * successful, return the Account object, otherwise report an error.
		 */
		if (accountManager.addAccountExplicitly(newAccount, null, null)) {
			/*
			 * If you don't set android:syncable="true" in in your <provider>
			 * element in the manifest, then call context.setIsSyncable(account,
			 * AUTHORITY, 1) here.
			 */
		} else {
			/*
			 * The account exists or some other error occurred. Log this, report
			 * it, or handle it internally.
			 */
		}

		return newAccount;
	}

	// https://www.grokkingandroid.com/use-contentobserver-to-listen-to-changes/
	// Description about handler...
	public class ContactsObserver extends ContentObserver {
		public ContactsObserver() {
			super(null);
			// TODO Auto-generated constructor stub
		}

		/*
		 * Define a method that's called when data in the observed content
		 * provider changes. This method signature is provided for compatibility
		 * with older platforms.
		 */
		@Override
		public void onChange(boolean selfChange) {
			/*
			 * Invoke the method signature available as of Android platform
			 * version 4.1, with a null URI.
			 */
			onChange(selfChange, null);
		}

		/*
		 * Define a method that's called when data in the observed content
		 * provider changes.
		 */
		@Override
		public void onChange(boolean selfChange, Uri changeUri) {
			Log.e(C.ID, "onChange");
			Log.e(C.ID, "selfChange: " + selfChange);
			/*
			 * Ask the framework to run your sync adapter. To maintain backward
			 * compatibility, assume that changeUri is null.
			 */
			Bundle bundle = new Bundle();
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
			bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			bundle.putInt("syncMode", C.SYNC_MOBILE_TO_SERVER);
			ContentResolver.requestSync(myAccount, AUTHORITY, bundle);
		}
	}
	
	public class MyScanEventListener implements ScanEventListener {
		@Override
		public void onScanEvent(ScanEvent event) {
			switch(event.getType()) {
			case STARTED:
				ConsoleLogger.log(MainActivity.this, textView1, "STARTED: " + event);
				break;
			case SCANNED:
				ConsoleLogger.log(MainActivity.this, textView1, "SCANNED: " + event.getScanned());
				break;
			case ENDED:
				ConsoleLogger.log(MainActivity.this, textView1, "EDDED: " + syncManager.getScannedMap());
				scanCompleted = true;
				break;
			}
		}
	}
	
	public class MySyncEventListener implements SyncEventListener {
		@Override
		public void onSyncEvent(SyncEvent syncEvent) {
			ConsoleLogger.log(MainActivity.this, textView1, syncEvent.toString());
			
			// do something along with event types..
			switch(syncEvent.getType()){
			case SyncEvent.DIR_CREATED:
				ConsoleLogger.log(MainActivity.this, textView1, "DIR_CREATED: " + syncEvent.getPath());
				break;
			case SyncEvent.DIR_DELETED:
				ConsoleLogger.log(MainActivity.this, textView1, "DIR_DELETED: " + syncEvent.getPath());
				break;
			case SyncEvent.FILE_CREATED:
				ConsoleLogger.log(MainActivity.this, textView1, "FILE_CREATED: " + syncEvent.getPath());
				break;
			case SyncEvent.FILE_DELETED:
				ConsoleLogger.log(MainActivity.this, textView1, "FILE_DELETED: " + syncEvent.getPath());
				break;
			case SyncEvent.FILE_MODIFIED:
				ConsoleLogger.log(MainActivity.this, textView1, "FILE_MODIFIED: " + syncEvent.getPath());
				break;
			}
			
			File[] files = new File[]{ new File(syncEvent.getPath()) };
			uploadFiles(files, syncEvent.getType());
		}
		
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();

		mResolver.unregisterContentObserver(observer);
	}

	CouchDbConnector dbConnector;
	void dbInit(Context context) {
		// TouchDB Connection
		// start TouchDB
		TDServer server = null;
		String filesDir = context.getFilesDir().getAbsolutePath();
		Log.i(C.ID, "FilesDir: " + filesDir);
		try {
			server = new TDServer(filesDir);
		} catch (IOException e) {
			Log.e("tag", "Error starting TDServer", e);
		}

		// start TouchDB-Ektorp adapter
		HttpClient httpClient = new TouchDBHttpClient(server);
		CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

		// create a local database
		dbConnector = dbInstance.createConnector("samsungfile", true);

		// push this database to the test replication server
		ReplicationCommand pushCommand = new ReplicationCommand.Builder().source("samsungfile")
				.target("http://jerryj3.cloudant.com/a_samsung_file").continuous(true).build();

		ReplicationStatus pushStatus = dbInstance.replicate(pushCommand);
		Log.i(C.ID, "DB push: " + pushStatus.isOk());

		// pull this database from the test replication server
		ReplicationCommand pullCommand = new ReplicationCommand.Builder().source("http://jerryj3.cloudant.com/a_samsung_file")
				.target("samsungfile").continuous(true).build();

		ReplicationStatus pullStatus = dbInstance.replicate(pullCommand);
		Log.i(C.ID, "DB pull: " + pullStatus.isOk());

		ChangesCommand cmd = new ChangesCommand.Builder().includeDocs(true).build();
		ChangeEventTask task = new ChangeEventTask(dbConnector, cmd);
		task.execute();
		Log.i(C.ID, "ChangeEventTask started");
	}

	public void uploadFiles(File[] files, int event) {
		Log.i(C.ID, "uploadFiles: " + files.length + ", " + event);
		
		Map<String, ObjectNode> allFiles = new HashMap<String, ObjectNode>();
		ViewQuery view = new ViewQuery().allDocs().includeDocs(true);
		ViewResult allDocs = dbConnector.queryView(view);
		for(Iterator<Row> it = allDocs.iterator(); it.hasNext();) {
			Row row = it.next();
			allFiles.put(row.getDocAsNode().get("absolutePath").getTextValue(), (ObjectNode)row.getDocAsNode());
		}
		
		for(int i = 0; i < files.length; i++) {
			File aFile = files[i];
			ObjectMapper om = new ObjectMapper();
			ObjectNode dbMeta = allFiles.get(aFile.getAbsolutePath());
			
			ObjectNode meta = om.createObjectNode();
			if(dbMeta != null) {
				meta = dbMeta;
			}
			
			meta.put("absolutePath", aFile.getAbsolutePath());
			meta.put("name", aFile.getName());
			meta.put("lastModified", aFile.lastModified());
			meta.put("isDirectory", aFile.isDirectory());
			meta.put("userid", "cloudantdemo");
			
			if(dbMeta == null) {
				if(event == SyncEvent.FILE_CREATED || event == SyncEvent.DIR_CREATED) {
					Log.i(C.ID, "CREATE FILE: " + meta);
					dbConnector.create(meta);
				}
				else {
					Log.i(C.ID, "CONFLICT: " + meta);
				}
			}
			else {
				if(event == SyncEvent.FILE_MODIFIED) {
					Log.i(C.ID, "UPDATE FILE: " + meta);
					dbConnector.update(meta);
				}
				else if(event == SyncEvent.FILE_DELETED || event == SyncEvent.DIR_DELETED) {
					Log.i(C.ID, "DELETE FILE: " + meta);
					dbConnector.delete(meta);
				}
				else {
					Log.i(C.ID, "CONFLICT: " + meta);
				}
			}
			
			if(event != SyncEvent.DIR_DELETED && event != SyncEvent.FILE_DELETED) {
				Log.i(C.ID, "FILE Attach: " + meta.get("_id"));
				try {
					FileInputStream fin = new FileInputStream(aFile);
					String contentType = "image/jpeg";
					AttachmentInputStream binary = new AttachmentInputStream("binary", fin, contentType);
//					dbConnector.createAttachment(meta.get("_id").getTextValue(), meta.get("_rev").getTextValue(), binary);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			Log.i(C.ID, "FILE Done: " + meta.get("_id"));
		}
	}
	
	public class ChangeEventTask extends ChangesFeedAsyncTask {
		public ChangeEventTask(CouchDbConnector couchDbConnector, ChangesCommand changesCommand) {
			super(couchDbConnector, changesCommand);
		}

		@Override
		protected void handleDocumentChange(DocumentChange change) {
			JsonNode doc = change.getDocAsNode();
			Log.i(C.ID, "FILE_DOC: " + doc);
		}
	}

	static {
		// TouchDB Initialization
		TDURLStreamHandlerFactory.registerSelfIgnoreError();
		Log.i(C.ID, "ContactsSyncAdapterService init");
	}
}
