package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Standalone test package, with no INTERNET permission or production private data. */
public final class FeedFixtureActivity extends Activity {
    static volatile boolean fail, block, empty;
    static volatile int requests;
    FeedView widget;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            new FeedStore(this).save(new FeedSource("Native fixture", "https://fixture.invalid/rss?private=local-fixture", FeedSource.Format.RSS_2));
        } catch (IOException failed) { throw new IllegalStateException(failed); }
        widget = new FeedView(this, new FeedClient(new FeedClient.Transport() {
            @Override public InputStream open(FeedSource source, FeedParser.Cancellation cancellation) throws Exception {
                requests++;
                while (block) { cancellation.check(); Thread.sleep(10); }
                cancellation.check();
                if (fail) throw new IOException("Fixture unavailable");
                String body = empty ? "<rss version='2.0'><channel/></rss>"
                        : "<rss version='2.0'><channel><item><guid>native-one</guid><title>Native fixture headline</title><link>https://fixture.invalid/story</link></item></channel></rss>";
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }
        }));
        widget.setPadding(40, 40, 40, 40); setContentView(widget);
    }
    @Override protected void onStart() { super.onStart(); widget.start(); }
    @Override protected void onStop() { widget.stop(); super.onStop(); }
}
