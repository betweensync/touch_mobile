package com.cloudant.andysync.util;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

public class ConsoleLogger {
	TextView console;
	
	public ConsoleLogger(TextView console){
		this.console = console;
	}
	
	public void log(String tag, String message){
		Log.i(tag, message);
		
		String old = this.console.getText().toString();		
		this.console.setText(message + "\r\n" + old);
	}
	
	public static void log(Activity act, final TextView console, final String message){
		act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
            	synchronized (console) {
            		String old = console.getText().toString();	
            		String msg = message + "\r\n" + old;
            		
            		console.setText(msg);
            		//Log.d(console.getId() + " ", msg);
            	}
            }
        });		
	}
	
	static String baseMessage = null;
	
	public static void showScanning(Activity act, final TextView console, final long count){
		act.runOnUiThread(new Runnable() {
			@Override
            public void run() {
            	synchronized (console) {
            		if (baseMessage == null)
            			baseMessage = console.getText().toString();  
            		String scanMessage = "  [" +  count + "] files scaned..\r\n" + baseMessage;
            		
            		console.setText( scanMessage );
            		//Log.d(console.getId() + " ", scanMessage + ".");
            	}
            }
        });		
	}
	
	
	public void clear(){
		this.console.setText("");
	}
}
