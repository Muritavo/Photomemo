package com.muritavo.photomemo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.MediaStore;

/**
 * Created by 15111018 on 28/08/2015.
 */
public class AuxiliarDoBancoDeDados extends SQLiteOpenHelper{

    public AuxiliarDoBancoDeDados(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createCommand = "CREATE TABLE descricoes (_id integer primary key autoincrement, identificador integer, descricao TEXT);";
        db.execSQL(createCommand);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
