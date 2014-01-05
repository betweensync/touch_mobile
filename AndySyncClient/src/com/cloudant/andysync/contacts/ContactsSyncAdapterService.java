package com.cloudant.andysync.contacts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
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
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings.Secure;
import android.util.Log;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class ContactsSyncAdapterService extends Service {
	final static boolean DEBUG = true;
	final static boolean TIMER = true;
	
	static {
		// TouchDB Initialization
		TDURLStreamHandlerFactory.registerSelfIgnoreError();
		Log.i(C.ID, "ContactsSyncAdapterService init");
	}

	private static SyncAdapterImpl sSyncAdapter = null;
	
	static boolean initialized = false;
	static ReplicationCommand pushCommand, pullCommand;
	static ReplicationStatus pushStatus, pullStatus;
	public static CouchDbConnector dbConnector;
	static Account account;
	static ConcurrentLinkedQueue<JsonNode> queue = new ConcurrentLinkedQueue<JsonNode>();
	static String deviceId;

	public ContactsSyncAdapterService() {
		super();
	}

	private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
		private Context mContext;

		public SyncAdapterImpl(Context context) {
			super(context, true);
			mContext = context;
			Log.i(C.ID, "SyncAdapterImpl init");
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
				SyncResult syncResult) {
			try {
				ContactsSyncAdapterService.performSync(mContext, account, extras, authority, provider, syncResult);
			} catch (OperationCanceledException e) {
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(C.ID, "ContactsSyncAdapterService.onBind");
		
		IBinder ret = null;
		ret = getSyncAdapter().getSyncAdapterBinder();
		return ret;
	}

	private SyncAdapterImpl getSyncAdapter() {
		if (sSyncAdapter == null)
			sSyncAdapter = new SyncAdapterImpl(this);
		return sSyncAdapter;
	}

	public static CouchDbConnector init(Context context, Account account, boolean replication) {
		if(initialized) {
			Log.i(C.ID, "INIT: Already initialized " + replication);
			return dbConnector;
		}
		Log.i(C.ID, "INIT: Initializing " + replication);
		
		ContactsSyncAdapterService.account = account;
		deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID); 
		Log.i(C.ID, "INIT: deviceId " + deviceId);

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
		dbConnector = dbInstance.createConnector("samsung", true);

		if(replication) {
			// push this database to the test replication server
			pushCommand = new ReplicationCommand.Builder().source("samsung")
					.target("http://jerryj3.cloudant.com/a_samsung").continuous(true).build();
	
			pushStatus = dbInstance.replicate(pushCommand);
			Log.i(C.ID, "DB push: " + pushStatus.isOk());
	
			// pull this database from the test replication server
			pullCommand = new ReplicationCommand.Builder().source("http://jerryj3.cloudant.com/a_samsung")
					.target("samsung").continuous(true).build();
	
			pullStatus = dbInstance.replicate(pullCommand);
			Log.i(C.ID, "DB pull: " + pullStatus.isOk());
	
			ChangesCommand cmd = new ChangesCommand.Builder().includeDocs(true).build();
			ChangeEventTask task = new ChangeEventTask(dbConnector, cmd);
			task.execute();
			Log.i(C.ID, "ChangeEventTask started");
		}
		
		initialized = true;
		return dbConnector;
	}

	public static class ChangeEventTask extends ChangesFeedAsyncTask {
		public ChangeEventTask(CouchDbConnector couchDbConnector, ChangesCommand changesCommand) {
			super(couchDbConnector, changesCommand);
		}

		@Override
		protected void handleDocumentChange(DocumentChange change) {
			JsonNode doc = change.getDocAsNode();
			if (doc.get("generated_by_mobile") == null || !doc.get("generated_by_mobile").getTextValue().equals(deviceId)) {
				if(DEBUG) Log.i(C.ID, "DOWN: " + doc);
//				queue.add(doc);
				
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				bundle.putInt("syncMode", C.SYNC_SERVER_TO_MOBILE);
				ContentResolver.requestSync(account, MainActivity.AUTHORITY, bundle);
			}
			else {
				if(DEBUG) Log.i(C.ID, "LOCAL: " + doc);
			}
		}
	}

	static int lastSync = C.SYNC_INIT;
	static int lastNumOfDB = 0;
	private static void performSync(Context context, Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncResult) throws OperationCanceledException {
		int syncMode = extras.getInt("syncMode");

		init(context, account, true);
		ContentResolver mContentResolver = context.getContentResolver();
		Log.i(C.ID, "performSync: " + syncMode);
		Log.i(C.ID, "lastSync: " + lastSync);
		
		ObjectMapper om = new ObjectMapper();

		long time = System.currentTimeMillis();
		
		if(syncMode == C.SYNC_MOBILE_TO_SERVER) {
			boolean deletedOrDirty = false;
			
			Cursor deletedContactsCursor = mContentResolver.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID }, RawContacts.DELETED + "=1", null, null);
			
			while(deletedContactsCursor.moveToNext()) {
				int rawContactId = deletedContactsCursor.getInt(0);
	
				Log.i(C.ID, "Deleted: " + rawContactId);
				
				int count = mContentResolver.delete(
					RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
					ContactsContract.RawContacts._ID + "=" + rawContactId, null);
				
				deletedOrDirty = true;
			}
			
			Cursor dirtyContactsCursor = mContentResolver.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID }, RawContacts.DIRTY + "=1", null, null);
	
			while(dirtyContactsCursor.moveToNext()) {
				int rawContactId = dirtyContactsCursor.getInt(0);
	
				Log.i(C.ID, "Dirty: " + rawContactId);
				
				ContentValues values = new ContentValues();
				values.clear();
				values.put(RawContacts.DIRTY , 0);
				mContentResolver.update(ContactsContract.RawContacts.CONTENT_URI, values,
						ContactsContract.RawContacts._ID + "=" + rawContactId, null);
				
				deletedOrDirty = true;
			}
		
			if(!deletedOrDirty) {
				Log.e(C.ID, "Ignore SYNC_MOBILE_TO_SERVER");
				return;
			}
		}
		
		if(TIMER) Log.i(C.ID, "Time1: " + (System.currentTimeMillis() - time));

		List<ObjectNode> serverContacts = new ArrayList<ObjectNode>();
		List<ObjectNode> serverContactsOnly = new ArrayList<ObjectNode>();
		ViewQuery view = new ViewQuery().allDocs().includeDocs(true);
		ViewResult allDocs = dbConnector.queryView(view);
		Iterator<Row> it = allDocs.iterator();
		while(it.hasNext()) {
			Row row = it.next();
			serverContacts.add((ObjectNode)row.getDocAsNode());
			serverContactsOnly.add((ObjectNode)row.getDocAsNode());
		}
		
		if(TIMER) Log.i(C.ID, "Time2: " + (System.currentTimeMillis() - time));

		
		if(lastSync == C.SYNC_SERVER_TO_MOBILE && syncMode == C.SYNC_MOBILE_TO_SERVER) {
			Log.i(C.ID, "lastNumOfDB: " + lastNumOfDB);
			
			if(lastNumOfDB != serverContacts.size()) {
				Log.e(C.ID, "Waiting SYNC_SERVER_TO_MOBILE");
				
				//	서버에서 모바일로 Sync 중이므로 나중에 다시 실행
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				bundle.putInt("syncMode", C.SYNC_MOBILE_TO_SERVER);
				ContentResolver.requestSync(account, MainActivity.AUTHORITY, bundle);
	
				syncMode = lastSync;
			}
		}
		
		HashMap<Integer, ObjectNode> allPhones = new HashMap<Integer, ObjectNode>();
		Cursor phoneCursor = mContentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
		int idxId = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID);
		int idxType = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
		int idxAddr = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
		while(phoneCursor.moveToNext()) {
			int rawContactId = phoneCursor.getInt(idxId);
			ObjectNode phones = allPhones.get(rawContactId);
			if(phones == null) {
				phones = om.createObjectNode();
				allPhones.put(rawContactId, phones);
			}
			int phoneType = phoneCursor.getInt(idxType);
			String phoneNumber = phoneCursor.getString(idxAddr);
			switch(phoneType) {
				case Phone.TYPE_MOBILE:
					phones.put("mobile", phoneNumber);
					break;
				case Phone.TYPE_HOME: 
					phones.put("home", phoneNumber);
					break;
				case Phone.TYPE_WORK: 
					phones.put("work", phoneNumber);
					break;
				default: 
					phones.put("other", phoneNumber);
			}
		}

		if(TIMER) Log.i(C.ID, "Time2.2: " + (System.currentTimeMillis() - time));

		HashMap<Integer, ObjectNode> allEmails = new HashMap<Integer, ObjectNode>();
		Cursor emailCursor = mContentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, null, null, null);
		idxId = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID);
		idxType = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
		idxAddr = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
		while(emailCursor.moveToNext()) {
			int rawContactId = emailCursor.getInt(idxId);
			ObjectNode emails = allEmails.get(rawContactId);
			if(emails == null) {
				emails = om.createObjectNode();
				allEmails.put(rawContactId, emails);
			}
			int emailType = emailCursor.getInt(idxType);
			String emailAddr = emailCursor.getString(idxAddr);
			String version = emailCursor.getString(emailCursor.getColumnIndex(ContactsContract.Data.DATA_VERSION));
			switch(emailType) {
				case Email.TYPE_MOBILE: 
					emails.put("mobile", emailAddr);
					break;
				case Email.TYPE_HOME: 
					emails.put("home", emailAddr);
					break;
				case Email.TYPE_WORK:
					emails.put("work", emailAddr);
					break;
				default: 
					emails.put("other", emailAddr);
			}
		}

		if(TIMER) Log.i(C.ID, "Time2.3: " + (System.currentTimeMillis() - time));

		String[] projection = { RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
				RawContacts.DISPLAY_NAME_PRIMARY, RawContacts.DISPLAY_NAME_ALTERNATIVE};
