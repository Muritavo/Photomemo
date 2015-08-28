package com.muritavo.photomemo;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.LruCache;

import java.text.SimpleDateFormat;
import java.util.Formatter;

public class Photomemo extends Application {
    private LruCache<String, Bitmap> imagensEmCache;
    private SimpleDateFormat formatter;
    private ImageLoaderAsync carregadorDeImagem;

    /**
     * Reserva um espaço da memória para armazenar os icones de cada imagem
     * @return O objeto LruCache.
     */
    public LruCache<String, Bitmap> getImagensEmCache (){
        if (imagensEmCache == null){
            final int memoriaCache = (int) (Runtime.getRuntime().maxMemory() / 1024);
            final int cacheSize = memoriaCache / 8;

            imagensEmCache = new LruCache<String, Bitmap>(cacheSize);
        }
        return imagensEmCache;
    }

    /**
     * Retorna o formatador de data, caso não exista, o cria.
     * @return Retorna o objeto formatador de data
     */
    public SimpleDateFormat getFormatter (){
        if (formatter == null){
            formatter = new SimpleDateFormat("dd/MM/yyyy");
        }
        return formatter;
    }

    public ImageLoaderAsync getCarregadorDeImagem(Context context){
        if (carregadorDeImagem == null){
            carregadorDeImagem = new ImageLoaderAsync(context, getImagensEmCache());
        }
        return carregadorDeImagem;
    }
}
