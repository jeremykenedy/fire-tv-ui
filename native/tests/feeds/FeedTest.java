package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FeedTest {
    private static int assertions, opens;
    private static String redirect;
    private static int response = 200;
    private static long length = -1;
    private static FakeConnection latest;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value) { if (!value) throw new AssertionError(); assertions++; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unsafe feed accepted"); }
        catch (IOException | IllegalArgumentException | org.xml.sax.SAXException | InterruptedException expected) { assertions++; }
    }
    private static List<FeedParser.Item> rss(String value) throws Exception { return parse(FeedSource.Format.RSS_2, value); }
    private static List<FeedParser.Item> parse(FeedSource.Format format, String value) throws Exception {
        return FeedParser.read(format, new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), new FeedParser.Cancellation());
    }
    private static final String RSS = "<rss version='2.0'><channel><title>Fixture source</title><item><guid>one</guid><title>A &amp; B</title><link>https://example.org/story</link></item></channel></rss>";
    private static final String JSON = "{\"version\":\"https://jsonfeed.org/version/1.1\",\"title\":\"Fixture\",\"items\":[{\"id\":\"1\",\"title\":\"Story\",\"url\":\"https://example.org/story\"}]}";
    private static final class FakeConnection extends HttpURLConnection {
        boolean disconnected;
        FakeConnection(URL url) { super(url); }
        @Override public void connect() {}
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return response; }
        @Override public String getHeaderField(String key) { return key.equals("Location") ? redirect : null; }
        @Override public long getContentLengthLong() { return length; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(RSS.getBytes(StandardCharsets.UTF_8)); }
    }
    public static void main(String[] args) throws Exception {
        FeedSource source = new FeedSource("Fixture", "https://example.org/feed?private=fixture", FeedSource.Format.RSS_2);
        check(source.display().equals("Fixture - example.org"));
        check(!source.display().contains("private"));
        for (String bad : new String[]{"http://example.org/feed", "file:///tmp/feed", "https://u:p@example.org/feed",
                "https://example.org:8443/feed", "https://example.org/feed#fragment", "intent://example.org", "javascript:alert(1)",
                "https://example.org\\@evil.test/feed", "https://example.org/\nfeed"}) rejects(() -> new FeedSource("Bad", bad, FeedSource.Format.RSS_2));
        check(rss(RSS).size() == 1 && rss(RSS).get(0).title.equals("A & B"));
        check(parse(FeedSource.Format.JSON_FEED, JSON).size() == 1);
        check(rss("<rss version='2.0'><channel/></rss>").isEmpty());
        check(parse(FeedSource.Format.JSON_FEED, JSON.replace("https://example.org/story", "javascript:alert(1)")).isEmpty());
        check(rss(RSS.replace("https://example.org/story", "file:///tmp/private")).isEmpty());
        rejects(() -> rss("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///private-fixture'>]><rss version='2.0'><channel>&x;</channel></rss>"));
        rejects(() -> rss("<!DOCTYPE rss SYSTEM 'https://external.test/evil.dtd'><rss version='2.0'><channel/></rss>"));
        rejects(() -> rss("<feed xmlns='http://www.w3.org/2005/Atom'><title>Unsupported Atom</title></feed>"));
        rejects(() -> rss("<rss version='2.0'>"));
        rejects(() -> rss("<rss version='2.0'/ >"));
        rejects(() -> rss("<rss version='2.0'><x/></rss>"));
        rejects(() -> rss("<rss version='2.0'><channel>" + "<x>".repeat(35) + "</x>".repeat(35) + "</channel></rss>"));
        rejects(() -> rss(" ".repeat(FeedParser.MAX_BYTES + 1)));
        rejects(() -> parse(FeedSource.Format.JSON_FEED, "{\"items\":[]}"));
        rejects(() -> parse(FeedSource.Format.JSON_FEED, JSON.replace("/version/1.1", "/version/2")));
        rejects(() -> parse(FeedSource.Format.JSON_FEED, JSON.replace("\"id\":\"1\"", "\"id\":123")));
        rejects(() -> parse(FeedSource.Format.JSON_FEED, JSON.replace("\"id\":\"1\"", "\"id\":\"1\",\"id\":\"2\"")));
        rejects(() -> FeedParser.read(FeedSource.Format.RSS_2, new ByteArrayInputStream(new byte[]{(byte) 0xff}), new FeedParser.Cancellation()));
        StringBuilder many = new StringBuilder("<rss version='2.0'><channel>");
        for (int i = 0; i < 100; i++) many.append("<item><guid>").append(i).append("</guid><title>Headline</title><link>https://example.org/story</link></item>");
        many.append("</channel></rss>"); check(rss(many.toString()).size() == FeedParser.MAX_ITEMS);
        FeedParser.Cancellation cancelled = new FeedParser.Cancellation(); cancelled.cancel();
        rejects(() -> FeedParser.read(FeedSource.Format.RSS_2, new ByteArrayInputStream(RSS.getBytes(StandardCharsets.UTF_8)), cancelled));
        FeedSnapshot fresh = FeedSnapshot.empty().success(rss(RSS), 100);
        check(!fresh.stale(101)); check(fresh.failure().stale(101));
        check(fresh.failure().items.size() == 1); check(fresh.stale(FeedSnapshot.STALE_MS + 100));
        check(fresh.failure().message(101).contains("Stale"));
        check(FeedSnapshot.empty().failure().message(100).contains("unavailable"));
        java.util.Set<String> hidden = new java.util.HashSet<>();
        hidden.add(FeedSnapshot.hiddenKey(fresh.items.get(0).id));
        check(fresh.visible(hidden).items.isEmpty());
        check(fresh.failure().visible(hidden).failed && fresh.failure().visible(hidden).fetchedAt == 100);
        check(fresh.items.size() == 1);
        hidden.clear(); check(fresh.visible(hidden).items.size() == 1);
        hidden.add(FeedSnapshot.hiddenKey("other-source-item")); check(fresh.visible(hidden).items.size() == 1);
        final boolean[] closed = {false};
        FeedClient fixture = new FeedClient((s,c) -> new ByteArrayInputStream(RSS.getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        });
        check(fixture.fetch(source, new FeedParser.Cancellation()).size() == 1 && closed[0]);
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) { opens++; return latest = new FakeConnection(url); }
        } : null);
        FeedClient network = new FeedClient(new FeedClient.Https());
        check(network.fetch(source, new FeedParser.Cancellation()).size() == 1);
        check(latest.disconnected && !latest.getInstanceFollowRedirects());
        check(latest.getConnectTimeout() == 8000 && latest.getReadTimeout() == 8000);
        check("".equals(latest.getRequestProperty("Cookie")) && "".equals(latest.getRequestProperty("Authorization")));
        response = 302; redirect = "https://other.test/private"; opens = 0;
        rejects(() -> network.fetch(source, new FeedParser.Cancellation())); check(opens == 1 && latest.disconnected);
        redirect = "http://example.org/downgrade"; rejects(() -> network.fetch(source, new FeedParser.Cancellation()));
        redirect = "/loop"; opens = 0; rejects(() -> network.fetch(source, new FeedParser.Cancellation())); check(opens == 4);
        response = 503; rejects(() -> network.fetch(source, new FeedParser.Cancellation()));
        response = 200; length = FeedParser.MAX_BYTES + 1;
        rejects(() -> network.fetch(source, new FeedParser.Cancellation())); check(latest.disconnected);
        System.out.println("Selected feed: " + assertions + " assertions passed");
    }
}
