package com.muritavo.photomemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

public class AdicionarDescricao extends Activity {
    private ImageLoaderAsync carregadorDeImagens;
    private String caminhoDaImagem;
    private String descricaoDaImagem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adicionar_descricao);

        Intent intent = getIntent();
        caminhoDaImagem = intent.getStringExtra("path");
        descricaoDaImagem = intent.getStringExtra("descricao");
        ImageView imageView = (ImageView) findViewById(R.id.preVisualizacao);
        carregadorDeImagens = ((Photomemo) this.getApplication()).getCarregadorDeImagem(getApplicationContext());
        carregadorDeImagens.carregarBitmap(caminhoDaImagem, imageView, true);

        EditText descricao = (EditText) findViewById(R.id.campoDescricao);

        Button botao = (Button) findViewById(R.id.buttonSalvar);
        botao.setOnClickListener(salvar);

        Button descartar = (Button) findViewById(R.id.buttonDescartar);
        descartar.setOnClickListener(descarte);
    }
    /**
     * Atualiza o banco de dados com a nova descrição da imagem
     */
    private OnClickListener salvar = new OnClickListener() {
        @Override
        public void onClick(View v) {
            EditText editTag = (EditText) findViewById(R.id.campoDescricao);
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
