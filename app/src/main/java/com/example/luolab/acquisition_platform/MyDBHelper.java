package com.example.luolab.acquisition_platform;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MyDBHelper extends SQLiteOpenHelper {

    private final static String DATABASE_NAME = "usrInfo.db";
    private final static int DATABASE_VERSION = 1;
    private final static String TABLE_NAME = "my_db";
    private final static String FEILD_ID = "_id";
    private final static String FEILD_NANE = "name_text";
    private final static String FEILD_AGE =  "age_text";
    private final static String FEILD_BIR =  "bir_text";
    private final static String FEILD_HEIGHT = "height_text";
    private final static String FEILD_WEIGHT = "weight_text";

    private String sql =
            "CREATE TABLE IF NOT EXISTS "+TABLE_NAME+"("+
                    FEILD_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
                    FEILD_NANE+" TEXT,"+
                    FEILD_AGE+" TEXT,"+
                    FEILD_BIR+" TEXT,"+
                    FEILD_HEIGHT+" TEXT,"+
                    FEILD_WEIGHT+" TEXT"+
                    ")";

    private static SQLiteDatabase database;

    public MyDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        database = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //db.execSQL("DROP TABLE IF EXISTS newMemorandum"); //刪除舊有的資料表
        onCreate(db);
    }

    public Cursor query(){
        Cursor cursor = database.rawQuery("SELECT * FROM my_db", null);
        cursor.moveToFirst();
        //Log.d("asd",cursor.getCount() + "");
        if(cursor.getCount() == 0)
            insert("預設","23","19910123","170","70");
        return database.rawQuery("SELECT * FROM my_db", null);
    }
    public void deleteAll(){
        database.delete(TABLE_NAME,null,null);
    }
    public void insert(String Name,String Age,String Bir,String Height,String Weight){
        ContentValues contentValues = new ContentValues();
        contentValues.put(FEILD_NANE, Name);
        contentValues.put(FEILD_AGE, Age);
        contentValues.put(FEILD_BIR, Bir);
        contentValues.put(FEILD_HEIGHT, Height);
        contentValues.put(FEILD_WEIGHT, Weight);
        database.insert(TABLE_NAME, null, contentValues);
    }
}
