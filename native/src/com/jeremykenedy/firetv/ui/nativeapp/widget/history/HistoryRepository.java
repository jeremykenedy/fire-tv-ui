package com.jeremykenedy.firetv.ui.nativeapp.widget.history;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One dated, bounded Wikipedia text extract; no content is bundled or invented. */
final class HistoryRepository {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final long MAX_FRESH_MS = 3600000L;
    private static final Pattern EVENT = Pattern.compile("^([0-9]{1,4})[ ]*[–—-][ ]+(.{1,800})$");
    private static final Pattern SECTION = Pattern.compile("^==[^=].*[^=]==$");
    static final String LICENSE = "https://creativecommons.org/licenses/by-sa/4.0/";

    interface Clock {
        long now();
    }

    interface Transport {
        Response get(String url) throws IOException;
    }

    static final class Response {
        final int status;
        final String body, cacheControl, age, retryAfter;

        Response(int status, String body, String cacheControl, String age, String retryAfter) {
            this.status = status;
            this.body = body;
            this.cacheControl = cacheControl;
            this.age = age;
            this.retryAfter = retryAfter;
        }
    }

    static final class Event {
        final LocalDate date;
        final String line, page;

        Event(LocalDate date, String line, String page) {
            this.date = date;
            this.line = line;
            this.page = page;
        }
    }

    static final HistoryRepository SHARED =
            new HistoryRepository(
                    new Http(),
                    new Clock() {
                        public long now() {
                            return System.currentTimeMillis();
                        }
                    });
    private final Transport transport;
    private final Clock clock;
    private volatile Event cached;
    private volatile long savedAt, freshUntil;
    private long retryAt;

    HistoryRepository(Transport transport, Clock clock) {
        this.transport = transport;
        this.clock = clock;
    }

    synchronized Event get(LocalDate date) throws IOException {
        if (date == null) throw new IllegalArgumentException("Date is required.");
        long now = clock.now();
        if (cached != null && cached.date.equals(date) && now >= savedAt && now < freshUntil)
            return cached;
        if (now >= 0 && now < retryAt)
            throw new IOException("History is temporarily unavailable. Try again later.");
        try {
            Response response = transport.get(apiUrl(date));
            if (response.status != 200) {
                retryAt = retryDeadline(response.retryAfter, now);
                throw new IOException("History is temporarily unavailable. Try again later.");
            }
            Event result = parse(date, response.body);
            long fresh = cacheMillis(response.cacheControl, response.age);
            cached = null;
            savedAt = now;
            freshUntil = now + fresh;
            cached = fresh > 0 ? result : null;
            retryAt = 0;
            return result;
        } catch (IOException failure) {
            if (retryAt <= now) retryAt = now + 60000L;
            throw failure;
        }
    }

    boolean hasFresh(LocalDate date) {
        Event item = cached;
        long now = clock.now();
        return item != null && item.date.equals(date) && now >= savedAt && now < freshUntil;
    }

