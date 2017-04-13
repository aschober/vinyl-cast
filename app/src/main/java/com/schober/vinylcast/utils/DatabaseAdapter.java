/* Gracenote Android Music SDK Sample Application
 *
 * Copyright (C) 2010 Gracenote, Inc. All Rights Reserved.
 */
package com.schober.vinylcast.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import com.gracenote.gnsdk.GnAlbum;
import com.gracenote.gnsdk.GnAlbumIterator;
import com.gracenote.gnsdk.GnAssetFetch;
import com.gracenote.gnsdk.GnException;
import com.gracenote.gnsdk.GnImageSize;
import com.gracenote.gnsdk.GnResponseAlbums;
import com.gracenote.gnsdk.GnUser;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


/**
 * <p>
 * This class will work as a utility class to deal with database. It will be
 * responsible for database connection, insert a row into database, retrieve the
 * cursor for select and update operations.
 * 
 * 
 */
public final class DatabaseAdapter {
	private static final String TAG = "DatabaseAdapter";

    private final Context context;
    private GnUser gnUser;

	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;
	public static final int MAX_COUNT = 100;

	public static final String MUSIC_HISTORY_TABLE = "search_history";
	public static final String MUSIC_HISTORY_RESPONSE_TABLE = "search_response";
	
	public static final String MUSIC_HISTORY_ID = "_id";
	public static final String MUSIC_HISTORY_SEARCH_ID = "search_id";
	public static final String MUSIC_HISTORY_ALBUM_TITLE = "album_title";
	public static final String MUSIC_HISTORY_ARTIST = "artist";
	public static final String MUSIC_HISTORY_TRACK_TITLE = "track_title";
	public static final String MUSIC_HISTORY_COVERART_URL = "cover_art_url";
	public static final String MUSIC_HISTORY_COVERART_MIMETYPE = "cover_art_mimetype";
	public static final String MUSIC_HISTORY_COVERART_SIZE = "cover_art_size";
	public static final String MUSIC_HISTORY_ALBUMID = "album_id";
	public static final String MUSIC_HISTORY_ALBUM_TRACK_COUNT = "album_track_count";
	public static final String MUSIC_HISTORY_TRACK_NUMBER = "track_number";
	public static final String MUSIC_HISTORY_GENRE_ID = "genre_id";
	public static final String MUSIC_HISTORY_GENRE = "genre";
	public static final String MUSIC_HISTORY_DATE = "date_time";
	public static final String MUSIC_HISTORY_COVERART_IMAGE = "coverart_image_data";
	public static final String MUSIC_HISTORY_FINGERPRINT = "fingerprint";
	public static final String COMMA = ",";

	public DatabaseAdapter(Context context, GnUser gnUser) {
		this.context = context;
        this.gnUser = gnUser;
		DBHelper = new DatabaseHelper(this.context);
	}

	public DatabaseAdapter open() throws SQLException {
		db = DBHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		DBHelper.close();
	}

