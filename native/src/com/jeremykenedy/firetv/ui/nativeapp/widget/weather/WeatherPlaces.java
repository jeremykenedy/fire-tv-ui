package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** User initiated Census place search. A result is only a choice, not an NWS coverage claim. */
final class WeatherPlaces {
    private static final String BASE = "https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Places_CouSub_ConCity_SubMCD/MapServer/";
    private static final String[] STATES = {"01:AL", "02:AK", "04:AZ", "05:AR", "06:CA", "08:CO", "09:CT", "10:DE", "11:DC", "12:FL", "13:GA", "15:HI", "16:ID", "17:IL", "18:IN", "19:IA", "20:KS", "21:KY", "22:LA", "23:ME", "24:MD", "25:MA", "26:MI", "27:MN", "28:MS", "29:MO", "30:MT", "31:NE", "32:NV", "33:NH", "34:NJ", "35:NM", "36:NY", "37:NC", "38:ND", "39:OH", "40:OK", "41:OR", "42:PA", "44:RI", "45:SC", "46:SD", "47:TN", "48:TX", "49:UT", "50:VT", "51:VA", "53:WA", "54:WV", "55:WI", "56:WY", "60:AS", "66:GU", "69:MP", "72:PR", "78:VI"};
    static final class Place {
        final String label, latitude, longitude;
        Place(String label, String latitude, String longitude) { this.label = label; this.latitude = latitude; this.longitude = longitude; }
        String description() { return label + " (" + latitude + ", " + longitude + ")"; }
    }
    static String url(String query, int layer) throws Exception {
        if (query == null || query.length() < 2 || query.length() > 64 || !query.equals(query.trim())
                || (layer != 4 && layer != 5)) throw new IllegalArgumentException("Enter a city or town name.");
        String[] parts = query.split(",", -1);
        if (parts.length > 2 || !parts[0].trim().matches("[\\p{L}\\p{M} .'-]{2,60}"))
            throw new IllegalArgumentException("Enter a city name and optional two-letter state.");
        String name = parts[0].trim().toUpperCase(Locale.ROOT).replace("'", "''");
        String where = "UPPER(BASENAME) = '" + name + "'";
        if (parts.length == 2) {
            String code = stateCode(parts[1].trim().toUpperCase(Locale.ROOT));
            if (code.isEmpty()) throw new IllegalArgumentException("Enter a valid two-letter state.");
            where += " AND STATE = '" + code + "'";
        }
        return BASE + layer + "/query?where=" + URLEncoder.encode(where, "UTF-8")
                + "&outFields=NAME%2CSTATE%2CCENTLAT%2CCENTLON&returnGeometry=false&resultRecordCount=40&f=json";
    }
    static List<Place> search(String query, WeatherHttp http, WeatherRequest request) throws Exception {
        List<Place> places = new ArrayList<>();
        for (int layer : new int[]{4, 5}) {
            WeatherHttp.Response response = http.get(url(query, layer), null, request);
            if (response.code != 200) throw new IOException("Place search is unavailable (HTTP " + response.code + ").");
            parse(response.body, places);
        }
        return places;
    }
    static void parse(String body, List<Place> places) throws Exception {
        JSONObject result = new JSONObject(body);
        if (result.has("error") || result.optBoolean("exceededTransferLimit", false)) throw new IOException("Place search was incomplete. Try a more specific name.");
        JSONArray features = result.getJSONArray("features");
        if (features.length() > 40) throw new IOException("Too many place results.");
        for (int i = 0; i < features.length(); i++) {
            JSONObject value = features.getJSONObject(i).getJSONObject("attributes");
            String name = value.getString("NAME"), state = state(value.getString("STATE"));
            if (state.isEmpty() || name.length() == 0 || name.length() > 70 || name.matches("(?s).*\\p{Cntrl}.*")) continue;
            double lat = value.getDouble("CENTLAT"), lon = value.getDouble("CENTLON");
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
            String label = name + ", " + state;
            String latitude = String.format(Locale.ROOT, "%.7f", lat), longitude = String.format(Locale.ROOT, "%.7f", lon);
            WeatherOptions.location(label, latitude, longitude, false, false);
            places.add(new Place(label, latitude, longitude));
        }
    }
    private static String stateCode(String abbreviation) {
        for (String entry : STATES) if (entry.substring(3).equals(abbreviation)) return entry.substring(0, 2);
        return "";
    }
    private static String state(String code) {
        for (String entry : STATES) if (entry.startsWith(code + ":")) return entry.substring(3);
        return "";
    }
}
