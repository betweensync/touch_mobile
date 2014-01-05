package com.cloudant.andysync.observer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloudant.andysync.sync.SyncEvent;
import com.cloudant.andysync.sync.SyncEventListener;

import android.util.Log;
import caramel.macc.andysync.FileScanner;
import caramel.macc.andysync.SFile;
import caramel.macc.andysync.ScanEvent;
import caramel.macc.andysync.ScanEventListener;
import caramel.macc.andysync.core.FileScannerFactory;

public class AndroidObservationManager implements ObservationManager, FileChangedEventListener {
	//private static AndroidObservationManager aom = new AndroidObservationManager();
	private static final String TAG = "AndroidObservationManager";
	private Map<String, DirectoryObserver> observers = Collections.synchronizedMap(new HashMap<String, DirectoryObserver>());
	private List<SyncEventListener> listeners = new ArrayList<SyncEventListener>();
	
	public AndroidObservationManager(){
	}
	
//	public static AndroidObservationManager instance(){
//		return aom;
//	}
	
	@Override
	public void start(String path) {
		Log.d(TAG, "% observation starts..");
		
		File f = new File(path);
		
		if (!f.isDirectory())
			return;
		
		DirectoryObserver dObserver = this.observers.get(path);
		
		if (dObserver == null){
			this._startWatching(path);
		}
	}
	
	protected void _startWatching(String path){
		DirectoryObserver dObserver = new DirectoryObserver(path);
		dObserver.addFileChangedEventListener(this);
		dObserver.startWatching();
		
		this.observers.put(path, dObserver);
		Log.d(TAG, "% _startWatching - " + path);
	}
	
	protected void _startWatchingRecursively(String path){
		this._startWatching(path);
		
		File [] subfiles = new File(path).listFiles();
		for(File f : subfiles){
			if (f.isDirectory()){
				this._startWatchingRecursively(f.getAbsolutePath());
			}
		}
	}

	@Override
	public void stop(String path) {
		this._stopWatching(path);
	}
	
	protected void _stopWatching(String path){
		DirectoryObserver dObserver = this.observers.get(path);
		
		if (dObserver != null){
			dObserver.stopWatching();
			this.observers.remove(path);
			
			Log.d(TAG, "% _stopWatching - " + path);
		}
	}
	
	protected void _stopWatchingRecursively(String path){
		this._stopWatching(path);
		
		String [] paths = this.getObservationPaths();
		
		for (String opath : paths){
			if (opath.startsWith(path)){
				this._stopWatching(opath);
			}
		}
	}

	@Override
	public void stopAll() {
		for(DirectoryObserver dirObserver : this.observers.values()){
			dirObserver.stopWatching();
		}
		
		this.observers.clear();
		
		Log.d(TAG, "% stopAll");
	}

	@Override
	public String[] getObservationPaths() {
		return this.observers.keySet().toArray(new String[0]);
	}

	@Override
	public void addSyncEventListener(SyncEventListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeEventListener(SyncEventListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void removeAllEventListener() {
		this.listeners.clear();
	}

	@Override
	public void onFileChanged(FileChangedEvent fcevent) {
		SyncEvent syncEvent = null;
		String path = fcevent.getPath();

		Log.d(TAG, "% onFileChanged - " + fcevent);
		
		
		switch(fcevent.getType()){
			case FileChangedEvent.DIR_CRATE :
				this._startWatching(path);
				syncEvent = new SyncEvent(SyncEvent.DIR_CREATED, path);
				break;
			case FileChangedEvent.DIR_MOVED_FROM :
				new RecursionWorker(path).start();
				return;
			case FileChangedEvent.DIR_DELETE :
				this._stopWatchingRecursively(fcevent.getPath());
				syncEvent = new SyncEvent(SyncEvent.DIR_DELETED, path);
				break;
			case FileChangedEvent.DIR_MOVED_TO:
				this._stopWatchingRecursively(fcevent.getPath());
				syncEvent = new SyncEvent(SyncEvent.DIR_DELETED, path);
				break;
			case FileChangedEvent.CREATE :
				syncEvent = new SyncEvent(SyncEvent.FILE_CREATED, path);
				break;
			case FileChangedEvent.MOVED_FROM :
				syncEvent = new SyncEvent(SyncEvent.FILE_DELETED, path);
				break;
			case FileChangedEvent.DELETE :
				syncEvent = new SyncEvent(SyncEvent.FILE_DELETED, path);
				break;
			case FileChangedEvent.MOVED_TO :
				syncEvent = new SyncEvent(SyncEvent.FILE_CREATED, path);
				break;
			case FileChangedEvent.MODIFY :
				syncEvent = new SyncEvent(SyncEvent.FILE_MODIFIED, path);
				break;
			case FileChangedEvent.UNKNOWN :
				break;
		}
		
		if (syncEvent != null){
			this.dispatchEvent(syncEvent);
		}
	}
	
	protected void dispatchEvent(SyncEvent syncEvent){
		for(int inx = 0; inx < this.listeners.size(); inx++){
			this.listeners.get(inx).onSyncEvent(syncEvent);
		}
	}

	@Override
	public void startRescursively(String path) {
		this._startWatchingRecursively(path);
	}

	@Override
	public void stopRecursively(String path) {
		this._stopWatchingRecursively(path);
	}
	
	class RecursionWorker extends Thread implements ScanEventListener{
		private String path;
		private FileScanner scanner;
		
		public RecursionWorker(String path){
			this.path = path;
			this.scanner = FileScannerFactory.instance().createOptimizedFileScanner();
			this.scanner.setBaseDir(path);
			this.scanner.addScanEventListener(this);
		}
		
		public void run(){
			Log.d("RecursionWorker", "# scan starts - " + path);
			
			this.scanner.start();
			
			try {
				// wait until scanning is ended..
				synchronized(this.scanner){
					this.scanner.wait();
				}
			} catch (InterruptedException e) {
			}
		}

		@Override
		public void onScanEvent(ScanEvent scanEvent) {
			switch(scanEvent.getType()){
			case SCANNED:
				for (SFile sf : scanEvent.getScanned().values()){
					if (sf.isDirectory()){
						_startWatching(path);
					}
				}
				break;
			case ENDED:
				SyncEvent syncEvent = new SyncEvent(SyncEvent.DIR_CREATED, path);
				syncEvent.setSubFiles(this.scanner.getSFileMap());
				dispatchEvent(syncEvent);
				
				synchronized(this.scanner){
					this.scanner.notify();
				}
				
				Log.d("RecursionWorker", "# scan ends - " + path);
				
				break;
			}
		}
	}
}
