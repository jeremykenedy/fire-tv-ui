package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class RecommendationArtwork {
    private static final int LIMIT = 4 * 1024 * 1024;

    private RecommendationArtwork() {}

    static Bitmap load(Context context, String source, Object background) {
        if (!(background instanceof CharSequence) && !(background instanceof Uri)) return null;
        String value = background.toString();
        if (value.length() > 16384) return null;
        try {
            Uri uri = Uri.parse(value);
            byte[] data;
            if ("content".equals(uri.getScheme())) {
                if (uri.getAuthority() == null || uri.getFragment() != null) return null;
                ProviderInfo provider =
                        context.getPackageManager().resolveContentProvider(uri.getAuthority(), 0);
                if (provider == null
                        || !RecommendationArtworkRules.ownedContent(source, provider.packageName))
                    return null;
                // The resolver enforces the producer's normal export/URI-grant permissions.
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    data = bytes(input);
                }
            } else if (RecommendationArtworkRules.allowedHttps(source, value)) {
                data = download(source, value);
            } else return null;
            if (data == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth <= 0
                    || options.outHeight <= 0
                    || options.outWidth > 8192
                    || options.outHeight > 8192) return null;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 960
                    || options.outHeight / options.inSampleSize > 540) options.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static byte[] download(String source, String value) throws Exception {
        for (int redirects = 0; redirects <= 2; redirects++) {
            if (!RecommendationArtworkRules.allowedHttps(source, value)) return null;
            HttpURLConnection request = (HttpURLConnection) new URL(value).openConnection();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(3000);
            request.setReadTimeout(5000);
            request.setRequestProperty("Accept", "image/webp,image/jpeg,image/png");
            try {
                int status = request.getResponseCode();
                if (status == 301
                        || status == 302
                        || status == 303
                        || status == 307
                        || status == 308) {
                    String location = request.getHeaderField("Location");
                    if (location == null) return null;
                    value = new URL(new URL(value), location).toString();
                    continue;
                }
                if (status != 200 || request.getContentLength() > LIMIT) return null;
                String mime = request.getContentType();
                if (mime != null
                        && !mime.startsWith("image/")
                        && !mime.startsWith("application/octet-stream")) return null;
                try (InputStream input = request.getInputStream()) {
                    return bytes(input);
                }
            } finally {
                request.disconnect();
            }
        }
        return null;
    }

    private static byte[] bytes(InputStream input) throws Exception {
        if (input == null) return null;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > LIMIT) return null;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
