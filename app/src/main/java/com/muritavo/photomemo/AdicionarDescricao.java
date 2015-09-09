package com.muritavo.photomemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
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
    private String tituloDaImagem;
    private int id;
    private BancoDeDadosPhotomemo bancoDeDadosPhotomemo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ContentResolver cr = getContentResolver();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adicionar_descricao);
        bancoDeDadosPhotomemo = ((Photomemo) this.getApplication()).getBancoDeDadosPhotomemo(getApplicationContext());

        Intent intent = getIntent();
        caminhoDaImagem = intent.getStringExtra("path");
        tituloDaImagem = intent.getStringExtra("tituloImagem");

        Cursor cur = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Images.ImageColumns.DATA, MediaStore.Images.ImageColumns._ID}, MediaStore.Images.ImageColumns.DATA + " LIKE ?", new String[]{"%" + tituloDaImagem + "%"}, null);
        cur.moveToFirst();
        id = cur.getInt(cur.getColumnIndex(MediaStore.Images.ImageColumns._ID));

        ImageView imageView = (ImageView) findViewById(R.id.preVisualizacao);
        carregadorDeImagens = ((Photomemo) this.getApplication()).getCarregadorDeImagem(getApplicationContext());
        carregadorDeImagens.carregarBitmap(caminhoDaImagem, imageView, true);

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
            bancoDeDadosPhotomemo.addDescricao(id, descricao);
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
