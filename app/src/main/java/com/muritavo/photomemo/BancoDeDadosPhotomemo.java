package com.muritavo.photomemo;

import android.content.ContentValues;
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

    public void fechar (){
        if (database != null) database.close();
    }

    public void addDescricao (Long id, String descricao) {
        abrir();
        ContentValues colunas = new ContentValues();
        colunas.put("_id", id);
        colunas.put("descricao", descricao);
        database.insert("Photomemo", null, colunas);
        database.close();
    }
}
