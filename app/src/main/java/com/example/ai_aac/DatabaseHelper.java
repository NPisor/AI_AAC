package com.example.ai_aac;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "messages.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_PICTOGRAM = "pictogram";
    private static final String COLUMN_RESPONSE = "response";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    public DatabaseHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_MESSAGES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PICTOGRAM + " TEXT, "
                + COLUMN_RESPONSE + " TEXT, "
                + COLUMN_TIMESTAMP + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    // Insert a new message
    public void insertMessage(String pictogram, String response, String timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PICTOGRAM, pictogram);
        values.put(COLUMN_RESPONSE, response);
        values.put(COLUMN_TIMESTAMP, timestamp);
        db.insert(TABLE_MESSAGES, null, values);
        db.close();
    }

    // Retrieve all messages
    public Cursor getAllMessages() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_MESSAGES, null, null, null, null, null, COLUMN_TIMESTAMP + " ASC");
    }

    public String getLatestTimestamp() {
        SQLiteDatabase db = this.getReadableDatabase();
        String latestTimestamp = "0"; // Default value if no messages exist
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "messages",
                    new String[]{"timestamp"}, // Column to retrieve
                    null, // No selection criteria
                    null, // No selection arguments
                    null, // No group by
                    null, // No having
                    "timestamp DESC", // Order by timestamp descending
                    "1" // Limit to 1 result
            );

            if (cursor != null && cursor.moveToFirst()) {
                @SuppressLint("Range") String timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));
                latestTimestamp = timestamp;
            }
        } catch (Exception e) {
            Log.e("DatabaseHelper", "Error retrieving latest timestamp", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return latestTimestamp;
    }


}
