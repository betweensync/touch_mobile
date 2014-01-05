package com.cloudant.andysync.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StringUtil {
	static long sequence = 0;
	
	public static Date convertStringToDate(String strDate, String pattern)
			{
		DateFormat df = new SimpleDateFormat(pattern, Locale.US);

		Date date = null;
		strDate = strDate.replace("T", " ");
		try {
			date = df.parse(strDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return date;
	}
	
	public static String convertLongToDateString(long mills, String format){
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		
		String date = sdf.format(mills);
		
		return date;
	}
	
	public static long convertDateStringToLong(String date, String format){
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		
		long time = -1;
		try {
			time = sdf.parse(date).getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return time;
	}
	
	public synchronized static String generateSyncEventId(){
		if (sequence == Long.MAX_VALUE)
			sequence = 0;
		
		return convertLongToDateString(System.currentTimeMillis(), "yyyy-MM-dd hh:mm:ss")
			+ "-" + String.format("%20d", sequence++);
	}
	
	public static void main(String [] args){
		//System.out.println(convertLongToDateString(System.currentTimeMillis(), "yyyyMMdd"));
		//System.out.println(convertStringToDate("2012-05-23T05:16:57+00:00", "yyyy-MM-dd hh:mm:ss"));
	}
}
