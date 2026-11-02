package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import java.util.ArrayList;
import java.util.List;

public final class WeatherPlacesTest {
    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unsafe search accepted"); }
        catch (IllegalArgumentException | java.io.IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        String url = WeatherPlaces.url("O'Brien", 4);
        check(url.startsWith("https://tigerweb.geo.census.gov/") && url.contains("O%27%27BRIEN"), "Literal names are escaped and fixed to Census");
        check(WeatherPlaces.url("Portland, OR", 4).contains("STATE+%3D+%2741%27"), "A valid state narrows an ambiguous city");
        rejects(() -> WeatherPlaces.url("Portland, ZZ", 4));
        rejects(() -> WeatherPlaces.url("Portland%", 4));
        rejects(() -> WeatherPlaces.url("X", 4));
        rejects(() -> WeatherPlaces.url("Portland", 6));
        List<WeatherPlaces.Place> places = new ArrayList<>();
        WeatherPlaces.parse("{\"features\":[{\"attributes\":{\"NAME\":\"Portland city\",\"STATE\":\"41\",\"CENTLAT\":\"+45.5371363\",\"CENTLON\":\"-122.6500207\"}},{\"attributes\":{\"NAME\":\"Portland city\",\"STATE\":\"23\",\"CENTLAT\":\"+43.6471006\",\"CENTLON\":\"-070.1681549\"}}]}", places);
        check(places.size() == 2 && places.get(0).label.equals("Portland city, OR") && places.get(1).label.equals("Portland city, ME"), "Ambiguous cities remain separate named choices");
        check(places.get(0).latitude.equals("45.5371363") && places.get(0).longitude.equals("-122.6500207"), "Census coordinates stay attached to the selected place");
        WeatherOptions selected = WeatherOptions.location(places.get(0).label, places.get(0).latitude, places.get(0).longitude, true, false);
        check(selected.metric && selected.latitude().equals("45.5371"), "Selection can preserve current units and supported coordinate precision");
        List<WeatherPlaces.Place> invalid = new ArrayList<>();
        WeatherPlaces.parse("{\"features\":[{\"attributes\":{\"NAME\":\"Example\",\"STATE\":\"99\",\"CENTLAT\":\"1\",\"CENTLON\":\"1\"}},{\"attributes\":{\"NAME\":\"Example\",\"STATE\":\"41\",\"CENTLAT\":\"100\",\"CENTLON\":\"1\"}}]}", invalid);
        check(invalid.isEmpty(), "Unknown states and invalid coordinates cannot become choices");
        rejects(() -> WeatherPlaces.parse("{\"error\":{\"message\":\"down\"}}", new ArrayList<>()));
        rejects(() -> WeatherPlaces.parse("{\"features\":[],\"exceededTransferLimit\":true}", new ArrayList<>()));
        System.out.println("Census location selection, ambiguity, invalid results and query boundaries passed.");
    }
}
