package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

public final class AdvancedActivity extends CardActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Advanced");
        LinearLayout body = scrollBody();
        body.addView(text("Inspect this TV without changing its settings.", 18, MUTED));
        android.widget.TextView diagnostics =
                button(
                        "Diagnostics",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                startActivity(
                                        new Intent(
                                                AdvancedActivity.this, DiagnosticsActivity.class));
                            }
                        });
        addButton(body, diagnostics);
        diagnostics.requestFocus();
    }
}
