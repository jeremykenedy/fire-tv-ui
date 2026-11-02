package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import java.net.URI;
import java.net.URISyntaxException;

/** One explicitly selected source. URLs stay private; labels never include paths or query strings. */
public final class FeedSource {
    public enum Format { RSS_2, JSON_FEED }
    public final String label, url;
    public final Format format;
    public FeedSource(String label, String url, Format format) {
        if (label == null || label.trim().isEmpty() || label.length() > 40 || format == null) throw new IllegalArgumentException("Enter a source name of 1 to 40 characters.");
        for (int i = 0; i < label.length(); i++) if (Character.isISOControl(label.charAt(i))) throw new IllegalArgumentException("Names cannot contain control characters.");
        validateUrl(url);
        this.label = label.trim(); this.url = url; this.format = format;
    }
    public static URI validateUrl(String value) {
        if (value == null || value.length() > 2048 || value.indexOf('\\') >= 0) throw new IllegalArgumentException("Use an HTTPS source URL of at most 2048 characters.");
        try {
            URI uri = new URI(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException("Use HTTPS without embedded account credentials or a fragment.");
            return uri;
        } catch (URISyntaxException invalid) { throw new IllegalArgumentException("Enter a valid HTTPS source URL."); }
    }
    public String display() { return label + " - " + validateUrl(url).getHost(); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof FeedSource)) return false;
        FeedSource source = (FeedSource) other;
        return label.equals(source.label) && url.equals(source.url) && format == source.format;
    }
    @Override public int hashCode() { return url.hashCode() * 31 + label.hashCode() + format.ordinal(); }
}