    static String pageTitle(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.US));
    }

    static String sourceUrl(LocalDate date) {
        return "https://en.wikipedia.org/wiki/" + pageTitle(date).replace(' ', '_');
    }

    static String apiUrl(LocalDate date) throws IOException {
        return "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&format=json&formatversion=2&maxage=3600&smaxage=3600&titles="
                + URLEncoder.encode(pageTitle(date), "UTF-8");
    }

    static Event parse(LocalDate date, String json) throws IOException {
        if (json == null || json.length() > MAX_BYTES)
            throw new IOException("History response is unavailable.");
        try {
            JSONObject object = new JSONObject(json);
            if (object.has("error")) throw new IOException("History source returned an error.");
            JSONArray pages = object.getJSONObject("query").getJSONArray("pages");
            if (pages.length() != 1) throw new IOException("History page is unavailable.");
            JSONObject page = pages.getJSONObject(0);
            if (page.has("missing") || !pageTitle(date).equals(page.getString("title")))
                throw new IOException("History page does not match the TV date.");
            String extract = page.getString("extract");
            boolean events = false, found = false, closed = false;
            List<String> entries = new ArrayList<>();
            for (String raw : extract.split("\\n", -1)) {
                String line = raw.trim();
                if (!events) {
                    if (line.equals("== Events ==")) {
                        events = true;
                        found = true;
                    }
                    continue;
                }
                if (SECTION.matcher(line).matches()) {
                    closed = true;
                    break;
                }
                Matcher match = EVENT.matcher(line);
                if (!match.matches()) continue;
                int year = Integer.parseInt(match.group(1));
                if (year < 1 || year > date.getYear()) continue;
                boolean safe = true;
                for (int i = 0; i < line.length(); i++)
                    if (Character.isISOControl(line.charAt(i))) {
                        safe = false;
                        break;
                    }
                if (safe) entries.add(line);
            }
            if (!found || !closed || entries.isEmpty())
                throw new IOException("No dated events are available for this day.");
            int index = (int) Math.floorMod(date.toEpochDay(), (long) entries.size());
            return new Event(date, entries.get(index), sourceUrl(date));
        } catch (JSONException | NumberFormatException invalid) {
            throw new IOException("History response could not be read.", invalid);
        }
    }

    static long cacheMillis(String control, String age) {
        if (control == null) return 0;
        long seconds = -1;
        for (String token : control.toLowerCase(Locale.US).split(",")) {
            token = token.trim();
            if (token.equals("no-store")
                    || token.equals("no-cache")
                    || token.startsWith("no-cache=")) return 0;
            if (token.startsWith("max-age=")) {
                try {
                    long parsed = Long.parseLong(token.substring(8).replace("\"", ""));
                    seconds = seconds < 0 ? parsed : Math.min(seconds, parsed);
                } catch (NumberFormatException invalid) {
                    return 0;
                }
            }
        }
        if (seconds < 0) return 0;
        long elapsed = 0;
        try {
            if (age != null) elapsed = Math.max(0, Long.parseLong(age));
        } catch (NumberFormatException invalid) {
            return 0;
        }
        return Math.max(
                0,
                Math.min(
                        MAX_FRESH_MS, (Math.min(seconds, 3600) - Math.min(elapsed, 3600)) * 1000L));
    }

    private static long retryDeadline(String value, long now) {
        long delay = 60000L;
        if (value != null) {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds > Long.MAX_VALUE / 1000L) return Long.MAX_VALUE;
                delay = Math.max(delay, Math.max(0, seconds) * 1000L);
            } catch (NumberFormatException ignored) {
                try {
                    long until =
                            java.time.ZonedDateTime.parse(
                                            value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant()
                                    .toEpochMilli();
                    if (until > now) delay = Math.max(delay, until - now);
                } catch (java.time.DateTimeException invalid) {
                    /* Use the default retry delay. */
                }
            }
        }
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }

    private static final class Http implements Transport {
        @Override
        public Response get(String address) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            try {
                long deadline = System.nanoTime() + 15000000000L;
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty(
                        "User-Agent", "FireTVUI/2.0 (https://github.com/jeremykenedy/fire-tv-ui)");
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                String control = connection.getHeaderField("Cache-Control"),
                        age = connection.getHeaderField("Age"),
                        retry = connection.getHeaderField("Retry-After");
                if (status != 200) return new Response(status, "", control, age, retry);
                if (connection.getContentLength() > MAX_BYTES)
                    throw new IOException("History response is too large.");
                try (InputStream input = connection.getInputStream();
                        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (System.nanoTime() > deadline)
                            throw new IOException("History request timed out.");
                        if (Thread.currentThread().isInterrupted())
                            throw new IOException("History request canceled.");
                        if (output.size() + count > MAX_BYTES)
                            throw new IOException("History response is too large.");
                        output.write(buffer, 0, count);
                    }
                    return new Response(
                            status,
                            new String(output.toByteArray(), StandardCharsets.UTF_8),
                            control,
                            age,
                            retry);
                }
            } finally {
                connection.disconnect();
            }
        }
    }
}
