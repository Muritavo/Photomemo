package com.muritavo.photomemo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by 15111018 on 28/08/2015.
 */
public class AuxiliarDoBancoDeDados extends SQLiteOpenHelper{
    private ContentResolver contentResolver;

    public AuxiliarDoBancoDeDados(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        contentResolver = context.getContentResolver();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createCommand = "CREATE TABLE Photomemo (_id integer primary key, descricao TEXT);";
        db.execSQL("ATTACH DATABASE ? AS PhotomemoTest", new String[]{contentResolver.toString()});
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
