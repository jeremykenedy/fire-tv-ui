package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public final class FeedSettings {
    private FeedSettings() {}
    public static void show(final Activity activity) {
        final FeedStore store = new FeedStore(activity);
        FeedSource saved = null;
        try { saved = store.read(); } catch (Exception unavailable) { }
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density); body.setPadding(pad, pad, pad, pad);
        TextView explanation = new TextView(activity);
        explanation.setText("Choose a source you may use. Supports RSS 2.0 or JSON Feed 1/1.1 title-and-HTTPS-link items in UTF-8. No HTML rendering or automatic link opening. Source URLs stay on this TV and are excluded from portable backups.");
        body.addView(explanation);
        final EditText label = new EditText(activity); label.setSingleLine(true); label.setHint("Source name");
        label.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)}); label.setText(saved == null ? "" : saved.label); body.addView(label);
        final EditText url = new EditText(activity); url.setSingleLine(true); url.setHint("https:// selected feed URL");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        url.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)}); url.setText(saved == null ? "" : saved.url); body.addView(url);
        final RadioGroup format = new RadioGroup(activity);
        RadioButton rss = new RadioButton(activity); rss.setId(0x6f01); rss.setText("RSS 2.0"); format.addView(rss);
        RadioButton json = new RadioButton(activity); json.setId(0x6f02); json.setText("JSON Feed 1 / 1.1"); format.addView(json);
        format.check(saved != null && saved.format == FeedSource.Format.JSON_FEED ? 0x6f02 : 0x6f01); body.addView(format);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Custom feed").setView(body)
                .setNegativeButton("Cancel", null).setNeutralButton("Remove source", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) { store.clear(); }
                }).setPositiveButton("Use selected source", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            FeedSource source = new FeedSource(label.getText().toString(), url.getText().toString().trim(),
                                    format.getCheckedRadioButtonId() == 0x6f02 ? FeedSource.Format.JSON_FEED : FeedSource.Format.RSS_2);
                            store.save(source); dialog.dismiss();
                        } catch (IllegalArgumentException invalid) { url.setError(invalid.getMessage()); }
                        catch (Exception failed) { url.setError("The source could not be saved. Your previous selection was retained."); }
                    }
                });
            }
        });
        dialog.show();
    }
}
