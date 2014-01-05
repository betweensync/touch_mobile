package com.cloudant.andysync.observer;

import java.io.File;


public class FileChangedEvent {
	public static final int CREATE 		= 256; 	// FileObserver.CREATE;
	public static final int DELETE 		= 512; 	// FileObserver.DELETE;
	public static final int DELETE_SELF = 1024; // FileObserver.DELETE_SELF;
	public static final int MODIFY 		= 2; 	// FileObserver.MODIFY;
	public static final int MOVED_FROM 	= 64;	// FileObserver.MOVED_FROM;
	public static final int MOVED_TO 	= 128; 	// FileObserver.MOVED_TO;
	public static final int MOVE_SELF 	= 2048; // FileObserver.MOVE_SELF;
	
	public static final int DIR_CRATE 		= 1073742080;
	public static final int DIR_DELETE 		= 1073742336;
	public static final int DIR_MOVED_FROM 	= 1073741888;
	public static final int DIR_MOVED_TO 	= 1073741952;
	
	public static final int UNKNOWN 	= -1;
//	public static final int ACCESS = FileObserver.ACCESS;
//	public static final int ATTRIB = FileObserver.ATTRIB;
//	public static final int CLOSE_NOWRITE = FileObserver.CLOSE_NOWRITE;
//	public static final int CLOSE_WRITE = FileObserver.CLOSE_WRITE;
//	public static final int OPEN = FileObserver.OPEN;
	
	private int type	= UNKNOWN;
	private String path;
	private String typeStr;
	
	public FileChangedEvent(int type, String path){
		File f = new File(path);
		
//		if(f.isDirectory()){
//			switch(type){
//			case CREATE:
//				this.type = DIR_CRATE;
//				break;
//			case DELETE:
//				this.type = DIR_DELETE;
//				break;
//			}
//		} else{
			this.type = type;
//		}
		
		this.path = path;
		
		this.convertTypeToString();
	}
	
	public int getType(){
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	public String getTypeString(){
		return this.typeStr;
	}
	
	private void convertTypeToString(){
		switch(this.type){
		case CREATE:
			typeStr = "CREATE";
			break;
		case DELETE:
			typeStr = "DELETE";
			break;
		case DELETE_SELF:
			typeStr = "DELETE_SELF";
			break;
		case MODIFY:
			typeStr = "MODIFY";
			break;
		case MOVED_FROM:
			typeStr = "MOVED_FROM";
			break;
		case MOVED_TO:
			typeStr = "MOVED_TO";
			break;
		case MOVE_SELF:
			typeStr = "MOVE_SELF";
			break;
		case DIR_CRATE:
			typeStr = "DIR_CRATE";
			break;
		case DIR_DELETE:
			typeStr = "DIR_DELETE";
			break;
		case DIR_MOVED_FROM:
			typeStr = "DIR_MOVED_FROM";
			break;
		case DIR_MOVED_TO:
			typeStr = "DIR_MOVED_TO";
			break;
		default:
			typeStr = "UNKNOWN";
			break;
		}
	}
	
	@Override
	public String toString() {
		return "FileChangedEvent [type=" + typeStr + ", path=" + path + "]";
	}
}
