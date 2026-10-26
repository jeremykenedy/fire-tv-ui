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
                        "Attribution and complete license text for bundled screensaver previews.",
                        18,
                        MUTED));
        try {
            String directory = "screensaver-previews/licenses";
            String[] names = getAssets().list(directory);
            if (names == null || names.length == 0)
                throw new java.io.IOException("No preview notices were bundled.");
            java.util.Arrays.sort(names);
            for (String name : names) {
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        getAssets().open(directory + "/" + name),
                                        StandardCharsets.UTF_8))) {
                    StringBuilder notice = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (notice.length() + line.length() > 256 * 1024)
                            throw new java.io.IOException(
                                    "A preview notice exceeds the supported size.");
                        notice.append(line).append('\n');
                    }
                    body.addView(text(name, 20, INK));
                    body.addView(text(notice.toString(), 17, INK));
                }
            }
        } catch (java.io.IOException unavailable) {
            body.addView(text("The bundled media notices could not be read.", 18, INK));
        }
        body.setFocusable(true);
        body.requestFocus();
    }
}
