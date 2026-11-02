package com.jeremykenedy.firetv.nowplayingfixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("publish".equals(intent.getStringExtra("command"))) FixtureSession.publish(context);
        if ("release".equals(intent.getStringExtra("command"))) FixtureSession.release();
    }
}
