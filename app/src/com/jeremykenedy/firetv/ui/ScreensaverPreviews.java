package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;

/** Loads app-provided previews and screenshots captured from the TV. */
public final class ScreensaverPreviews {
    public final Drawable image;
    public final boolean available;

    private ScreensaverPreviews(Drawable image, boolean available) {
        this.image = image;
        this.available = available;
    }

    public static ScreensaverPreviews load(Context context, ServiceInfo service,
            String component) {
        String fileName = Screensavers.previewFile(component);
        // Device captures reflect local configuration and take precedence.
        File directory = context.getExternalFilesDir("screensaver-previews");
        if (directory != null) {
            Drawable capture = Drawable.createFromPath(new File(directory, fileName).getPath());
            if (capture != null) {
                return new ScreensaverPreviews(capture, true);
            }
        }
        // Bundled captures show the actual dream, while some app-provided
        // metadata contains only a generic or empty drawable.
        try (InputStream stream = context.getAssets().open("screensaver-previews/" + fileName)) {
            Drawable preview = Drawable.createFromStream(stream, fileName);
            if (preview != null) {
                return new ScreensaverPreviews(preview, true);
            }
        } catch (Exception ignored) {
            // Check the app's own preview next.
        }
        PackageManager pm = context.getPackageManager();
        if (service != null) {
            try (XmlResourceParser parser = service.loadXmlMetaData(pm, "android.service.dream")) {
                if (parser != null) {
                    int event;
                    do {
                        event = parser.next();
                    } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
                    if ("dream".equals(parser.getName())) {
                        int resource = parser.getAttributeResourceValue(
                                "http://schemas.android.com/apk/res/android", "previewImage", 0);
                        if (resource != 0) {
                            Drawable preview = pm.getResourcesForApplication(service.applicationInfo)
                                    .getDrawable(resource, null);
                            if (preview != null) {
                                return new ScreensaverPreviews(preview, true);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Invalid third-party metadata does not hide the screensaver.
            }
        }
        return new ScreensaverPreviews(service == null ? null : service.loadIcon(pm), false);
    }
}
