package com.muritavo.photomemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;

import static android.provider.MediaStore.Images.*;

public class TelaInicial extends Activity {
    private Boolean gridViewEstaAtivo; // Ultimo modo de view escolhido pelo usuario.

    //Variaveis que indicam o local onde será salva a imagem que o usuario vai capturar
    private Uri caminhoDaImagem = null;
    private String tituloDaImagem;
    private long dataDeCaptura = 0;

    private LruCache<String, Bitmap> imagensEmCache; //Cache onde serão armazenadas os icones de cada imagem
    private SharedPreferences sharedPreferences;
    private imageCursorAdapter adapter; //Adapter utilizado para popular a liasta ou grade
    private LinearLayout layoutLista; //A parte do layout que mantem a lista de imagens
    private SimpleDateFormat formatter; //Utilizado para converter a data de captura em um formato de data
    private LinearLayout layoutCampoDeBusca; //A parte do layout que mantem o campo de busca
    private ContentResolver cr;
    private EditText campoDeBuscaEditText;
    private Button ocultarCampoDeBusca;
    public static Point dimensoes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tela_inicial);
        formatter = ((Photomemo) this.getApplication()).getFormatter();
        cr = this.getContentResolver();
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        }
        catch (Exception ex){
        } //Desabilita o botão menu fisico

        // Obtem as dimensões (em px) de cada eixo da tela e armazena na variavel dimensoes
        Display display = getWindowManager().getDefaultDisplay();
        dimensoes = new Point();
        display.getSize(dimensoes);

        // Verifica se há uma activity anterior que foi encerrada
        if (savedInstanceState != null) {
            caminhoDaImagem = Uri.parse((String) savedInstanceState.get("caminhoDaImagem"));
            tituloDaImagem = (String) savedInstanceState.get("tituloDaImagem");
            dataDeCaptura = (Long) savedInstanceState.get("dataDeCaptura");
        }

        //Cria as referencias aos elementos do layout e obtem o ultimo modo de visualização deixado pelo usuario
        sharedPreferences = getSharedPreferences("photomemoTags", MODE_PRIVATE);
        gridViewEstaAtivo = sharedPreferences.getBoolean("gridViewEstaAtivo", true); //True:Cria uma Grade com os icones | False: Cria uma lista com os icones e descrição
        layoutLista = (LinearLayout) findViewById(R.id.rootLinearLayout);
        layoutCampoDeBusca = (LinearLayout) findViewById(R.id.campoDeBusca);
        campoDeBuscaEditText = (EditText) findViewById(R.id.campoDeBuscaEditText);
        ocultarCampoDeBusca = (Button) findViewById(R.id.ocultarCampo);

        // Obtem o objeto que vai armazenar as imagens
        imagensEmCache = ((Photomemo) this.getApplication()).getImagensEmCache();

        criaAdapter(); //Cria o adaptador que vai ser utilizado para popular a lista.
        renovarLayout();
    }
    @Override
    protected void onResume() {
        criaAdapter();
        renovarLayout();
        super.onResume();
    } //Recria o Adaptador e renova o layout
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_tela_inicial, menu);
        MenuItem botaoLista = menu.findItem(R.id.list_view);
        MenuItem botaoGrade = menu.findItem(R.id.grid_view);
        MenuItem botaoBusca = menu.findItem(R.id.search);
        if (layoutCampoDeBusca.getVisibility() == View.VISIBLE){
            botaoBusca.setVisible(false);
        } //Esconde o botao de busca se o campo de busca estiver visivel
        if (gridViewEstaAtivo){
            botaoLista.setVisible(true);
            botaoGrade.setVisible(false);
        }// Esconde o botao de lista e mostra o de grade
        else {
            botaoGrade.setVisible(true);
            botaoLista.setVisible(false);
        }// Esconde o botao de grade e mostra o botao de lista
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        switch (id){
            case R.id.abrir_camera:
                requisitaFoto();
                break;
            case R.id.list_view:
                gridViewEstaAtivo = false;
                invalidateOptionsMenu();
                renovarLayout();
                break;
            case R.id.grid_view:
                gridViewEstaAtivo = true;
                invalidateOptionsMenu();
                renovarLayout();
                break;
            case R.id.search:
                invalidateOptionsMenu();
                layoutCampoDeBusca.setVisibility(View.VISIBLE);
                campoDeBuscaEditText.addTextChangedListener(atualizaBusca);
                ocultarCampoDeBusca.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        layoutCampoDeBusca.setVisibility(View.GONE);
                        adapter.changeCursor(cr.query(Media.EXTERNAL_CONTENT_URI, new String[]{
                                ImageColumns._ID,
                                ImageColumns.TITLE,
                                ImageColumns.DATA,
                                ImageColumns.DATE_TAKEN,
                                ImageColumns.DESCRIPTION
                        }, ImageColumns.TITLE + " LIKE ?", new String[]{
                                "Photomemo%"
                        }, ImageColumns.TITLE));
                        invalidateOptionsMenu();
                        renovarLayout();
                    }
                });
                break;
            case R.id.compartilharImagens:
                Intent compartilharImagem = new Intent(TelaInicial.this, CompartilharImagem.class);
                startActivity(compartilharImagem);
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == -1){
            Intent adicionarFotoAoBancoDeDados = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, caminhoDaImagem);
            this.sendBroadcast(adicionarFotoAoBancoDeDados);

            Intent adicionarDescricao = new Intent(this, AdicionarDescricao.class);
            adicionarDescricao.putExtra("path", caminhoDaImagem.toString().replace("file:", ""));
            startActivity(adicionarDescricao);
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (caminhoDaImagem == null){
            caminhoDaImagem = Uri.parse("");
        }
        outState.putString("caminhoDaImagem", caminhoDaImagem.toString());
        outState.putString("tituloDaImagem", tituloDaImagem);
        outState.putLong("dataDeCaptura", dataDeCaptura);
        super.onSaveInstanceState(outState);
    }
    @Override
    protected void onDestroy() {
        SharedPreferences.Editor editor;
        editor = sharedPreferences.edit();
        editor.putBoolean("gridViewEstaAtivo", gridViewEstaAtivo);
        editor.apply();
        super.onDestroy();
    } //Salva o modo de visualização ao sair da aplicação
    /**
     * Mantem referencias para cada item da lista ou grade
     */
    private class ViewHolder{
        String tituloImagem;
        String descricaoDaImagem;
        String dataDeCaptura;
        String caminhoDaImagem;
        ImageView imagem;
        TextView descricao;
        TextView data;
        TextView titulo;
    }
    /**
     * Adaptador utilizado para popular a lista ou grade
     */
    private class imageCursorAdapter extends CursorAdapter {
        ViewHolder holder;

        public imageCursorAdapter (Context context, Cursor c){
            super(context, c);
        }

        @Override
        public View newView (Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            holder = new ViewHolder();
            if (gridViewEstaAtivo) {
                View v = inflater.inflate(R.layout.grid_view_tile, parent, false);
                RelativeLayout square = (RelativeLayout) v.findViewById(R.id.tile);
                square.getLayoutParams().height = dimensoes.x / 5;
                holder.imagem = (ImageView) v.findViewById(R.id.icone);
                v.setMinimumHeight(v.getMeasuredWidth());
                v.setTag(holder);
                return v;
            }
            else {
                View v = inflater.inflate(R.layout.list_view_button, parent, false);
                holder.imagem = (ImageView) v.findViewById(R.id.icone);
                holder.titulo = (TextView) v.findViewById(R.id.titulo);
                holder.data = (TextView) v.findViewById(R.id.data);
                holder.descricao = (TextView) v.findViewById(R.id.descricao);
                v.setTag(holder);
                return v;
            }
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            holder = (ViewHolder) view.getTag();
            holder.imagem.setImageBitmap(null);
            holder.tituloImagem = cursor.getString(cursor.getColumnIndex(ImageColumns.TITLE));
            holder.dataDeCaptura = formatter.format(cursor.getLong(cursor.getColumnIndex(ImageColumns.DATE_TAKEN)));
            if ((holder.descricaoDaImagem = cursor.getString(cursor.getColumnIndex(ImageColumns.DESCRIPTION))) == null) {
                holder.descricaoDaImagem = getResources().getString(R.string.descricao_inexistente);
            }
            holder.caminhoDaImagem = cursor.getString(cursor.getColumnIndex(ImageColumns.DATA));
            if (imagensEmCache.get(holder.caminhoDaImagem) == null){
                loadBitmap(holder.caminhoDaImagem, holder.imagem);
            }
            else{
                holder.imagem.setImageBitmap(imagensEmCache.get(holder.caminhoDaImagem));
            }
            if (!gridViewEstaAtivo){
                holder.titulo.setText(holder.tituloImagem);
                holder.data.setText(holder.dataDeCaptura);
                holder.descricao.setText(holder.descricaoDaImagem);
            }
        }
    }
    /**
     *  Cria um adaptador que vai ser usado para popular a lista ou grade
     */
    private void criaAdapter() {
        String [] colunas = new String[]{
                ImageColumns._ID,
                ImageColumns.TITLE,
                ImageColumns.DATA,
                ImageColumns.DATE_TAKEN,
                ImageColumns.DESCRIPTION
        }; // Colunas que serão requeridas do banco de dados do celular
        String comandoWhere = ImageColumns.DATA + " LIKE ?"; //Comando WHERE do 3º argumento da busca do Content Resolver || ? representa onde serao colocados os argumentos do comando
        String[] argumentos = new String[]{
                "%Photomemo%",
        }; //Os argumentos do comando WHERE que serão substituidos no caracter '?'
        Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, colunas, comandoWhere, argumentos, ImageColumns.DATE_TAKEN); //Cursor que ira percorrer cada fila da busca do Content Resolver
        if (adapter == null){
            adapter = new imageCursorAdapter(TelaInicial.this, cursor);
        } //Caso não exista um Adaptador ele cria um novo
        else adapter.changeCursor(cursor);
    }
    /**
     * Cria um adaptador que vai ser usado para popular a lista ou grade customizada usando um argumento para buscar na descricao
     * @param argumento o argumento que vai ser usado para buscar na coluna descricao
     */
    private void criaAdapter(String argumento){
        String [] colunas = new String[]{
                ImageColumns._ID,
                ImageColumns.TITLE,
                ImageColumns.DATA,
                ImageColumns.DATE_TAKEN,
                ImageColumns.DESCRIPTION
        }; // Colunas que serão requeridas do banco de dados do celular
        String comandoWhere = ImageColumns.DATA + " LIKE ? AND " + ImageColumns.DESCRIPTION + " LIKE ?"; //Comando WHERE do 3º argumento da busca do Content Resolver || ? representa onde serao colocados os argumentos do comando
        String[] argumentos = new String[]{
                "%Photomemo%", "%" + argumento + "%"
        }; //Os argumentos do comando WHERE que serão substituidos no caracter '?'
        Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, colunas, comandoWhere, argumentos, ImageColumns.DATE_TAKEN); //Cursor que ira percorrer cada fila da busca do Content Resolver
        if (adapter == null){
            adapter = new imageCursorAdapter(TelaInicial.this, cursor);
        } //Caso não exista um Adaptador ele cria um novo
        else adapter.changeCursor(cursor);
    }
    /**
     * Renova o layout do usuario cada vez que o conteudo for modificado
     */
    private void renovarLayout() {
        layoutLista.removeAllViews();
        if (gridViewEstaAtivo) {
            GridView gridItens = new GridView(this);
            gridItens.setColumnWidth(dimensoes.x / 5);
            gridItens.setNumColumns(GridView.AUTO_FIT);
            gridItens.setAdapter(adapter);
            gridItens.setOnItemClickListener(openImage);
            layoutLista.addView(gridItens, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        else {
            ListView lista = new ListView(this);
            lista.setAdapter(adapter);
            lista.setOnItemClickListener(openImage);
            layoutLista.addView(lista, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }
    /**
     * Inicializa uma atividade da câmera para que o usuario possa capturar uma nova foto
     */
    private void requisitaFoto(){
        Uri uri = criarCaminho();
        Intent abrirCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        abrirCamera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        startActivityForResult(abrirCamera, 1);
    }
    /**
     *  Cria um Uri apontando para um arquivo onde vai ser salvo a imagem capturada pela camêra
     * @return O caminho Uri
     */
    private Uri criarCaminho(){
        dataDeCaptura = System.currentTimeMillis();
        tituloDaImagem = "Photomemo" + dataDeCaptura;
        File photoMemoDirectory = new File (Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "PhotoMemo");
        photoMemoDirectory.mkdirs();
        caminhoDaImagem = Uri.parse("file:" + photoMemoDirectory.getAbsolutePath() + "/" + tituloDaImagem + ".jpg");
        return caminhoDaImagem;
    } //Cria um arquivo onde será armazenada a foto
    /**
     * Atualiza os resultados cada vez que o usuario modificar o argumento da busca.
     */
    private TextWatcher atualizaBusca = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() == 0){
                criaAdapter();
            }
            else criaAdapter(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }; //É executado cada vez que o texto de busca é modificado

    /**
     * Inseri uma imagem temporária enquanto a imagem é carregada
     */
    static class AsyncDrawable extends BitmapDrawable {

        private final WeakReference<LoadBitmapIcon> bitmapWorkerTaskWeakReference;
        public AsyncDrawable (Resources res, Bitmap bitmap, LoadBitmapIcon bitmapWorkerTask){
            super (res, bitmap);
            bitmapWorkerTaskWeakReference = new WeakReference<LoadBitmapIcon>(bitmapWorkerTask);
        }

        public LoadBitmapIcon getBitmapWorkerTask(){
            return  bitmapWorkerTaskWeakReference.get();
        }

    } // Inseri uma imagem temporaria na lista enquanto um icone é criado
    private void loadBitmap (String caminhoDaImagem, ImageView imageView) {
        if (cancelPotentialWork(caminhoDaImagem, imageView)) {
            final LoadBitmapIcon task = new LoadBitmapIcon(imageView);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), BitmapFactory.decodeResource(getResources(), R.drawable.ic_crop_original_black_48dp), task);
            imageView.setImageDrawable(asyncDrawable);
            task.execute(caminhoDaImagem);
        }
    }
    private static boolean cancelPotentialWork (String caminho, ImageView imageView){
        final LoadBitmapIcon bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null){
            final String bitmapPath = bitmapWorkerTask.caminho;

            if (bitmapPath.equals("") || bitmapPath.equals(caminho)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }

        return true;
    } //Verifica se uma operação esta sendo executada em segundo plano e a finaliza caso nao seja mais necessaria
    private static LoadBitmapIcon getBitmapWorkerTask (ImageView imageView) {
        if (imageView != null){
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable){
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }
    private class LoadBitmapIcon extends AsyncTask<Object, Object, Bitmap>{

        private final WeakReference<ImageView> imageViewWeakReference;
        private String caminho = "";
        public LoadBitmapIcon (ImageView imageView){
            imageViewWeakReference = new WeakReference<ImageView>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Object... params) {
            caminho = (String) params[0];
            Bitmap bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(caminho), dimensoes.x / 3, dimensoes.x / 3);
            imagensEmCache.put(caminho, bitmap);
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

    }

    private AdapterView.OnItemClickListener openImage = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final ViewHolder itens = (ViewHolder) view.getTag();
            AlertDialog.Builder builder = new AlertDialog.Builder(TelaInicial.this);
            LayoutInflater inflater = LayoutInflater.from(TelaInicial.this);
            View v = inflater.inflate(R.layout.imagem_selecionada, null);
            ImageView imagem = (ImageView) v.findViewById(R.id.preVisualizacao);
            TextView descricao = (TextView) v.findViewById(R.id.descricao);
            TextView data = (TextView) v.findViewById(R.id.data);
            data.setText(itens.dataDeCaptura);
            descricao.setText(itens.descricaoDaImagem);
            imagem.setImageBitmap(ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(itens.caminhoDaImagem), dimensoes.x / 3, dimensoes.x / 3));
            builder.setTitle(getResources().getString(R.string.titulo) + itens.tituloImagem);
            builder.setView(v);
            builder.setPositiveButton(R.string.editar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent adicionarDescricao = new Intent(TelaInicial.this, AdicionarDescricao.class);
                    adicionarDescricao.putExtra("path", itens.caminhoDaImagem);
                    adicionarDescricao.putExtra("tituloDaImagem", itens.tituloImagem);
                    startActivity(adicionarDescricao);
                }
            });
            builder.setNeutralButton(R.string.voltar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(R.string.deletar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(TelaInicial.this);
                    builder.setTitle(getString(R.string.atencao));
                    builder.setNeutralButton(getString(R.string.voltar), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.setPositiveButton(getString(R.string.deletar), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            File deletar = new File(itens.caminhoDaImagem);
                            deletar.delete();
                            cr.delete(Media.EXTERNAL_CONTENT_URI, ImageColumns.TITLE + " = ?", new String[]{itens.tituloImagem});
                            criaAdapter();
                            renovarLayout();
                        }
                    });
                    AlertDialog confirmacao = builder.create();
                    confirmacao.show();
                }
            });
            builder.setCancelable(true);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    };
}