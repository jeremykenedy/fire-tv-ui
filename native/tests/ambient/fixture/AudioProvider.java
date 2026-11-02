package proof.ambient.fixture;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
public final class AudioProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "audio/wav";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});cursor.addRow(new Object[]{"Synthetic silent audio"});return cursor;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        String name=uri.getLastPathSegment();if(!mode.equals("r")||!("silence".equals(name)||"invalid".equals(name)))throw new FileNotFoundException();File file=new File(getContext().getCacheDir(),name+".wav");
        try{if(!file.exists())try(FileOutputStream output=new FileOutputStream(file)){if(name.equals("invalid"))output.write(new byte[]{1,2,3});else{
            int bytes=8000*2*15;output.write("RIFF".getBytes("US-ASCII"));little(output,36+bytes,4);output.write("WAVEfmt ".getBytes("US-ASCII"));little(output,16,4);little(output,1,2);little(output,1,2);little(output,8000,4);little(output,16000,4);little(output,2,2);little(output,16,2);output.write("data".getBytes("US-ASCII"));little(output,bytes,4);output.write(new byte[bytes]);}}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(IOException error){throw new FileNotFoundException("Synthetic audio unavailable");}
    }
    private static void little(OutputStream output,int value,int bytes)throws IOException{for(int i=0;i<bytes;i++)output.write((value>>>(8*i))&255);}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
