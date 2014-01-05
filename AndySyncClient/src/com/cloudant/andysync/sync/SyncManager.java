package com.cloudant.andysync.sync;

import java.util.Map;

import caramel.macc.andysync.SFile;
import caramel.macc.andysync.ScanEventListener;

public interface SyncManager {
	public void start(String path);
	public void stop();

	public void setSyncEventListener(SyncEventListener syncListener);
	public void setScanEventListener(ScanEventListener scanListener);
	
	public Map<String, SFile> getScannedMap();
}
