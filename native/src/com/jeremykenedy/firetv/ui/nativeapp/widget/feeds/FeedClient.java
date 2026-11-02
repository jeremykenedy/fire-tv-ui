package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Same-host HTTPS only; no authentication headers, remote redirects or unbounded response bodies. */
public final class FeedClient {
    public interface Transport { InputStream open(FeedSource source, FeedParser.Cancellation cancellation) throws Exception; }
    private final Transport transport;
    public FeedClient(Transport transport) { this.transport = transport; }
    public List<FeedParser.Item> fetch(FeedSource source, FeedParser.Cancellation cancellation) throws Exception {
        cancellation.check();
        try (InputStream input = transport.open(source, cancellation)) { return FeedParser.read(source.format, input, cancellation); }
    }
    public static final class Https implements Transport {
        @Override public InputStream open(FeedSource source, final FeedParser.Cancellation cancellation) throws Exception {
            URL url = FeedSource.validateUrl(source.url).toURL();
            String originalHost = url.getHost(); final long deadline = System.nanoTime() + 30000000000L;
            for (int redirect = 0; redirect <= 3; redirect++) {
                cancellation.check();
                FeedSource.validateUrl(url.toExternalForm());
                if (!originalHost.equalsIgnoreCase(url.getHost())) throw new IOException("Feed redirects must stay on the selected host.");
                if (System.nanoTime() > deadline) throw new IOException("Feed request timed out.");
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false); connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Fire-TV-UI-Selected-Feed");
                connection.setRequestProperty("Accept", "application/rss+xml, application/feed+json, application/json, application/xml, text/xml");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Cookie", ""); connection.setRequestProperty("Authorization", "");
                boolean owned = false;
                try {
                    int status = connection.getResponseCode();
                    if (status == 200) {
                        if (connection.getContentLengthLong() > FeedParser.MAX_BYTES) throw new IOException("Feed exceeds supported size.");
                        String encoding = connection.getContentEncoding();
                        if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new IOException("Compressed feed responses are unsupported.");
                        final InputStream stream = connection.getInputStream();
                        owned = true;
                        return new FilterInputStream(stream) {
                            private void check() throws IOException {
                                try { cancellation.check(); } catch (InterruptedException cancelled) { throw new IOException("Feed cancelled.", cancelled); }
                                if (System.nanoTime() > deadline) throw new IOException("Feed request timed out.");
                            }
                            @Override public int read(byte[] bytes, int offset, int length) throws IOException { check(); return super.read(bytes, offset, length); }
                            @Override public int read() throws IOException { check(); return super.read(); }
                            @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                        };
                    }
                    if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) throw new IOException("Feed unavailable.");
                    String next = connection.getHeaderField("Location");
                    if (next == null || redirect == 3) throw new IOException("Unsupported feed redirect.");
                    url = new URL(url, next);
                } finally { if (!owned) connection.disconnect(); }
            }
            throw new IOException("Feed request could not complete.");
        }
    }
}
