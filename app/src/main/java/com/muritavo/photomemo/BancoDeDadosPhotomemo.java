package com.muritavo.photomemo;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore;

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

    public void addDescricao (int id, String descricao) {
        abrir();
        ContentValues colunas = new ContentValues();
        colunas.put("identificador", id);
        colunas.put("descricao", descricao);
        database.insert("descricoes", null, colunas);
        fechar();
    }

    public void deletar(int identificador) {
        abrir();
        database.delete("descricoes", "identificador LIKE ?", new String[]{String.valueOf(identificador)});
        fechar();
    }

    public String buscarDescricao(int identificador) {
        abrir();
        String[] colunas = new String[]{
                "_id",
                "identificador",
                "descricao"
        };
        Cursor cur = database.query("descricoes", colunas, "identificador LIKE ?", new String[]{String.valueOf(identificador)}, null, null, null);
        if (cur.getCount() != 0)
            cur.moveToFirst();
        else return null;
        fechar();
        return cur.getString(cur.getColumnIndex("descricao"));
    }

    public String[] buscarDescricao(String descricao){
        abrir();
        String[] colunas = new String[]{
                "_id",
                "identificador",
                "descricao"
        };
        Cursor cur = database.query("descricoes", colunas, "descricao LIKE ?", new String[]{"%"+descricao+"%"}, null, null, "identificador");
        String [] IDs = new String[cur.getCount()];
        cur.moveToFirst();
        for (int row = 0; row < IDs.length; row++){
            IDs[row] = cur.getString(cur.getColumnIndex("identificador"));
            cur.moveToNext();
        }
        return IDs;
    }
}
