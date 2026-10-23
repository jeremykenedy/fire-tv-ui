package com.jeremykenedy.firetv.ui;

import android.os.Bundle;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PreviewCreditsActivity extends CardActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Preview media credits");
        LinearLayout body = scrollBody();
        body.addView(
                text(
                        "The bundled Jellyfish Drift preview uses the Deep ocean scene credited"
                            + " below.",
                        18,
                        MUTED));
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                getAssets().open("media-notices/jellyfish-drift.txt"),
                                StandardCharsets.UTF_8))) {
            StringBuilder notice = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                notice.append(line).append('\n');
            }
            body.addView(text(notice.toString(), 17, INK));
        } catch (java.io.IOException unavailable) {
            body.addView(text("The bundled media notice could not be read.", 18, INK));
        }
        body.setFocusable(true);
        body.requestFocus();
    }
}
