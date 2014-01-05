package com.cloudant.andysync.sync;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.cloudant.andysync.util.StringUtil;

import caramel.macc.andysync.SFile;

public class SyncEvent implements Serializable{
	public static final int FILE_CREATED	= 4;
	public static final int FILE_MODIFIED	= 8;
	public static final int FILE_DELETED	= 16;
	
	public static final int DIR_CREATED		= 1024;
	public static final int DIR_DELETED		= 2048;
	
	private String 	id;
	private int 	type;
	private String 	typeName;
	
	private String 	path;
	private Map<String, SFile> subFiles = Collections.synchronizedMap(new HashMap<String, SFile>());
	
	public SyncEvent(int type, String path){
		this.setType(type);
		this.setPath(path);
		
		// id generation..
		this.setId(StringUtil.generateSyncEventId());
	}
	
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
		
		switch(type){
		case SyncEvent.DIR_CREATED:
			this.typeName = "DIR_CREATED";
			break;
		case SyncEvent.DIR_DELETED:
			this.typeName = "DIR_DELETED";
			break;
		case SyncEvent.FILE_CREATED:
			this.typeName = "FILE_CREATED";
			break;
		case SyncEvent.FILE_DELETED:
			this.typeName = "FILE_DELETED";
			break;
		case SyncEvent.FILE_MODIFIED:
			this.typeName = "FILE_MODIFIED";
			break;
		}
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public Map<String, SFile> getSubFiles(){
		return this.subFiles;
	}
	
	public void setSubFiles(Map<String, SFile> subFiles){
		this.subFiles = subFiles;
	}
	@Override
	public String toString() {
		return "SyncEvent [id=" + id + ", type=" + type + ", typeName="
				+ typeName + ", path=" + path + "]";
	}
}
