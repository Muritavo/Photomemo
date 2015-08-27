package com.muritavo.photomemo;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class CompartilharImagem extends ListActivity {
    private LruCache<String, Bitmap> imagensEmCache;
    private SimpleDateFormat formatter;
    private ListView lista;
    private ArrayList<Uri> files;
    private Point dimensoes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        files = new ArrayList<Uri>(); // Array que vai armazenar o caminho para cada imagem que sera enviada
        formatter = ((Photomemo) getApplication()).getFormatter(); // Formato de data
        imagensEmCache = ((Photomemo) getApplication()).getImagensEmCache(); // Variavel de aplicacao que guarda os icones de cada imagem
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.DATA,
                        MediaStore.Images.ImageColumns.TITLE,
                        MediaStore.Images.ImageColumns.DESCRIPTION,
                        MediaStore.Images.ImageColumns.DATE_TAKEN
                },
                MediaStore.Images.ImageColumns.TITLE + " LIKE ? ",
                new String[]{"Photomemo%"},
                MediaStore.Images.ImageColumns.TITLE); //SELECT a lista de imagens do banco de dados do smartphone
        ImageCursorAdapter imageCursorAdapter = new ImageCursorAdapter(this, cursor); //Adaptador para mostrar as imagens em uma lista ou grade
        setListAdapter(imageCursorAdapter); //Adiciona o adapter criado anteriormente para popular a lista
        lista = getListView(); //Faz referencia ao objeto ListView da Activity
        lista.setOnItemClickListener(listener); //Adiciona um listener a lista
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_compartilhar_imagem, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.enviar) {
            if (files.size() == 0){
                Toast.makeText(this, getResources().getString(R.string.aviso), Toast.LENGTH_LONG).show();
            }
            else {
                Intent enviarImagens = new Intent(Intent.ACTION_SEND_MULTIPLE);
                enviarImagens.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                enviarImagens.setType("image/jpeg");
                enviarImagens.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                startActivity(Intent.createChooser(enviarImagens, getResources().getString(R.string.enviar)));
                files.clear();
            }
            lista.getCheckedItemIds();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ViewHolder{
        String tituloImagem;
        String descricaoDaImagem;
        String dataDeCaptura;
        String caminhoDaImagem;
        ImageView imagem;
        TextView descricao;
        TextView data;
        TextView titulo;
        ImageView checkBox;
    } // mantem todos os objetos pertencentes a view para que estes nao se percam durante a reciclagem de views do objeto ListView
    public class ImageCursorAdapter extends CursorAdapter {
        ViewHolder holder;

        public ImageCursorAdapter (Context context, Cursor c){
            super(context, c);
        }
        @Override
        public View newView (Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            holder = new ViewHolder();
            View v = inflater.inflate(R.layout.list_view_button, parent, false);
            holder.imagem = (ImageView) v.findViewById(R.id.icone);
            holder.titulo = (TextView) v.findViewById(R.id.titulo);
            holder.data = (TextView) v.findViewById(R.id.data);
            holder.descricao = (TextView) v.findViewById(R.id.descricao);
            holder.checkBox = (ImageView) v.findViewById(R.id.checked);
            v.setTag(holder);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            holder = (ViewHolder) view.getTag();
            holder.imagem.setImageBitmap(null);
            holder.tituloImagem = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.TITLE));
            holder.dataDeCaptura = formatter.format(cursor.getLong(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)));
            if ((holder.descricaoDaImagem = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DESCRIPTION))) == null) {
                holder.descricaoDaImagem = getResources().getString(R.string.descricao_inexistente);
            }
            holder.caminhoDaImagem = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
            if (imagensEmCache.get(holder.caminhoDaImagem) == null){
                loadBitmap(holder.caminhoDaImagem, holder.imagem);
            }
            else if (files.contains(Uri.parse("file://" + holder.caminhoDaImagem))) {
                holder.imagem.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_black_48dp));
            }
            else {
                    holder.imagem.setImageBitmap(imagensEmCache.get(holder.caminhoDaImagem));
            }
            holder.titulo.setText(holder.tituloImagem);
            holder.data.setText(holder.dataDeCaptura);
            holder.descricao.setText(holder.descricaoDaImagem);
        }
    }

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
    public static boolean cancelPotentialWork (String caminho, ImageView imageView){
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
            Bitmap bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(caminho), TelaInicial.dimensoes.x / 3, TelaInicial.dimensoes.x / 3);
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

    private AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ViewHolder holder = (ViewHolder) view.getTag();
            if (files.contains(Uri.parse("file://" + holder.caminhoDaImagem))){
                files.remove(Uri.parse("file://" + holder.caminhoDaImagem));
                if (imagensEmCache.get(holder.caminhoDaImagem) == null) holder.imagem.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_crop_original_black_48dp));
                else holder.imagem.setImageBitmap(imagensEmCache.get(holder.caminhoDaImagem));
            }
            else {
                files.add(Uri.parse("file://" + holder.caminhoDaImagem));
                holder.imagem.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_black_48dp));
            }
        }
    };
}
