package com.cloudant.andysync.sync;

import java.util.Map;

import com.cloudant.andysync.observer.FileChangedEvent;
import com.cloudant.andysync.observer.FileChangedEventListener;
import com.cloudant.andysync.observer.ObservationManager;
import com.cloudant.andysync.observer.ObservationManagerFactory;
import com.cloudant.andysync.util.ConsoleLogger;
import com.cloudant.andysync.util.StringUtil;

import android.util.Log;

import caramel.macc.andysync.FileScanner;
import caramel.macc.andysync.SFile;
import caramel.macc.andysync.ScanEvent;
import caramel.macc.andysync.ScanEventListener;
import caramel.macc.andysync.core.FileScannerFactory;

public class AndySyncManager implements SyncManager, ScanEventListener {
	//private static AndySyncManager andySyncManager = new AndySyncManager();
	private static final String TAG = "AndySyncManager";  
	
	private FileScanner scanner;
	private ObservationManager obsManager;
	
	public AndySyncManager(){
		// init..
		this.init();		
	}
	
//	public static AndySyncManager instance(){
//		return andySyncManager;
//	}
	
	private void init(){
		this.scanner = FileScannerFactory.instance().createOptimizedFileScanner();
		this.scanner.addScanEventListener(this);
		
		this.obsManager = ObservationManagerFactory.instance().create();
		
		Log.d(TAG, "$ initialized...");
	}
	
	@Override
	public void start(String path) {
		Log.d(TAG, "$ starts.. path=" + path);
		
		// start scan..
		this.scanner.setBaseDir(path);
		this.scanner.start();
		
		Log.d(TAG, "$ started - scan.. ");
		
		// watch starts concurrently..
		this.obsManager.start(path);
		Log.d(TAG, "$ started - observe.. ");
		// for sub dir watching, see ScanEventListener implementation below..
	}


	@Override
	public void stop() {
		this.scanner.stop();
		this.obsManager.stopAll();
		
		this.init();
	}

	@Override
	public void setSyncEventListener(SyncEventListener syncListener) {
		if (this.obsManager != null){
			this.obsManager.addSyncEventListener(syncListener);
		}
	}

	@Override
	public void setScanEventListener(ScanEventListener scanListener) {
		if (this.scanner != null){
			this.scanner.addScanEventListener(scanListener);
		}
	}

	@Override
	public Map<String, SFile> getScannedMap() {
		return this.scanner.getSFileMap();
	}

	static long scannedFileCount = 0;
	static long started = 0;
	static long ended = 0;
	
	@Override
	public void onScanEvent(ScanEvent event) {
		switch (event.getType()) {
		case STARTED:
			this.started = System.currentTimeMillis();
			this.scannedFileCount = 0;
			
			break;
		case SCANNED:
			if (event.getScanned().size() == 0)
				return;
			
			// start observation if dir scanned..
			this.watchSubDir(event.getScanned());
			
			this.scannedFileCount += event.getScanned().size();
			break;
		case ENDED:			
			this.ended = System.currentTimeMillis();
			this.scanner.stop();
			
			break;
		}
		
	}
	
	private void watchSubDir(Map<String, SFile> scanned){
		for (SFile sf : scanned.values()){
			if (sf.isDirectory()){
				this.obsManager.start(sf.getAbsolutePath());
			}
		}
	}
}
