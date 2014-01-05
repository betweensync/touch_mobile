package com.cloudant.andysync.sync;

public class SyncManagerFactory {
	public static SyncManagerFactory factory = new SyncManagerFactory();
	
	private SyncManagerFactory(){
		
	}
	
	public SyncManager getAndySyncManager(){
		return new AndySyncManager();
	}
}
