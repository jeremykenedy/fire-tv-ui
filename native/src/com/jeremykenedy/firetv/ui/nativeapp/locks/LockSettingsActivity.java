package com.jeremykenedy.firetv.ui.nativeapp.locks;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.io.IOException;
import java.util.Set;

public final class LockSettingsActivity extends Activity {
    private boolean startupBlocked;
    private String focus = "pin";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) focus = state.getString("focus", "pin");
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        state.putString("focus", focus); super.onSaveInstanceState(state);
    }

    private void render() {
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Launcher locks", 30));
        page.addView(Pages.text(this, "Hold an app or folder on Home to lock it. A PIN is required for each locked launch. Locks apply only inside this launcher; other launchers and system settings remain available. This is not system security or a parental-control service.", 16));
        page.addView(Pages.text(this, "The PIN and lock list stay on this device and are excluded from saved setups and portable backups. Keep your PIN: there is no PIN recovery in this launcher.", 16));
        try {
            final LockStore store = new LockStore(this);
            final boolean configured = store.hasPin();
            button(page, "pin", configured ? "Change launcher PIN" : "Create launcher PIN", new Runnable() {
                @Override public void run() {
                    if (configured) LauncherLocks.changePin(LockSettingsActivity.this, refresh());
                    else LauncherLocks.createPin(LockSettingsActivity.this, refresh());
                }
            });
            Set<String> targets = store.lockedTargets();
            if (!targets.isEmpty()) button(page, "all", "Remove all launcher locks", new Runnable() {
                @Override public void run() { LauncherLocks.unlockAll(LockSettingsActivity.this, refresh()); }
            });
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            list.addView(Pages.text(this, targets.isEmpty() ? "No locked apps or folders." : "Locked apps and folders", 20));
            for (final String target : targets) button(list, target, target, new Runnable() {
                @Override public void run() { LauncherLocks.showControls(LockSettingsActivity.this, target, target, refresh()); }
            });
        } catch (IOException failed) {
            page.addView(Pages.text(this, "Lock storage is unavailable. Locks were not reset. Launches remain blocked until storage is restored.", 18));
        }
    }

    private Runnable refresh() { return new Runnable() { @Override public void run() { render(); } }; }

    private void button(LinearLayout parent, final String key, String label, final Runnable action) {
        View button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focus = key; action.run(); }
        });
        parent.addView(button);
        if (focus.equals(key)) button.requestFocus();
    }
}
