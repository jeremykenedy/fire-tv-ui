package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.Set;

public final class AstronomyLocation {
    private static final Set<String> ZONES = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
    public final double latitude;
    public final double longitude;
    public final String zone;
    public final String label;

    public AstronomyLocation(double latitude, double longitude, String zone, String label) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90 || !Double.isFinite(longitude) || Math.abs(longitude) > 180)
            throw new IllegalArgumentException("Latitude must be -90 to 90; longitude must be -180 to 180.");
        if (zone == null || !ZONES.contains(zone)
                || (!zone.contains("/") && !zone.equals("UTC")))
            throw new IllegalArgumentException("Choose an IANA time zone, for example America/Los_Angeles.");
        if (label == null || label.trim().isEmpty() || label.trim().length() > 40)
            throw new IllegalArgumentException("Enter a location label of 1 to 40 characters.");
        for (int i = 0; i < label.length(); i++) if (Character.isISOControl(label.charAt(i)))
            throw new IllegalArgumentException("Location labels cannot contain control characters.");
        this.latitude = latitude; this.longitude = longitude; this.zone = zone; this.label = label.trim();
    }

    public String encode() { return "v1\t" + latitude + "\t" + longitude + "\t" + zone + "\t" + label; }
    public static AstronomyLocation decode(String value) {
        if (value == null || value.length() > 256) throw new IllegalArgumentException("Invalid saved location.");
        String[] fields = value.split("\t", -1);
        if (fields.length != 5 || !fields[0].equals("v1")) throw new IllegalArgumentException("Invalid saved location.");
        return new AstronomyLocation(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]), fields[3], fields[4]);
    }
}
