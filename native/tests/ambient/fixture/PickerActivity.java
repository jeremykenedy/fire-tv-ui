package proof.ambient.fixture;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
public final class PickerActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);int mode=getIntent().getIntExtra("fixture_mode",0);if(mode==1){setResult(RESULT_CANCELED);finish();return;}Uri uri=Uri.parse("content://proof.ambient.fixture/"+(mode==2?"invalid":"silence"));Intent result=new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);result.setClipData(ClipData.newRawUri("Synthetic silent audio",uri));setResult(RESULT_OK,result);finish();}
}
