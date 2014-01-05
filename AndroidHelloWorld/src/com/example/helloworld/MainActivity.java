package com.example.helloworld;

import java.util.Iterator;
import java.util.List;

import org.ektorp.CouchDbConnector;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.ViewResult.Row;

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
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {

	EditText editText1;
	Button buttonStoM, buttonMerge, buttonMtoS, buttonViewDB;
	Account myAccount;
	ContentResolver mResolver;
	ContactsObserver observer;

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
		editText1 = (EditText) findViewById(R.id.editText1);
		buttonStoM = (Button) findViewById(R.id.buttonStoM);
		buttonMerge = (Button) findViewById(R.id.buttonMerge);
		buttonMtoS = (Button) findViewById(R.id.buttonMtoS);
		buttonViewDB = (Button) findViewById(R.id.buttonViewDB);
		buttonStoM.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return false;
				}

				editText1.setText("");

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

				editText1.setText("");

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

				editText1.setText("");

				// List all documents
				CouchDbConnector dbConnector = ContactsSyncAdapterService.init(getApplicationContext(), myAccount, false);
				ViewQuery view = new ViewQuery().allDocs().includeDocs(true);
				ViewResult allRows = dbConnector.queryView(view);
				Iterator<Row> it = allRows.iterator();
				while(it.hasNext()) {
					Row node = it.next();
					editText1.append("DOC: " + node.getDocAsNode() + "\n");
				}
				
				return false;
			}
		});

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

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();

		mResolver.unregisterContentObserver(observer);
	}
}
