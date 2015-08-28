package com.muritavo.photomemo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.MathContext;

/**
 * Created by 15111018 on 28/08/2015.
 */
public class ImageLoaderAsync {
    private Context context;
    private LruCache<String, Bitmap> imagensEmCache;

    public ImageLoaderAsync (Context context, LruCache cache){
        this.context = context;
        imagensEmCache = cache;
    }

    public void carregarBitmap (String caminhoDaImagem, ImageView imageView, Boolean fullSize){
        if (cancelPotentialWork(caminhoDaImagem, imageView)) {
            final LoadBitmapIcon task = new LoadBitmapIcon(imageView);
            final ImagemTemporaria imagemTemporaria = new ImagemTemporaria(context.getResources(), BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_crop_original_black_48dp), task);
            imageView.setImageDrawable(imagemTemporaria);
            task.execute(caminhoDaImagem, fullSize);
        }
    }

    /**
     * Inseri uma imagem temporária enquanto a imagem é carregada
     */
    static class ImagemTemporaria extends BitmapDrawable {
        private final WeakReference<LoadBitmapIcon> bitmapWorkerTaskWeakReference;

        public ImagemTemporaria(Resources res, Bitmap bitmap, LoadBitmapIcon bitmapWorkerTask){
            super (res, bitmap);
            bitmapWorkerTaskWeakReference = new WeakReference<LoadBitmapIcon>(bitmapWorkerTask);
        }

        public LoadBitmapIcon getBitmapWorkerTask(){
            return  bitmapWorkerTaskWeakReference.get();
        }

    }

    private boolean cancelPotentialWork(String caminhoDaImagem, ImageView imageView) {
        final LoadBitmapIcon bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null){
            final String bitmapPath = bitmapWorkerTask.caminho;

            if (bitmapPath.equals("") || bitmapPath.equals(caminhoDaImagem)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }

        return true;
    }

    private LoadBitmapIcon getBitmapWorkerTask (ImageView imageView) {
        if (imageView != null){
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof ImagemTemporaria){
                final ImagemTemporaria imagemTemporaria = (ImagemTemporaria) drawable;
                return imagemTemporaria.getBitmapWorkerTask();
            }
        }
        return null;
    }

    private class LoadBitmapIcon extends AsyncTask<Object, Object, Bitmap> {

        private final WeakReference<ImageView> imageViewWeakReference;
        private String caminho = "";
        public LoadBitmapIcon (ImageView imageView){
            imageViewWeakReference = new WeakReference<ImageView>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Object... params) {
            int reqWidth;
            int reqHeight;
            caminho = (String) params[0];
            boolean fullSize = (Boolean) params[1];

            BitmapFactory.Options dimensoes = new BitmapFactory.Options();

            dimensoes.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(caminho, dimensoes);
            if (fullSize) {
                reqWidth = TelaInicial.dimensoes.x;
                reqHeight = TelaInicial.dimensoes.y;
            }
            else {
                reqWidth = TelaInicial.dimensoes.x / 4;
                reqHeight = TelaInicial.dimensoes.y / 4;
            }
            dimensoes.inSampleSize = calcularTamanhoIdeal(dimensoes, reqWidth, reqHeight);
            dimensoes.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(caminho, dimensoes);

            float rotacao = precisaDeRotacao(Uri.fromFile(new File(caminho)));

            if (bitmap != null && rotacao != 0){
                Matrix matriz = new Matrix();
                matriz.preRotate(rotacao);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matriz, true);
            }
            if (bitmap == null){
                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_broken_image_black_48dp);
            }
            if (!fullSize) imagensEmCache.put(caminho, bitmap);
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (imageViewWeakReference != null && bitmap != null) {
                final ImageView imageView = imageViewWeakReference.get();
                final LoadBitmapIcon loadBitmapIcon = getBitmapWorkerTask(imageView);
                if (this == loadBitmapIcon && imageView != null){
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

        private int calcularTamanhoIdeal(BitmapFactory.Options dimensoes, int reqWidth, int reqHeight){
            final int height = dimensoes.outHeight;
            final int width = dimensoes.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth){
                if (width > height) inSampleSize = Math.round((float) height / (float) reqHeight);

                else inSampleSize = Math.round((float) width / (float) reqWidth);
            }
            return inSampleSize;
        }

        private float precisaDeRotacao(Uri uri) {
            try {
                if (uri.getScheme().equals("file")) {
                    ExifInterface exif = new ExifInterface(uri.getPath());
                    int rotacao = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch (rotacao){
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            return 90;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            return 180;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            return 270;
                    }
                }
            } catch (IOException e) {
                return 0;
            }
            return 0;
        }
    }
}
