package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** Plain titles and HTTPS links only. No remote HTML, images, scripts, enclosures or embedded players. */
public final class FeedParser {
    public static final int MAX_BYTES = 128 * 1024;
    public static final int MAX_ITEMS = 30;
    public static final class Cancellation {
        private volatile boolean cancelled;
        public void cancel() { cancelled = true; }
        public void check() throws InterruptedException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException("Feed request cancelled.");
        }
    }
    public static final class Item {
        public final String id, title, url;
        Item(String id, String title, String url) { this.id = id; this.title = title; this.url = url; }
    }
    private FeedParser() {}
    public static List<Item> read(FeedSource.Format format, InputStream input, Cancellation cancellation) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            cancellation.check();
            if (bytes.size() + count > MAX_BYTES) throw new IOException("The feed exceeds the supported size.");
            bytes.write(buffer, 0, count);
        }
        cancellation.check();
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        if (text.startsWith("\ufeff")) text = text.substring(1);
        List<Item> result = format == FeedSource.Format.JSON_FEED ? json(text) : rss(text, cancellation);
        cancellation.check();
        return Collections.unmodifiableList(result);
    }
    private static List<Item> json(String text) {
        Map<String, Object> feed = LegacyJson.object(text);
        Object version = feed.get("version");
        if (!("https://jsonfeed.org/version/1".equals(version) || "https://jsonfeed.org/version/1.1".equals(version))
                || !(feed.get("title") instanceof String) || !(feed.get("items") instanceof List)) throw new IllegalArgumentException("Unsupported JSON Feed document.");
        List<?> items = (List<?>) feed.get("items");
        if (items.size() > 1000) throw new IllegalArgumentException("Too many feed items.");
        List<Item> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Object raw : items) {
            if (!(raw instanceof Map)) throw new IllegalArgumentException("Invalid JSON Feed item.");
            Map<?, ?> entry = (Map<?, ?>) raw;
            if (!(entry.get("id") instanceof String)) throw new IllegalArgumentException("JSON Feed items need string identities.");
            add(result, seen, (String) entry.get("id"), entry.get("title"), entry.get("url"));
        }
        return result;
    }
    private static List<Item> rss(String text, final Cancellation cancellation) throws Exception {
        // Decode as UTF-8 first and parse a character source, so alternate byte encodings cannot hide a declaration.
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) throw new IllegalArgumentException("DTD and entity declarations are unsupported.");
        final List<Item> result = new ArrayList<>(); final Set<String> seen = new HashSet<>();
        SAXParserFactory factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(true); factory.setValidating(false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setEntityResolver(new org.xml.sax.EntityResolver() {
            @Override public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                throw new SAXException("External entities are unsupported.");
            }
        });
        final boolean[] channelSeen = {false};
        DefaultHandler handler = new DefaultHandler() {
            int depth, nodes, itemDepth, captureDepth; boolean root, channel;
            String field, title, link, id; final StringBuilder value = new StringBuilder();
            @Override public void startElement(String uri, String local, String name, Attributes attributes) throws SAXException {
                checkCancellation();
                if (++depth > 32 || ++nodes > 20000) throw new SAXException("Feed structure exceeds supported limits.");
                if (depth == 1) {
                    root = "rss".equals(local) && "2.0".equals(attributes.getValue("version")) && uri.isEmpty();
                    if (!root) throw new SAXException("Only RSS 2.0 is supported.");
                }
                if (depth == 2 && "channel".equals(local) && uri.isEmpty()) {
                    if (channelSeen[0]) throw new SAXException("Only one RSS channel is supported.");
                    channel = true; channelSeen[0] = true;
                }
                if (depth == 3 && channel && "item".equals(local) && uri.isEmpty()) { itemDepth = depth; title = link = id = null; }
                if (itemDepth > 0 && depth == itemDepth + 1 && uri.isEmpty()
                        && ("title".equals(local) || "link".equals(local) || "guid".equals(local))) {
                    field = local; captureDepth = depth; value.setLength(0);
                }
            }
            @Override public void characters(char[] chars, int start, int length) throws SAXException {
                checkCancellation();
                if (field != null) {
                    if (value.length() + length > 4096) throw new SAXException("Feed text exceeds supported limits.");
                    value.append(chars, start, length);
                }
            }
            @Override public void endElement(String uri, String local, String name) throws SAXException {
                checkCancellation();
                if (field != null && depth == captureDepth) {
                    if (field.equals("title")) title = value.toString();
                    else if (field.equals("link")) link = value.toString(); else id = value.toString();
                    field = null;
                }
                if (itemDepth > 0 && depth == itemDepth) { add(result, seen, id == null ? link : id, title, link); itemDepth = 0; }
                if (depth == 2 && "channel".equals(local)) channel = false;
                depth--;
            }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
            private void checkCancellation() throws SAXException {
                try { cancellation.check(); } catch (InterruptedException cancelled) { throw new SAXException(cancelled); }
            }
        };
        reader.setContentHandler(handler); reader.setErrorHandler(handler);
        reader.parse(new InputSource(new StringReader(text)));
        if (!channelSeen[0]) throw new IllegalArgumentException("RSS 2.0 channel is missing.");
        cancellation.check();
        return result;
    }
    private static void add(List<Item> result, Set<String> seen, String id, Object titleValue, Object urlValue) {
        if (!(titleValue instanceof String) || !(urlValue instanceof String) || id == null || id.isEmpty() || id.length() > 2048) return;
        String title = ((String) titleValue).trim().replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ");
        String url = ((String) urlValue).trim();
        if (title.isEmpty() || title.length() > 300) return;
        try { FeedSource.validateUrl(url); } catch (IllegalArgumentException unsafe) { return; }
        if (seen.add(id) && result.size() < MAX_ITEMS) result.add(new Item(id, title, url));
    }
}
