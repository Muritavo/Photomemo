package com.muritavo.photomemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;

public class AdicionarDescricao extends Activity {
    private String caminhoDaImagem;
    private static final int REQ_WIDTH = TelaInicial.dimensoes.x;
    private static final int REQ_HEIGHT = TelaInicial.dimensoes.y;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adicionar_descricao);

        Intent intent = getIntent();
        caminhoDaImagem = intent.getStringExtra("path");
        ImageView imageView = (ImageView) findViewById(R.id.preVisualizacao);
        setPic(imageView, caminhoDaImagem);

        Button botao = (Button) findViewById(R.id.buttonSalvar);
        botao.setOnClickListener(salvar);

        Button descartar = (Button) findViewById(R.id.buttonDescartar);
        descartar.setOnClickListener(descarte);
    }
    /**
     * Reduz o tamanho da imagem e a coloca no layout
     *
     * @param imageView onde a image sera inserida.
     * @param caminhoDaImagem Localização da imagem no sistema.
     */
    private void setPic(ImageView imageView, String caminhoDaImagem) {
        // Obtem as medidas da imagem
        BitmapFactory.Options dimensoes = new BitmapFactory.Options(); //Vai manter as dimensoes da imagem
        //Obtem as dimensoes da imagem
        dimensoes.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(caminhoDaImagem, dimensoes);
        //Calcula o tamanho ideal da imagem que sera exibida
        dimensoes.inSampleSize = calcularTamanhoDaImagem(dimensoes, REQ_WIDTH, REQ_HEIGHT);
        //Decodifica a imagem com as dimensoes ideais configuradas
        dimensoes.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(caminhoDaImagem, dimensoes);
        //Verifica se precisa de rotação
        float rotacao = precisaDeRotacao(Uri.fromFile(new File(caminhoDaImagem)));

        if (rotacao != 0){
            //Rotaciona a imagem
            Matrix matrix = new Matrix();
            matrix.preRotate(rotacao);
            imageView.setImageBitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true));
        }
        else {
            //Usa a imagem original
            imageView.setImageBitmap(bitmap);
        }
    }
    /**
     * Calcula o tamanho ideal de inSampleSize para redimensionar a imagem de acordo com cada tipo de tela
     *
     * @param dimensoes Contem o tamanho padrão da imagem
     * @param reqWidth A dimensao do eixo x da tela do dispositivo
     * @param reqHeight A dimensão do eixo y da tela do dispositivo
     * @return Um valor >= 1 representando a quantidade de vezes que a imagem tera de ser reduzida para conservar memoria
     */
    private int calcularTamanhoDaImagem(BitmapFactory.Options dimensoes, int reqWidth, int reqHeight) {
        final int height = dimensoes.outHeight;
        final int width = dimensoes.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth){
            if (width > height){
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }
    /**
     * Verifica nas tags da imagem se ela precisa ser rotacionada
     * @param uri O caminho para a imagem dentro do dispositivo
     * @return A quantidade de graus que a imagem precisa ser rotacionada
     */
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
    /**
     * Atualiza o banco de dados com a nova descrição da imagem
     */
    private OnClickListener salvar = new OnClickListener() {
        @Override
        public void onClick(View v) {
            EditText editTag = (EditText) findViewById(R.id.EditTagText);
            String descricao = editTag.getText().toString();
            if (descricao.length() == 0) descricao = null;
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.ImageColumns.DESCRIPTION, descricao);

            cr.update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv, MediaStore.Images.ImageColumns.DATA + " = ?", new String[]{ caminhoDaImagem });

            onBackPressed();
        }
    };
    /**
     * Volta para a Tela inicial mantendo o banco de dados inalterado
     */
    private OnClickListener descarte = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(AdicionarDescricao.this);
            builder.setTitle(R.string.confirmacao_title);
            builder.setMessage(R.string.confirmacao_message);
            builder.setPositiveButton(R.string.confirmacao_positiva, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onBackPressed();
                }
            });
            builder.setNegativeButton(R.string.voltar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    };

}