//		String[] projection = { RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
//				RawContacts.DELETED, RawContacts.DISPLAY_NAME_PRIMARY, RawContacts.DISPLAY_NAME_ALTERNATIVE,
//				RawContacts.DIRTY, RawContacts.VERSION };
//		String[] projection = null;
//		String selection = RawContacts.DIRTY + "=1";
		String selection = RawContacts.ACCOUNT_NAME + "=?";
		String[] selectionArgs = new String[]{ "cloudantdemo14@gmail.com" };
//		String orderBy = RawContacts.DISPLAY_NAME_PRIMARY + " ASC";
		String orderBy = null;
		
		Cursor contactsCursor = mContentResolver.query(RawContacts.CONTENT_URI, projection, selection,
				selectionArgs, orderBy);

		if(TIMER) Log.i(C.ID, "Time2.4: " + (System.currentTimeMillis() - time));

		ArrayList<ObjectNode> mobileContacts = new ArrayList<ObjectNode>();
		ArrayList<ObjectNode> mobileContactsOnly = new ArrayList<ObjectNode>();
		while(contactsCursor.moveToNext()) {
			ObjectNode aContact = om.createObjectNode(); 
			int rawContactId = contactsCursor.getInt(0);
			int deleted = contactsCursor.getInt(1);
			int dirty = contactsCursor.getInt(2);
			
			int colCount = contactsCursor.getColumnCount();
			aContact = om.createObjectNode(); 
			for (int i = 0; i < colCount; i++) {
				String colName = contactsCursor.getColumnName(i);
				String colVal = contactsCursor.getString(i);
				aContact.put(colName, colVal);
				
				if(DEBUG) Log.i(C.ID, colName + ":" + colVal);
			}

			if(allPhones.get(rawContactId) != null) {
				aContact.put("phones", allPhones.get(rawContactId));
				if(DEBUG) Log.i(C.ID, "" + aContact.get("phones"));
			}
			if(allEmails.get(rawContactId) != null) {
				aContact.put("emails", allEmails.get(rawContactId));
				if(DEBUG) Log.i(C.ID, "" + aContact.get("emails"));
			}
			
			mobileContacts.add(aContact);
			
			if(TIMER) Log.i(C.ID, "Time2.5: " + (System.currentTimeMillis() - time));
			
			//	Check if a mobile contact exists in server
			if(!removeFrom(serverContactsOnly, aContact)) {
				mobileContactsOnly.add(aContact);
			}

			if(TIMER) Log.i(C.ID, "Time2.6: " + (System.currentTimeMillis() - time));
		}
		
		if(TIMER) Log.i(C.ID, "Time3: " + (System.currentTimeMillis() - time));

		if(syncMode == C.SYNC_MOBILE_TO_SERVER || syncMode == C.SYNC_MERGE) {
			Log.i(C.ID, (syncMode == C.SYNC_MOBILE_TO_SERVER) ? "SYNC_MOBILE_TO_SERVER" : "SYNC_MERGE");
			
			if(syncMode == C.SYNC_MOBILE_TO_SERVER) {
				Iterator<ObjectNode> it3 = serverContactsOnly.iterator();
				while(it3.hasNext()) {
					ObjectNode aContact = it3.next();
					
					//	If a contact exists in only server
					aContact.put("generated_by_mobile", deviceId);
					Log.i(C.ID, "DEL FROM SERVER: " + aContact.toString());
					dbConnector.update(aContact);
					dbConnector.delete(aContact);
				}
				
				if(TIMER) Log.i(C.ID, "Time4: " + (System.currentTimeMillis() - time));
			}

			Iterator<ObjectNode> it2 = mobileContactsOnly.iterator();
			while(it2.hasNext()) {
				ObjectNode aContact = it2.next();
				
				//	If a contact doesn't exist in server 
				aContact.remove("_id");
				aContact.put("generated_by_mobile", deviceId);
				Log.i(C.ID, "ADD TO SERVER: " + aContact.toString());
				dbConnector.create(aContact);
			}
			
			if(TIMER) Log.i(C.ID, "Time5: " + (System.currentTimeMillis() - time));
		}
		else if(syncMode == C.SYNC_SERVER_TO_MOBILE || syncMode == C.SYNC_MERGE) {
			Log.i(C.ID, (syncMode == C.SYNC_SERVER_TO_MOBILE) ? "SYNC_SERVER_TO_MOBILE" : "SYNC_MERGE");
			lastNumOfDB = serverContacts.size();
			Log.i(C.ID, "NumOfDB: " + lastNumOfDB);

			if(syncMode == C.SYNC_SERVER_TO_MOBILE) {
				Iterator<ObjectNode> it2 = mobileContactsOnly.iterator();
				while(it2.hasNext()) {
					ObjectNode aContact = (ObjectNode)it2.next();
					
					//	If a contact exists in only mobile 
					Log.i(C.ID, "DEL FROM MOBILE: " + aContact);
	
					int count = mContentResolver.delete(
							RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
							ContactsContract.RawContacts._ID + "=" + aContact.get("_id"), null);
					
					if(DEBUG) Log.i(C.ID, "DEL Count: " + count);
				}

				if(TIMER) Log.i(C.ID, "Time6: " + (System.currentTimeMillis() - time));
			}

			Iterator<ObjectNode> it3 = serverContactsOnly.iterator();
			while(it3.hasNext()) {
				ObjectNode aContact = it3.next();
				
				//	If a contact doesn't exist in mobile
				Log.i(C.ID, "ADD TO MOBILE: " + aContact.toString());

				ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
				int rawContactInsertIndex = ops.size();
				ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
						.withValue(RawContacts.ACCOUNT_TYPE, aContact.get("account_type").getTextValue())
						.withValue(RawContacts.ACCOUNT_NAME, aContact.get("account_name").getTextValue())
						.withValue(RawContacts.DIRTY, 0)
						.build());
				
				ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
						.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
						.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
						.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, aContact.get("display_name").getTextValue())
						.build());
				
				//Phone Number
				JsonNode phones = aContact.get("phones");
				if(phones != null) {
					Iterator<String> it4 = phones.getFieldNames();
					while(it4.hasNext()) {
						String name = it4.next();
						int type = 0;
						if(name.equals("mobile")) type = Phone.TYPE_MOBILE;
						else if(name.equals("work")) type = Phone.TYPE_WORK;
						else if(name.equals("home")) type = Phone.TYPE_HOME;
						else type = Phone.TYPE_OTHER;
						ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
								.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
								.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
								.withValue(Phone.NUMBER, phones.get(name).getTextValue())
								.withValue(Phone.TYPE, type).build());
					}
				}
				
				//Email details
				JsonNode emails = aContact.get("emails");
				if(emails != null) {
					Iterator<String> it5 = emails.getFieldNames();
					while(it5.hasNext()) {
						String name = it5.next();
						int type = 0;
						if(name.equals("mobile")) type = Email.TYPE_MOBILE;
						else if(name.equals("work")) type = Email.TYPE_WORK;
						else if(name.equals("home")) type = Email.TYPE_HOME;
						else type = Email.TYPE_OTHER;
						ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
							.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
							.withValue(ContactsContract.CommonDataKinds.Email.DATA, emails.get(name).getTextValue())
							.withValue(ContactsContract.CommonDataKinds.Email.TYPE, type).build());
					}
				}
				
				try {
					mContentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
			    } 
				catch (RemoteException e) {
			            // TODO Auto-generated catch block
			            e.printStackTrace();
			    } 
				catch (OperationApplicationException e) {
			            // TODO Auto-generated catch block
			            e.printStackTrace();
			    }
			}

			if(TIMER) Log.i(C.ID, "Time7: " + (System.currentTimeMillis() - time));
		}
		
		lastSync = syncMode;
	}

	final static String[] fields = {"display_name", "display_name_alt", "account_name", "account_type", "phones", "emails"};
	private static boolean removeFrom(List<ObjectNode> from, JsonNode elem) {
		for(int i = 0; i < from.size(); i++) {
			JsonNode node = from.get(i);
			boolean match = true;
			for(int j = 0; j < fields.length; j++) {
				if(!(node.get(fields[j]) == null && elem.get(fields[j]) == null) && !node.get(fields[j]).equals(elem.get(fields[j]))) {
					match = false;
					break;
				}
			}
			
			if(match) {
				from.remove(i);
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.i(C.ID, "ContactsSyncAdapterService.onCreate");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Log.i(C.ID, "ContactsSyncAdapterService.onStartCommand");
//		startForeground(1, new Notification());
//		return super.onStartCommand(intent, flags, startId);
		return START_REDELIVER_INTENT;
	}
}
