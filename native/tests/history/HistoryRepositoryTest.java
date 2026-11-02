package com.jeremykenedy.firetv.ui.nativeapp.widget.history;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;

public final class HistoryRepositoryTest {
    static final class Fake implements HistoryRepository.Clock, HistoryRepository.Transport {
        long time = 1000000;
        int calls;
        HistoryRepository.Response response;

        public long now() {
            return time;
        }

        public HistoryRepository.Response get(String url) {
            calls++;
            return response;
        }
    }

    static String page(String title, String body) {
        return new JSONObject()
                .put(
                        "query",
                        new JSONObject()
                                .put(
                                        "pages",
                                        new JSONArray()
                                                .put(
                                                        new JSONObject()
                                                                .put("title", title)
                                                                .put("extract", body))))
                .toString();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void rejects(LocalDate date, String body) {
        try {
            HistoryRepository.parse(date, body);
            throw new AssertionError("invalid extract accepted");
        } catch (IOException expected) {
        }
    }

    public static void main(String[] args) throws Exception {
        LocalDate date = LocalDate.of(2028, 2, 29);
        String text =
                "== Events ==\n"
                    + "=== Pre-1600 ===\n"
                    + "1200 – Synthetic event.\n"
                    + "== Births ==\n"
                    + "1201 – Not an event.";
        String body = page("February 29", text);
        check(
                HistoryRepository.parse(date, body).line.equals("1200 – Synthetic event."),
                "event section only");
        check(
                HistoryRepository.sourceUrl(date).endsWith("February_29"),
                "English local date source");
        rejects(date, page("March 1", text));
        rejects(date, page("February 29", "== Events ==\n1200 – truncated"));
        rejects(
                date,
                page(
                        "February 29",
                        "== Events ==\nNot a dated event\n== Births ==\n1201 – not an event"));
        check(
                HistoryRepository.cacheMillis("public, max-age=3600", "300") == 3300000,
                "age reduces freshness");
        check(HistoryRepository.cacheMillis("max-age=3600, no-store", "0") == 0, "no-store");
        check(
                HistoryRepository.cacheMillis("max-age=3600, max-age=10", "0") == 10000,
                "conservative duplicate");
        Fake fake = new Fake();
        fake.response = new HistoryRepository.Response(200, body, "max-age=3600", "0", null);
        HistoryRepository repo = new HistoryRepository(fake, fake);
        repo.get(date);
        repo.get(date);
        check(fake.calls == 1, "cache hit");
        fake.response = new HistoryRepository.Response(503, "", null, null, "120");
        try {
            repo.get(date.plusDays(1));
            throw new AssertionError("old event returned");
        } catch (IOException expected) {
        }
        try {
            repo.get(date.plusDays(2));
            throw new AssertionError("retry throttling bypassed");
        } catch (IOException expected) {
        }
        check(fake.calls == 2, "retry delay survives date change");
        fake.time += 120000;
        try {
            repo.get(date.plusDays(1));
        } catch (IOException expected) {
        }
        check(fake.calls == 3, "retry resumes");
        System.out.println("History repository behavior PASS");
    }
}
