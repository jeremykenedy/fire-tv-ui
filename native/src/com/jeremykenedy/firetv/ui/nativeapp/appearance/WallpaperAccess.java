package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;

final class WallpaperAccess {
    private WallpaperAccess() {}
    static boolean supported(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) return true;
        if (!"file".equals(uri.getScheme()) || uri.getPath()==null) return false;
        try {
            String path=new File(uri.getPath()).getCanonicalPath();
            File[] roots={context.getFilesDir(),context.getNoBackupFilesDir(),context.getCacheDir()};
            for(File root:roots) if(path.startsWith(root.getCanonicalPath()+File.separator)) return true;
        } catch(IOException failure) { return false; }
        return false;
    }
}
