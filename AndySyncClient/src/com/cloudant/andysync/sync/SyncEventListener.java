package com.cloudant.andysync.sync;

public interface SyncEventListener {
	public void onSyncEvent(SyncEvent syncEvent);
}
