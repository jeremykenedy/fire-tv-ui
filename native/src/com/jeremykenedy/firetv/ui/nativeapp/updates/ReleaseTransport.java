package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Bounded HTTPS requests; credentials, cookies and arbitrary redirect hosts are never accepted. */
public final class ReleaseTransport implements UpdateCoordinator.Transport {
    @Override public InputStream open(UpdateCoordinator.Release release, final UpdateCoordinator.Cancellation cancellation) throws Exception {
        return open(release.url, release.bytes, cancellation);
    }
    /** Caller must authenticate catalog metadata and its exact reviewed repository/tag URL first. */
    public InputStream open(String reviewedGitHubUrl, long expectedBytes, final UpdateCoordinator.Cancellation cancellation) throws Exception {
        if (expectedBytes <= 0 || expectedBytes > UpdateCoordinator.MAX_BYTES) throw new IOException("Invalid reviewed artifact size.");
        URL location = new URL(reviewedGitHubUrl);
        final long deadline = System.nanoTime() + 120000000000L;
        for (int redirects = 0; redirects <= 5; redirects++) {
            cancellation.check();
            String host = location.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(location.getProtocol()) || location.getUserInfo() != null || location.getPort() != -1
                    || !("github.com".equals(host) || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host))) throw new IOException("The download left GitHub's release servers.");
            if (System.nanoTime() > deadline) throw new IOException("The download timed out. Try again.");
            final HttpURLConnection connection = (HttpURLConnection) location.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Fire-TV-UI-Source");
            boolean streamOwnsConnection = false;
            try {
                int code = connection.getResponseCode();
                if (code == 200) {
                    long length = connection.getContentLengthLong();
                    if (length >= 0 && length != expectedBytes) throw new IOException("The download size differs from the reviewed release.");
                    InputStream source = connection.getInputStream();
                    InputStream bounded = new FilterInputStream(source) {
                        private void check() throws IOException {
                            try { cancellation.check(); }
                            catch (InterruptedException cancelled) { throw new IOException("Update cancelled.", cancelled); }
                            if (System.nanoTime() > deadline) throw new IOException("The download timed out. Try again.");
                        }
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException { check(); return super.read(bytes, offset, length); }
                        @Override public int read() throws IOException { check(); return super.read(); }
                        @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                    };
                    streamOwnsConnection = true;
                    return bounded;
                }
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) throw new IOException("Release server returned HTTP " + code + ". Try again later.");
                String next = connection.getHeaderField("Location");
                if (next == null || redirects == 5) throw new IOException("Too many or invalid release redirects.");
                location = new URL(location, next);
            } finally { if (!streamOwnsConnection) connection.disconnect(); }
        }
        throw new IOException("The download could not start.");
    }
}
