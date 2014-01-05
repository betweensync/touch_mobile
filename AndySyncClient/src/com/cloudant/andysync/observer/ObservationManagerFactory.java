package com.cloudant.andysync.observer;

public class ObservationManagerFactory {
	private static ObservationManagerFactory omFactory = new ObservationManagerFactory();
	
	private ObservationManagerFactory(){
		
	}
	
	public static ObservationManagerFactory instance(){
		return omFactory;
	}
	
	public ObservationManager create(){
		return new AndroidObservationManager();
	}
}
