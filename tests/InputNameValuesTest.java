package com.jeremykenedy.firetv.ui;

import org.json.JSONObject;

public final class InputNameValuesTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String first = "tvinput_name:HDMI 1", second = "tvinput_name:HDMI 2";
        String names = InputNameValues.change("{}", first, "  Sony Receiver  ");
        names = InputNameValues.change(names, second, "Game console");
        check(InputNameValues.display(names, first, "HDMI 1").equals("Sony Receiver"), "custom label");
        check(new JSONObject(names).has(first), "factory identity retained");
        check(InputNameValues.display(names, "app.package", "App").equals("App"), "ordinary apps unchanged");
        names = InputNameValues.change(names, first, null);
        check(InputNameValues.display(names, first, "HDMI 1").equals("HDMI 1"), "factory name restored");
        check(InputNameValues.display(names, second, "HDMI 2").equals("Game console"), "other input retained");
        check(InputNameValues.display("not json", first, "HDMI 1").equals("HDMI 1"), "corrupt preference fallback");
        for (String invalid : new String[]{"", "   ", "two\nlines", new String(new char[61]).replace('\0', 'x')}) {
            boolean rejected = false;
            try { InputNameValues.change(names, first, invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid label rejected");
        }
        for (String invalid : new String[]{"{\"app.package\":\"Name\"}", "{\"tvinput_name:HDMI 1\":3}",
                "{\"tvinput_name:HDMI 1\":\"\"}"}) {
            boolean rejected = false;
            try { InputNameValues.parse(invalid); } catch (org.json.JSONException expected) { rejected = true; }
            check(rejected, "invalid backup rejected");
        }
        check(InputNameValues.display(InputNameValues.change("{}", first, "ゲーム機"), first, "HDMI 1")
                .equals("ゲーム機"), "international name retained");
        System.out.println("Input rename, factory reset, identity, isolation and validation checks passed.");
    }
}
