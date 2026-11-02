package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.content.Context;
import java.io.IOException;

/** Optional inventory step, called only for a deliberately selected app-private media reference. */
public final class AndroidPrivateMedia {
    private AndroidPrivateMedia() {}
    public static PrivateMediaReference verify(Context context, String uri) throws IOException {
        return PrivateMediaReference.verify(uri, context.getFilesDir(), context.getNoBackupFilesDir(), context.getCacheDir());
    }
}