	public int deleterow(String _id) {
		int result = 0;
		try {
			result = db.delete(MUSIC_HISTORY_TABLE, MUSIC_HISTORY_ID +" = ?",
					new String[] { _id });
			result = db.delete(MUSIC_HISTORY_RESPONSE_TABLE, MUSIC_HISTORY_ID +" = ?", new String[] { _id });
		} catch (SQLiteDiskIOException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteFullException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteConstraintException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteDatabaseCorruptException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (Exception exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		}
		Log.i(TAG, "Transaction completed.");
		return result;
	}

	public int deleteAll() {
		int result = 0;
		try {
			result = db.delete(MUSIC_HISTORY_TABLE, null, null);
			result = db.delete(MUSIC_HISTORY_RESPONSE_TABLE, null, null);
		} catch (SQLiteDiskIOException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteFullException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteConstraintException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteDatabaseCorruptException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteException exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (Exception exception) {
			Log.e(TAG, "Failed to delete record - " + exception.getMessage());
			Toast.makeText(context,
					"Failed to delete record - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		}
		Log.i(TAG, "Transaction completed.");
		return result;
	}

	public Cursor getcursor() {
		Cursor cursor = null;
		try {
			cursor = db.rawQuery("select a."+MUSIC_HISTORY_ID + COMMA +
							"a."+MUSIC_HISTORY_ALBUM_TITLE + COMMA +
							"a."+MUSIC_HISTORY_TRACK_TITLE + COMMA +
							"a."+MUSIC_HISTORY_ARTIST + COMMA +
							"a."+MUSIC_HISTORY_COVERART_IMAGE + COMMA +
							"b."+MUSIC_HISTORY_DATE +" from " +
							MUSIC_HISTORY_RESPONSE_TABLE + " a" + COMMA +
							MUSIC_HISTORY_TABLE+ " b where a."+MUSIC_HISTORY_SEARCH_ID+" = b."+ MUSIC_HISTORY_ID
							+ " ORDER BY " + MUSIC_HISTORY_SEARCH_ID + " DESC" + ";",
							null);

			cursor.deactivate();
		} catch (SQLiteDiskIOException exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteFullException exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteConstraintException exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteDatabaseCorruptException exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (SQLiteException exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		} catch (Exception exception) {
			Log.e(TAG, "Failed to get cursor " + exception.getMessage());
			Toast.makeText(context,
					"Failed to retrive cursur - " + exception.getMessage(),
					Toast.LENGTH_SHORT).show();
		}
		return cursor;
	}

	public void handleMaxDBRow() {
		Cursor countCursor = db.rawQuery(
				"select count(*) from "+MUSIC_HISTORY_TABLE+";", null);
		int result_responce = 0;
		int result_history = 0;
		countCursor.moveToFirst();
		int count = countCursor.getInt(0);
		countCursor.close();

		Log.i(TAG, "count: " + count);

		if (count > MAX_COUNT) {

			Cursor minIdCursor = db.rawQuery(
					"select min(_id) from "+MUSIC_HISTORY_TABLE+";", null);
			minIdCursor.moveToFirst();
			String min_Id = minIdCursor.getString(0);
			minIdCursor.close();

			result_responce = db.delete(MUSIC_HISTORY_RESPONSE_TABLE, MUSIC_HISTORY_ID +" = ?",
					new String[] { min_Id });
			result_history = db.delete(MUSIC_HISTORY_TABLE, MUSIC_HISTORY_ID +" = ?",
					new String[] { min_Id });

			Log.i(TAG, "GN_DATABASE_METADATA_DELETE " + String.valueOf(result_responce));
			Log.i(TAG, "GN_DATABASE_SEARCH_HISTORY_DELETE " + String.valueOf(result_history));
		}
	}

	/**
	 * 
	 * @return Cursor - A pointer to a database which has location values for
	 *         records.
	 * 
	 */

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	
	
	public long insertChanges(GnResponseAlbums gnAlbums) throws GnException {
		ContentValues initialValues = null;
		// to insert into search_history
		initialValues = new ContentValues();
		TimeZone timeZone = TimeZone.getDefault();
		long gmtRawOffset = timeZone.getRawOffset();
		Date currentTime = new Date();
		Date convertedDate = new Date(currentTime.getTime() - gmtRawOffset);
		String time = dateFormat.format(convertedDate);
//		if (row.getFingerprintData() != null)
//			initialValues.put(MUSIC_HISTORY_FINGERPRINT, row.getFingerprintData());

		initialValues.put(MUSIC_HISTORY_DATE, time);
		try {

			long masterRowId = db.insert(MUSIC_HISTORY_TABLE, null, initialValues);

			Log.i(TAG, "New row: " + masterRowId);
			if (masterRowId == -1) {
				Toast.makeText(context, "Failed to insert into search history",
						Toast.LENGTH_SHORT);
				return 0;
			}
			GnAlbumIterator iter = gnAlbums.albums().getIterator();
			while(iter.hasNext()){
				GnAlbum album = iter.next();
				initialValues = new ContentValues();
				initialValues.put(MUSIC_HISTORY_SEARCH_ID, masterRowId);
				if (album.title().display() != null)
					initialValues.put(MUSIC_HISTORY_ALBUM_TITLE, album.title().display());

				String artist = null;
				if(album.trackMatched() != null)
					artist = album.trackMatched().artist().name().display();
				//use album artist if track artist not available
				if(artist== null || artist.isEmpty()){
					artist = album.artist().name().display();
				}				
				if (artist != null)
					initialValues.put(MUSIC_HISTORY_ARTIST, artist);
				
				if ( album.trackMatched() != null ) {			
					if (album.trackMatched().title().display()  != null)
						initialValues.put(MUSIC_HISTORY_TRACK_TITLE, album.trackMatched().title().display());
				}
				
				//load and store cover art
				String coverArtUrl = album.coverArt().asset(GnImageSize.kImageSizeSmall).url();
				byte[] imageData = getDataFromURL("http://"+coverArtUrl);
				if(imageData != null){
					Log.i(TAG, "cover art size = " + imageData.length + ", url = " + coverArtUrl);
					initialValues.put(MUSIC_HISTORY_COVERART_IMAGE, imageData);	
				}
																				
				if (album.gnId() != null)
					initialValues.put(MUSIC_HISTORY_ALBUMID, album.gnId());
				initialValues.put(MUSIC_HISTORY_ALBUM_TRACK_COUNT, album.trackCount());
				initialValues.put(MUSIC_HISTORY_TRACK_NUMBER, album.trackMatchNumber());
				
				
				long result = db.insert(MUSIC_HISTORY_RESPONSE_TABLE, null, initialValues);
				if (result == -1) {
					Log.e(TAG, "Failed to insert one of search result");
					Toast.makeText(context,
							"Failed to insert one of search result",
							Toast.LENGTH_SHORT).show();
				}
				Log.i(TAG, "Row inserted.");
			}
			handleMaxDBRow();

		} catch (SQLiteDiskIOException exception) {
			Log.e(TAG, "Failed to insert record - " + exception.getMessage());
			Toast.makeText(
					context,
					"Failed to insert search result - "
							+ exception.getMessage(), Toast.LENGTH_SHORT)
					.show();
		} catch (SQLiteFullException exception) {
			Log.e(TAG, "Failed to insert record - " + exception.getMessage());
			Toast.makeText(
					context,
					"Failed to insert search result - "
							+ exception.getMessage(), Toast.LENGTH_SHORT)
					.show();
		} catch (SQLiteConstraintException exception) {
			Log.e(TAG, "Failed to insert record - " + exception.getMessage());
			Toast.makeText(
					context,
					"Failed to insert search result - "
							+ exception.getMessage(), Toast.LENGTH_SHORT)
					.show();
		} catch (SQLiteDatabaseCorruptException exception) {
			Log.e(TAG, "Failed to insert record - " + exception.getMessage());
			Toast.makeText(
					context,
					"Failed to insert search result - "
							+ exception.getMessage(), Toast.LENGTH_SHORT)
					.show();
		} catch (SQLiteException exception) {
			Log.e(TAG, "Failed to insert record - " + exception.getMessage());
			Toast.makeText(
					context,
					"Failed to insert search result - "
							+ exception.getMessage(), Toast.LENGTH_SHORT)
					.show();
		}
		return 0;

	}

	/**
	 * <p>
	 * It will create a database and tables.
	 * 
	 * 
	 */
	public class DatabaseHelper extends SQLiteOpenHelper {

		final static String CREATE_TABLE_SEARCH_HISTORY = "CREATE TABLE " + MUSIC_HISTORY_TABLE + "("+
													MUSIC_HISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT "+ COMMA +
													MUSIC_HISTORY_FINGERPRINT+ " TEXT "+ COMMA +
													MUSIC_HISTORY_DATE + " TEXT "  +
													")";
		final static String CREATE_TABLE_RESPONSES = "CREATE TABLE " + MUSIC_HISTORY_RESPONSE_TABLE +"(" +
													MUSIC_HISTORY_ID + " INTEGER PRIMARY KEY " + COMMA +
													MUSIC_HISTORY_SEARCH_ID + " INTEGER " + COMMA +
													MUSIC_HISTORY_ALBUM_TITLE + " TEXT " +  COMMA +
													MUSIC_HISTORY_ARTIST + " TEXT "+   COMMA + 
													MUSIC_HISTORY_TRACK_TITLE + " TEXT "+  COMMA +
													MUSIC_HISTORY_COVERART_IMAGE + " BLOB " + COMMA +
													MUSIC_HISTORY_COVERART_MIMETYPE + " TEXT " + COMMA +
													MUSIC_HISTORY_COVERART_SIZE + " TEXT " + COMMA +
													MUSIC_HISTORY_ALBUMID + " TEXT " + COMMA +
													MUSIC_HISTORY_ALBUM_TRACK_COUNT + " TEXT " + COMMA +
													MUSIC_HISTORY_TRACK_NUMBER + " TEXT " + COMMA + 
													MUSIC_HISTORY_GENRE_ID +" TEXT "  +
													")";
	
		
		final static String CREATE_INDEX_DATE = "CREATE INDEX date_time_index on "+MUSIC_HISTORY_TABLE + "("+MUSIC_HISTORY_DATE+")";

		DatabaseHelper(Context context) {
			// database name - history_track
			// database version - 1
			super(context, "history_track", null, 1);
			Log.i(TAG, "Database helper constructor");
		}

		@Override
		public void onCreate(SQLiteDatabase db)
				throws SQLiteConstraintException, SQLiteDiskIOException,
                SQLiteException {

			db.execSQL(CREATE_TABLE_SEARCH_HISTORY);
			db.execSQL(CREATE_TABLE_RESPONSES);
			db.execSQL(CREATE_INDEX_DATE);
			Log.i(TAG, "Tables created...");

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
				throws SQLiteConstraintException, SQLiteDiskIOException,
                SQLiteException {
			Log.i(TAG, "table upgraded");
			db.execSQL("DROP TABLE IF EXISTS "+MUSIC_HISTORY_RESPONSE_TABLE);
			db.execSQL("DROP TABLE IF EXISTS "+MUSIC_HISTORY_TABLE);
			onCreate(db);
		}
	}

	/**
	 * This method will generate bytes for
	 * 
	 * @param obj
	 * @return
	 * @throws java.io.IOException
	 */
	public byte[] getBytes(Object obj) throws java.io.IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		oos.flush();
		oos.close();
		bos.close();
		byte[] data = bos.toByteArray();
		return data;
	}
	
	
    byte[] getDataFromURL(String urlString)
    {
		if ( null == gnUser )
			return null;

        byte[] imageData = null;
        
        try {
                imageData = new GnAssetFetch(gnUser, urlString).data();
        }
        catch (Exception e)
        {
            return null;
        }
        
        return imageData;
    }

	
}
