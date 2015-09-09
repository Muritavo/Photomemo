package com.muritavo.photomemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import static android.provider.MediaStore.Images.*;

public class TelaInicial extends Activity {
    private boolean gridViewEstaAtivo; // Ultimo modo de view escolhido pelo usuario.

    //Variaveis que indicam o local onde será salva a imagem que o usuario vai capturar
    private Uri caminhoDaImagem = null;
    private String tituloDaImagem;
    private long dataDeCaptura = 0;

    //Variaveis utilizadas na ação de seleção de diversas imagens
    private ArrayList<Uri> imagensSelecionadas;
    private boolean estaSelecionando;

    //Elementos pertencentes ao layout
    private LinearLayout layoutLista; //A parte do layout que mantem a lista de imagens
    private LinearLayout layoutCampoDeBusca; //A parte do layout que mantem o campo de busca
    private Button ocultarCampoDeBusca;
    private EditText campoDeBuscaEditText;

    //
    private imageCursorAdapter adapter; //Adapter utilizado para popular a lista ou grade
    private BancoDeDadosPhotomemo bancoDeDadosPhotomemo;
    private ImageLoaderAsync carregadorDeImagens;
    private ContentResolver cr;
    public static Point dimensoes;
    private SimpleDateFormat formatter; //Utilizado para converter a data de captura em um formato de data
    private LruCache<String, Bitmap> imagensEmCache; //Cache onde serão armazenadas os icones de cada imagem
    private SharedPreferences sharedPreferences;
    private MediaScannerConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tela_inicial);

        //Obtem o ultimo modo de visualização deixado pelo usuario
        sharedPreferences = getSharedPreferences("photomemoTags", MODE_PRIVATE);

        estaSelecionando = false;
        cr = this.getContentResolver();
        //Obtem o objeto que vai fazer a conexao com o banco de dados do aplicativo.
        bancoDeDadosPhotomemo = ((Photomemo) this.getApplication()).getBancoDeDadosPhotomemo(getApplicationContext());
        formatter = ((Photomemo) this.getApplication()).getFormatter();
        // Obtem o objeto que vai armazenar as imagens
        imagensEmCache = ((Photomemo) this.getApplication()).getImagensEmCache();
        // Obtem o objeto que vai carregar as imagens
        carregadorDeImagens = ((Photomemo) this.getApplication()).getCarregadorDeImagem(getApplicationContext());
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
            estaSelecionando = (Boolean) savedInstanceState.get("estaSelecionando");
            imagensSelecionadas = (ArrayList<Uri>) savedInstanceState.get("imagensSelecionadas");
        }
        //True:Cria uma Grade com os icones | False: Cria uma lista com os icones e descrição
        gridViewEstaAtivo = sharedPreferences.getBoolean("gridViewEstaAtivo", true);
        layoutLista = (LinearLayout) findViewById(R.id.rootLinearLayout);
        layoutCampoDeBusca = (LinearLayout) findViewById(R.id.campoDeBusca);
        campoDeBuscaEditText = (EditText) findViewById(R.id.campoDeBuscaEditText);
        ocultarCampoDeBusca = (Button) findViewById(R.id.ocultarCampo);
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
        MenuItem botaoEnvio = menu.findItem(R.id.compartilharImagens);
        MenuItem botaoCamera = menu.findItem(R.id.abrir_camera);
        MenuItem botaoDeletar = menu.findItem(R.id.deletar_multiplos);
        //Ativa ou desativa o botao de envio conforme a necessidade.
        if (estaSelecionando) {
            botaoDeletar.setVisible(true);
            botaoEnvio.setVisible(true);
            botaoBusca.setVisible(false);
            botaoCamera.setVisible(false);
            botaoLista.setVisible(false);
            botaoGrade.setVisible(false);
        }
        else botaoEnvio.setVisible(false);

        if (layoutCampoDeBusca.getVisibility() == View.VISIBLE){
            botaoBusca.setVisible(false);
        } //Esconde o botao de busca se o campo de busca estiver visivel

        if (gridViewEstaAtivo && !estaSelecionando){
            botaoLista.setVisible(true);
            botaoGrade.setVisible(false);
        }// Esconde o botao de lista e mostra o de grade

        else if (!estaSelecionando){
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
                        criaAdapter();
                        invalidateOptionsMenu();
                    }
                });
                break;
            case R.id.compartilharImagens:
                Intent enviarImagens = new Intent(Intent.ACTION_SEND_MULTIPLE);
                enviarImagens.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                enviarImagens.setType("image/jpeg");
                enviarImagens.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imagensSelecionadas);
                startActivity(Intent.createChooser(enviarImagens, getResources().getString(R.string.enviar)));
                break;
            case R.id.deletar_multiplos:
                for (int i = 0; i < imagensSelecionadas.size(); i++){
                    deletarImagem(imagensSelecionadas.get(i).toString().replace("file://", ""));
                }
                imagensSelecionadas.clear();
                estaSelecionando = false;
                invalidateOptionsMenu();
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == -1){
            connection = new MediaScannerConnection(this, mediaScannerConnectionClient);
            connection.connect();
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
        outState.putBoolean("estaSelecionando", estaSelecionando);
        outState.putParcelableArrayList("imagensSelecionadas", imagensSelecionadas);
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

    private void deletarImagem (String caminhoDaImagem){
        File deletar = new File(caminhoDaImagem);
        {
            Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, new String[]{ImageColumns._ID, ImageColumns.DATA}, ImageColumns.DATA + " = ?", new String[]{caminhoDaImagem}, null);
            cursor.moveToFirst();
            int identificador = cursor.getInt(cursor.getColumnIndex(ImageColumns._ID));
            bancoDeDadosPhotomemo.deletar(identificador);
            cr.delete(Media.EXTERNAL_CONTENT_URI, ImageColumns.DATA + " LIKE ?", new String[]{caminhoDaImagem});
        }
    }
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
        int identificador;
        boolean selecionado;
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
                holder.imagem.setMinimumHeight(dimensoes.y / 3);
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
            holder.identificador = cursor.getInt(cursor.getColumnIndex(ImageColumns._ID));
            holder.tituloImagem = cursor.getString(cursor.getColumnIndex(ImageColumns.TITLE));
            holder.dataDeCaptura = formatter.format(cursor.getLong(cursor.getColumnIndex(ImageColumns.DATE_TAKEN)));
            holder.caminhoDaImagem = cursor.getString(cursor.getColumnIndex(ImageColumns.DATA));
            holder.descricaoDaImagem = bancoDeDadosPhotomemo.buscarDescricao(holder.identificador);
            if (imagensSelecionadas != null && imagensSelecionadas.contains(Uri.parse("file://" + holder.caminhoDaImagem))) view.setBackgroundColor(getResources().getColor(R.color.gray));
            else view.setBackgroundColor(getResources().getColor(R.color.white));
            if (imagensEmCache.get(holder.caminhoDaImagem) == null){
                carregadorDeImagens.carregarBitmap(holder.caminhoDaImagem, holder.imagem, false);
            }
            else{
                holder.imagem.setImageBitmap(imagensEmCache.get(holder.caminhoDaImagem));
            }
            if (!gridViewEstaAtivo){
                holder.titulo.setText(holder.tituloImagem);
                holder.data.setText(holder.dataDeCaptura);
                if (holder.descricaoDaImagem != null) holder.descricao.setText(holder.descricaoDaImagem);
                else holder.descricao.setText(getResources().getString(R.string.descricao_inexistente));
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
                ImageColumns.DATE_TAKEN
        }; // Colunas que serão requeridas do banco de dados do celular
        String comandoWhere = ImageColumns.DATA + " LIKE ?"; //Comando WHERE do 3º argumento da busca do Content Resolver || ? representa onde serao colocados os argumentos do comando
        String[] argumentos = new String[]{
                "%Photomemo%",
        }; //Os argumentos do comando WHERE que serão substituidos no caracter '?'
        Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, colunas, comandoWhere, argumentos, MediaStore.Images.ImageColumns.DATE_TAKEN);

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
                ImageColumns.DATE_TAKEN
        }; // Colunas que serão requeridas do banco de dados do celular
        String comandoWhere = ImageColumns.DATA + " LIKE '%Photomemo%' AND " + ImageColumns._ID + " LIKE ? "; //Comando WHERE do 3º argumento da busca do Content Resolver || ? representa onde serao colocados os argumentos do comando
        String [] argumentos = bancoDeDadosPhotomemo.buscarDescricao(argumento);
        if (argumentos.length > 1){
            for (int i = 1; i < argumentos.length; i++){
                comandoWhere = comandoWhere + " OR " + ImageColumns._ID + " LIKE ? ";
            }
        }
        Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, colunas, comandoWhere, argumentos, MediaStore.Images.ImageColumns.DATE_TAKEN);
        cursor.getCount();
        adapter.changeCursor(cursor);
    }
    /**
     * Renova o layout do usuario cada vez que o conteudo for modificado
     */
    private void renovarLayout() {
        layoutLista.removeAllViews();
        if (imagensSelecionadas != null) imagensSelecionadas.clear();
        if (gridViewEstaAtivo) {
            GridView gridItens = new GridView(this);
            gridItens.setColumnWidth(dimensoes.x / 5);
            gridItens.setNumColumns(GridView.AUTO_FIT);
            gridItens.setAdapter(adapter);
            gridItens.setOnItemClickListener(abrirImagem);
            gridItens.setOnItemLongClickListener(comecarSelecao);
            layoutLista.addView(gridItens, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        else {
            ListView lista = new ListView(this);
            lista.setAdapter(adapter);
            lista.setOnItemClickListener(abrirImagem);
            lista.setOnItemLongClickListener(comecarSelecao);
            layoutLista.addView(lista, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        invalidateOptionsMenu();
    }
    /**
     * Inicializa uma atividade da câmera para que o usuario possa capturar uma nova foto
     */
    private void requisitaFoto(){
        dataDeCaptura = System.currentTimeMillis();
        tituloDaImagem = "Photomemo" + dataDeCaptura;
        File photoMemoDirectory = new File (Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "PhotoMemo");
        if (!photoMemoDirectory.exists()){
            photoMemoDirectory.mkdirs();
        }
        caminhoDaImagem = Uri.parse("file:" + photoMemoDirectory.getAbsolutePath() + "/" + tituloDaImagem + ".jpg");
        Intent abrirCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        abrirCamera.putExtra(MediaStore.EXTRA_OUTPUT, caminhoDaImagem);
        startActivityForResult(abrirCamera, 1);
    }
    /**
     * Atualiza os resultados cada vez que o usuario modificar o argumento da busca.
     */
    private TextWatcher atualizaBusca = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (imagensSelecionadas != null){
                imagensSelecionadas.clear();
                invalidateOptionsMenu();
            }
            if (s.length() == 0){
                criaAdapter();
            }
            else criaAdapter(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }; //É executado cada vez que o texto de busca é modificado

    private AdapterView.OnItemLongClickListener comecarSelecao = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            ViewHolder itens = (ViewHolder) view.getTag();
            estaSelecionando = true;
            parent.setOnItemLongClickListener(null);
            parent.setOnItemClickListener(seletor);
            imagensSelecionadas = new ArrayList<Uri>();
            imagensSelecionadas.add(Uri.parse("file://" + itens.caminhoDaImagem));
            view.setBackgroundColor(getResources().getColor(R.color.gray));
            invalidateOptionsMenu();

            return true;
        }
    };

    private AdapterView.OnItemClickListener seletor =   new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ViewHolder itens = (ViewHolder) view.getTag();
            Uri UriDaImagem = Uri.parse("file://" + itens.caminhoDaImagem);
            if (imagensSelecionadas.contains(UriDaImagem)){
                imagensSelecionadas.remove(UriDaImagem);
                view.setBackgroundColor(getResources().getColor(R.color.white));
            }
            else {
                imagensSelecionadas.add(UriDaImagem);
                view.setBackgroundColor(getResources().getColor(R.color.gray));
            }
            if (imagensSelecionadas.size() == 0){
                estaSelecionando = false;
                parent.setOnItemLongClickListener(comecarSelecao);
                parent.setOnItemClickListener(abrirImagem);
                invalidateOptionsMenu();
            }
        }
    };

    private AdapterView.OnItemClickListener abrirImagem = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final ViewHolder itens = (ViewHolder) view.getTag();
            AlertDialog.Builder builder = new AlertDialog.Builder(TelaInicial.this);
            LayoutInflater inflater = LayoutInflater.from(TelaInicial.this);
            View v = inflater.inflate(R.layout.imagem_selecionada, null);
            v.setMinimumHeight(dimensoes.x);
            ImageView imagem = (ImageView) v.findViewById(R.id.preVisualizacao);
            TextView descricao = (TextView) v.findViewById(R.id.descricao);
            TextView data = (TextView) v.findViewById(R.id.data);
            data.setText(itens.dataDeCaptura);
            if (itens.descricaoDaImagem == null) descricao.setText(getResources().getString(R.string.descricao_inexistente));
            else descricao.setText(itens.descricaoDaImagem);
            imagem.setImageBitmap(imagensEmCache.get(itens.caminhoDaImagem));
            builder.setTitle(getResources().getString(R.string.titulo) + itens.tituloImagem);
            builder.setView(v);
            builder.setPositiveButton(R.string.editar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent adicionarDescricao = new Intent(TelaInicial.this, AdicionarDescricao.class);
                    adicionarDescricao.putExtra("path", itens.caminhoDaImagem);
                    adicionarDescricao.putExtra("tituloImagem", itens.tituloImagem);
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
                            if (deletar.delete()){
                                cr.delete(Media.EXTERNAL_CONTENT_URI, ImageColumns.TITLE + " = ?", new String[]{itens.tituloImagem});
                                bancoDeDadosPhotomemo.deletar(itens.identificador);
                            }
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
    private MediaScannerConnectionClient mediaScannerConnectionClient = new MediaScannerConnectionClient() {

        @Override
        public void onMediaScannerConnected() {
            connection.scanFile(caminhoDaImagem.toString().replace("file:", ""), "image/jpeg");
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            Intent adicionarDescricao = new Intent(TelaInicial.this, AdicionarDescricao.class);
            adicionarDescricao.putExtra("path", caminhoDaImagem.toString().replace("file:", ""));
            adicionarDescricao.putExtra("tituloImagem", tituloDaImagem);
            startActivity(adicionarDescricao);
        }
    };
}