package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidLegacyIconMigration;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/** Run only in a disposable fixture package. */
public final class LegacyIconMigrationInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        File original = new File(context.getCacheDir(), "legacy-icon-fixture.png");
        String choice = null;
        try {
            Bitmap image = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888);
            image.eraseColor(Color.GREEN);
            try (FileOutputStream output = new FileOutputStream(original)) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Fixture failed.");
            } finally { image.recycle(); }
            byte[] originalBytes = read(original);
            String uri = Uri.fromFile(original).toString();
            Map<String, String> first = AndroidLegacyIconMigration.prepare(context, Arrays.asList(uri, uri));
            choice = first.get(uri);
            check(first.size() == 1 && choice != null, "Exact duplicate references must share a prepared choice.");
            check(first.equals(AndroidLegacyIconMigration.prepare(context, Collections.singletonList(uri))), "Restart retry must reuse the prepared choice.");
            File copy = new File(new File(context.getFilesDir(), "icons"), choice.substring(6));
            check(Arrays.equals(originalBytes, read(original)) && Arrays.equals(originalBytes, read(copy)),
                    "Preparation must preserve and copy the exact original bytes.");
            String key = "fixture.icon/.Main";
            IconSelectionStore.save(context, key, choice);
            Bitmap rendered = ((BitmapDrawable) IconSelectionStore.resolve(context, key,
                    new ColorDrawable(Color.RED), null)).getBitmap();
            check(rendered.getWidth() <= 512 && rendered.getWidth() == rendered.getHeight() * 2,
                    "Imported artwork must render bounded and proportional.");
            IconSelectionStore.save(context, key, IconSelectionStore.THEME);
            IconSelectionStore.save(context, key, IconSelectionStore.FACTORY);
            try {
                AndroidLegacyIconMigration.prepare(context, Collections.singletonList("file:///missing/legacy-icon.png"));
                throw new AssertionError("Missing source must fail preparation.");
            } catch (IOException expected) {}
            check(IconSelectionStore.FACTORY.equals(IconSelectionStore.choice(context, key)),
                    "An unavailable source must preserve the existing choice.");
            IconSelectionStore.save(context, key, IconSelectionStore.THEME);
            result.putString("status", "PASS");
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            original.delete();
            if (choice != null) IconSelectionStore.discardPreparedImage(context, choice);
        }
        finish(0, result);
    }

    private static byte[] read(File file) throws IOException {
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
