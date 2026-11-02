package proof.photos.fixture;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
public final class PhotoProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME}); cursor.addRow(new Object[] {"Fixture " + uri.getLastPathSegment()}); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"one".equals(name) && !"two".equals(name) && !"invalid".equals(name)) throw new FileNotFoundException("Missing fixture");
        File file = new File(getContext().getCacheDir(), name + ".png");
        try {
            if (!file.isFile()) try (FileOutputStream output = new FileOutputStream(file)) {
                if ("invalid".equals(name)) output.write(new byte[] {1, 2, 3});
                else { Bitmap image = Bitmap.createBitmap(2560, 1280, Bitmap.Config.ARGB_8888); image.eraseColor("one".equals(name) ? Color.GREEN : Color.BLUE); image.compress(Bitmap.CompressFormat.PNG, 100, output); image.recycle(); }
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception error) { throw new FileNotFoundException("Fixture unavailable"); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
}
