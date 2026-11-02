package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class BackgroundPreviewActivity extends Activity {
    private BackgroundLayer layer;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout content=new FrameLayout(this);
        TextView hint=new TextView(this); hint.setText("Background preview   •   Press Back to return");
        hint.setTextColor(0xffffffff); hint.setTextSize(18); hint.setPadding(24,16,24,16); hint.setBackgroundColor(0x99101523);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        position.bottomMargin=24; content.addView(hint,position);
        layer=BackgroundLayer.attach(this,content); content.setBackgroundColor(0x00000000);
    }
    @Override protected void onResume() { super.onResume(); layer.setResumed(true); }
    @Override protected void onPause() { layer.setResumed(false); super.onPause(); }
}
