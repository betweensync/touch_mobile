package com.cloudant.andysync.observer;

import java.io.File;

import android.os.FileObserver;
import android.os.Handler;
import android.util.Log;

public class DirectoryObserver extends FileObserver{
    static final String TAG	= "DirectoryObserver";
	
    private String rootPath;
	private static final int mask = (FileObserver.CREATE | FileObserver.DELETE
			| FileObserver.MODIFY | FileObserver.MOVED_FROM
			| FileObserver.MOVED_TO | FileObserver.MOVE_SELF);
	
	private static final int maskAll = FileObserver.ALL_EVENTS; 
	
	private FileChangedEventListener fceListener;
	//private Handler handler;
	
	public DirectoryObserver(String root){
		super(root, mask);
		//super(root, maskAll);
		
		if (! root.endsWith(File.separator)){
			root += File.separator;
		}
		rootPath = root;
	}
	@Override
	public void onEvent(int event, String path) {
		FileChangedEvent fcevent = new FileChangedEvent(event, rootPath + path);// & FileObserver.ALL_EVENTS, rootPath + path);
		
		Log.d(TAG, "**["+ (event) + "] " + fcevent.toString());
		
		if (fcevent.getType() != FileChangedEvent.UNKNOWN){
			this.fceListener.onFileChanged(fcevent);
		}
	}
	
	public void addFileChangedEventListener(FileChangedEventListener fceListener){
		this.fceListener = fceListener;
	}

	public void close(){
		super.finalize();
	}
	
	

}
