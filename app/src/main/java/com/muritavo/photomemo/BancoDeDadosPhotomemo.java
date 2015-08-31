package com.muritavo.photomemo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by 15111018 on 31/08/2015.
 */
public class BancoDeDadosPhotomemo {
    private static final String DATABASE_NAME = "Photomemo";
    private SQLiteDatabase database;
    private AuxiliarDoBancoDeDados auxiliar;

    public BancoDeDadosPhotomemo (Context context){
        auxiliar = new AuxiliarDoBancoDeDados(context, DATABASE_NAME, null, 1);
    }

    public void abrir (){
        database = auxiliar.getWritableDatabase();
    }

    public void close (){
        if (database != null) database.close();
    }

    public void addDescricao (Long id, String decsricao) {

    }
}
