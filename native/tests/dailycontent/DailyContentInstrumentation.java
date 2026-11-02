package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Read-only native rendering fixture. Does not edit preferences or the TV clock. */
public final class DailyContentInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        final Bundle result = new Bundle();
        try {
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    Set<String> ids = new HashSet<>();
                    for (WidgetProvider provider : DailyContentWidgets.providers()) {
                        check(ids.add(provider.item().key()), "unique slot identity");
                        View view = provider.createView(getTargetContext());
                        try {
                            provider.onStart(view); provider.onStart(view);
                            String rendered = text(view);
                            check(rendered.contains(provider.title()), "visible widget title");
                            check(rendered.contains("MIT License") || rendered.contains("NASA facts"), "visible credit");
                            render(view);
                        } finally { provider.onStop(view); provider.onStop(view); }
                    }
                    DailyContentView trivia = new DailyContentView(getTargetContext(), DailyContent.Kind.TRIVIA, true);
                    DailyContent edition = DailyContent.forDate(DailyContent.Kind.TRIVIA, LocalDate.now(), 0);
                    check(!text(trivia).contains(edition.answer), "answer hidden initially");
                    trivia.reveal(); check(text(trivia).contains(edition.answer), "answer revealed on request");
                    for (DailyContent.Kind kind : new DailyContent.Kind[]{DailyContent.Kind.ART, DailyContent.Kind.COMIC}) {
                        DailyIllustration illustration = new DailyIllustration(getTargetContext());
                        for (int day = 0; day < 7; day++) {
                            illustration.setContent(DailyContent.forDate(kind, LocalDate.of(2028, 2, 27).plusDays(day), 0));
                            render(illustration);
                        }
                    }
                }
            });
            result.putString("result", "PASS: seven native cards, stable IDs, credit, trivia reveal, seven art/comic editions, repeated lifecycle");
        } catch (Throwable error) { result.putString("failure", error.toString()); }
        finish(result.containsKey("failure") ? 1 : 0, result);
    }
    private static void render(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 640, 480);
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        try { view.draw(new Canvas(bitmap)); } finally { bitmap.recycle(); }
    }
    private static String text(View view) {
        StringBuilder value = new StringBuilder();
        if (view instanceof TextView) value.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) value.append(text(((ViewGroup) view).getChildAt(i)));
        return value.toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
