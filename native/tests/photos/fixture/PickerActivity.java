package proof.photos.fixture;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
public final class PickerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); int mode = getIntent().getIntExtra("fixture_mode", 0);
        if (mode == 1) { setResult(RESULT_CANCELED); finish(); return; }
        Uri one = Uri.parse("content://proof.photos.fixture/" + (mode == 2 ? "invalid" : mode == 3 ? "two" : "one"));
        Intent result = new Intent().setData(one).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        ClipData clip = ClipData.newRawUri("Selected fixture", one);
        if (mode == 0) clip.addItem(new ClipData.Item(Uri.parse("content://proof.photos.fixture/two")));
        result.setClipData(clip); setResult(RESULT_OK, result); finish();
    }
}
