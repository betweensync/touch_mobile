package com.cloudant.andysync.observer;

import com.cloudant.andysync.sync.SyncEventListener;

public interface ObservationManager {
	public void start(String path);
	public void startRescursively(String path);
	public void stop(String path);
	public void stopRecursively(String path);
	public void stopAll();
	
	public String[] getObservationPaths();
	
	public void addSyncEventListener(SyncEventListener listener);
	public void removeEventListener(SyncEventListener listener);
	public void removeAllEventListener();
}
