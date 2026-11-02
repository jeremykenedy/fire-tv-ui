package com.plexapp.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Isolated test handler with no network or account access. */
public final class FakePlexActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.util.Log.i("PlexRouteFixture", "Synthetic handler reached; sending callback");
        sendBroadcast(new Intent("com.jeremykenedy.firetv.plexrouteproof.FAKE_OPENED")
                .setPackage("com.jeremykenedy.firetv.plexrouteproof"),
                "com.jeremykenedy.firetv.plexrouteproof.FIXTURE");
        finish();
    }
}
