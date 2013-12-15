package com.example.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;

import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class MainActivity extends Activity {

	static {
		//	TouchDB Initialization
		TDURLStreamHandlerFactory.registerSelfIgnoreError();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//	TouchDB Connection
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
