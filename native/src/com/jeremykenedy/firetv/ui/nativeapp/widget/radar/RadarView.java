package com.jeremykenedy.firetv.ui.nativeapp.widget.radar;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class RadarView extends LinearLayout {
    private final TextView title, status, attribution;
    private final ImageView image;
    private RadarLoader loader;
    private Bitmap displayed;
    private RadarSource source;
    private boolean requested, listening;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if ("source".equals(key)) { pause(); begin(); }
        }
    };

    public RadarView(Context context, boolean full) {
        super(context); setOrientation(VERTICAL); setBackgroundColor(0xff111722);
        int pad = dp(full ? 24 : 12); setPadding(pad, pad, pad, pad);
        title = text(full ? 24 : 18); title.setTag("radar:title"); addView(title);
        status = text(full ? 17 : 14); status.setTag("radar:status"); addView(status);
        image = new ImageView(context); image.setTag("radar:image"); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false); addView(image, new LayoutParams(-1, 0, 1));
        attribution = text(full ? 15 : 12); attribution.setTag("radar:source"); addView(attribution);
        title.setText("NWS radar"); status.setText("Choose a supported region or station.");
        attribution.setText("Image: National Weather Service\nRadar image only. Not an alert service.");
        if (full && context instanceof Activity) {
            final Activity activity = (Activity) context;
            LinearLayout actions = new LinearLayout(context); addView(actions);
            Button choose = new Button(context); choose.setText("Choose area"); choose.setAllCaps(false);
            choose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { RadarSettings.show(activity); } }); actions.addView(choose);
            Button official = new Button(context); official.setText("Open NWS page"); official.setAllCaps(false);
            official.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { openOfficial(activity); } }); actions.addView(official);
        }
    }

    public static void show(final Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        final RadarView view = new RadarView(activity, true);
        dialog.setContentView(view);
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface owner) { view.stop(); }
        });
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
        view.start();
    }

    public void start() {
        requested = true;
        if (!listening) {
            getContext().getSharedPreferences(RadarPreferences.FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferences);
            listening = true;
        }
        begin();
    }

    private void begin() {
        if (!requested || loader != null || !isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE || !hasWindowFocus()) return;
        source = null;
        try { source = RadarPreferences.load(getContext()); }
        catch (IllegalStateException invalid) { title.setText("NWS radar"); status.setText(invalid.getMessage()); return; }
        if (source == null) { title.setText("NWS radar"); status.setText("Choose a supported region or station."); return; }
        title.setText(source.label);
        status.setText("Loading selected radar image...");
        image.setContentDescription("Complete NWS standard radar image for " + source.label);
        loader = new RadarLoader(getContext().getApplicationContext(), source, new RadarLoader.Listener() {
            @Override public void loaded(RadarSnapshot snapshot, Bitmap bitmap, String error, boolean retainImage) {
                render(snapshot, bitmap, error, retainImage);
            }
        }); loader.start();
    }

    void render(RadarSnapshot snapshot, Bitmap bitmap, String error) { render(snapshot, bitmap, error, false); }

    private void render(RadarSnapshot snapshot, Bitmap bitmap, String error, boolean retainImage) {
        if(snapshot != null) title.setText(snapshot.source.label);
        if(!retainImage) { clearImage(); displayed = bitmap; image.setImageBitmap(bitmap); }
        status.setText(error != null ? error : snapshot == null ? "Radar image unavailable." : snapshot.message);
        if (snapshot == null || snapshot.image == null) {
            attribution.setText("Image: National Weather Service\nRadar image only. Not an alert service.");
        } else {
            String modified = snapshot.modifiedAt <= 0 ? "unknown" : utc(snapshot.modifiedAt);
            attribution.setText("Image: National Weather Service (radar.weather.gov)\nDownloaded: "
                    + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new Date(snapshot.fetchedAt))
                    + "\nFile modified: " + modified + "\nProduct time and legend are printed in the image. Not an alert service.");
        }
    }

    private void openOfficial(Activity activity) {
        if (source == null) { RadarSettings.show(activity); return; }
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source.pageUrl()))); }
        catch (android.content.ActivityNotFoundException missing) {
            new android.app.AlertDialog.Builder(activity).setMessage("No browser is available. Official radar: radar.weather.gov")
                    .setPositiveButton("Done", null).show();
        }
    }
    private static String utc(long time) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC")); return formatter.format(new Date(time));
    }
    private TextView text(int size) { TextView view = new TextView(getContext()); view.setTextSize(size); view.setTextColor(Color.WHITE); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void clearImage() { image.setImageDrawable(null); if (displayed != null) displayed.recycle(); displayed = null; }
    private void pause() {
        if (loader != null) loader.close(); loader = null; clearImage();
        attribution.setText("Image: National Weather Service\nRadar image only. Not an alert service.");
    }
    public void stop() {
        requested = false; pause();
        if (listening) getContext().getSharedPreferences(RadarPreferences.FILE, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferences);
        listening = false;
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); begin(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) begin(); else pause(); }
    @Override protected void onVisibilityChanged(View view, int visibility) { super.onVisibilityChanged(view, visibility); if (image != null) { if (isShown()) begin(); else pause(); } }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(image != null) { if(visibility == VISIBLE) begin(); else pause(); }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
