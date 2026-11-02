package com.jeremykenedy.firetv.ui.updatefixture;

import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinator;
import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdatesActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

/** Test APK only. Fixture asset metadata is bundled by the fixture builder, never fetched. */
public final class FixtureActivity extends UpdatesActivity {
    private String variant;
    @Override public void onCreate(Bundle state) {
        variant = getIntent().getStringExtra("variant");
        if (variant == null) variant = "upgrade";
        super.onCreate(state);
    }
    @Override protected UpdateCoordinator.Source releaseSource() {
        return new UpdateCoordinator.Source() {
            @Override public UpdateCoordinator.Release check(UpdateCoordinator.Cancellation cancellation) throws Exception {
            cancellation.check();
            JSONObject metadata;
            try (InputStream input = getAssets().open(variant + ".json")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 16384) throw new IllegalArgumentException("Oversized fixture metadata");
                    output.write(buffer, 0, count);
                }
                metadata = new JSONObject(output.toString("UTF-8"));
            } catch (java.io.FileNotFoundException absent) { return null; }
            return new UpdateCoordinator.Release("fixture/update-tests", "v2", getPackageName(),
                    "https://github.com/fixture/update-tests/releases/download/v2/fixture.apk",
                    metadata.getString("sha256"), metadata.getLong("bytes"), 2, "Fixture 2",
                    "LOCAL TEST FIXTURE ONLY. No published release or production endpoint exists.");
            }
        };
    }
    @Override protected UpdateCoordinator.Transport releaseTransport() {
        return new UpdateCoordinator.Transport() {
            @Override public InputStream open(UpdateCoordinator.Release release, UpdateCoordinator.Cancellation cancellation) throws Exception {
                return getAssets().open(variant + ".apk");
            }
        };
    }
}
