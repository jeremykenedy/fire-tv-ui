package com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Original Java evaluation of Schlyter's published orbital formulae. See tests/astronomy/PROVENANCE.md. */
public final class Astronomy {
    private static final long DAY = 86400000L;
    private static final long EPOCH = 946598400000L; // 1999-12-31 00:00 UTC, Schlyter day zero.
    public enum SunState { RISE_AND_SET, ABOVE_HORIZON, BELOW_HORIZON, TRANSITION }

    public static final class SunDay {
        public final Long sunrise;
        public final Long sunset;
        public final SunState state;
        public final long start;
        public final long end;
        private SunDay(Long sunrise, Long sunset, SunState state, long start, long end) {
            this.sunrise = sunrise; this.sunset = sunset; this.state = state; this.start = start; this.end = end;
        }
    }

    public static final class Moon {
        public final double illumination;
        public final boolean waxing;
        public final double longitude;
        public final double latitude;
        private Moon(double illumination, boolean waxing, double longitude, double latitude) {
            this.illumination = illumination; this.waxing = waxing; this.longitude = longitude; this.latitude = latitude;
        }
        public String phase() {
            if (illumination < .01) return "Near new moon";
            if (illumination > .99) return "Near full moon";
            if (illumination >= .48 && illumination <= .52) return waxing ? "Near first quarter" : "Near last quarter";
            if (waxing) return illumination < .5 ? "Waxing crescent" : "Waxing gibbous";
            return illumination < .5 ? "Waning crescent" : "Waning gibbous";
        }
    }

    private Astronomy() {}
    private static double sin(double degrees) { return Math.sin(Math.toRadians(degrees)); }
    private static double cos(double degrees) { return Math.cos(Math.toRadians(degrees)); }
    private static double atan(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double wrap(double degrees) { return degrees - 360 * Math.floor(degrees / 360); }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    private static double days(long now) { return (now - EPOCH) / (double) DAY; }

    public static void validateDate(long now) {
        if (now < -2177452800000L || now >= 4102444800000L)
            throw new IllegalArgumentException("Calculations support dates from 1901 through 2099.");
    }

    private static double eccentric(double mean, double eccentricity) {
        double m = Math.toRadians(wrap(mean));
        double e = m;
        for (int i = 0; i < 8; i++) e -= (e - eccentricity * Math.sin(e) - m) / (1 - eccentricity * Math.cos(e));
        return Math.toDegrees(e);
    }

    public static double sunLongitude(long now) {
        validateDate(now);
        return solarLongitude(now);
    }

    private static double solarLongitude(long now) {
        double d = days(now), e = .016709 - 1.151E-9 * d;
        double anomaly = eccentric(356.0470 + .9856002585 * d, e);
        return wrap(atan(Math.sqrt(1 - e * e) * sin(anomaly), cos(anomaly) - e) + 282.9404 + 4.70935E-5 * d);
    }

    private static double sunAltitude(long now, double latitude, double longitude) {
        double d = days(now);
        double lon = solarLongitude(now), obliquity = 23.4393 - 3.563E-7 * d;
        double ra = atan(sin(lon) * cos(obliquity), cos(lon));
        double dec = Math.toDegrees(Math.asin(sin(lon) * sin(obliquity)));
        double utcHours = Math.floorMod(now, DAY) / 3600000.0;
        double sidereal = 356.0470 + .9856002585 * d + 282.9404 + 4.70935E-5 * d + 180 + 15 * utcHours;
        return Math.toDegrees(Math.asin(clamp(sin(latitude) * sin(dec)
                + cos(latitude) * cos(dec) * cos(sidereal + longitude - ra))));
    }

    public static SunDay sunDay(long now, double latitude, double longitude, TimeZone zone) {
        validateDate(now);
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90 || !Double.isFinite(longitude) || Math.abs(longitude) > 180)
            throw new IllegalArgumentException("Enter valid latitude and longitude.");
        Calendar date = new GregorianCalendar(zone);
        date.setTimeInMillis(now);
        date.set(Calendar.HOUR_OF_DAY, 0); date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0);
        long start = date.getTimeInMillis();
        date.add(Calendar.DAY_OF_MONTH, 1);
        long end = date.getTimeInMillis();
        Long rise = null, set = null;
        double before = sunAltitude(start, latitude, longitude) + .833;
        double min = before, max = before;
        for (long left = start; left < end;) {
            long right = Math.min(end, left + 300000L);
            double after = sunAltitude(right, latitude, longitude) + .833;
            min = Math.min(min, after); max = Math.max(max, after);
            if ((before < 0 && after >= 0) || (before >= 0 && after < 0)) {
                long low = left, high = right;
                boolean rising = after > before;
                while (high - low > 1000) {
                    long middle = low + (high - low) / 2;
                    boolean above = sunAltitude(middle, latitude, longitude) + .833 >= 0;
                    if (above == rising) high = middle; else low = middle;
                }
                long event = low + (high - low) / 2;
                if (event < end) { if (rising) rise = event; else set = event; }
            }
            left = right; before = after;
        }
        SunState state = rise != null && set != null ? SunState.RISE_AND_SET
                : rise != null || set != null ? SunState.TRANSITION
                : min > .1 ? SunState.ABOVE_HORIZON : max < -.1 ? SunState.BELOW_HORIZON : SunState.TRANSITION;
        return new SunDay(rise, set, state, start, end);
    }

    public static Moon moon(long now) {
        validateDate(now);
        double d = days(now);
        double node = wrap(125.1228 - .0529538083 * d), inclination = 5.1454;
        double perihelion = wrap(318.0634 + .1643573223 * d);
        double anomaly = wrap(115.3654 + 13.0649929509 * d);
        double e = eccentric(anomaly, .054900);
        double v = atan(Math.sqrt(1 - .054900 * .054900) * sin(e), cos(e) - .054900);
        double argument = v + perihelion;
        double x = cos(node) * cos(argument) - sin(node) * sin(argument) * cos(inclination);
        double y = sin(node) * cos(argument) + cos(node) * sin(argument) * cos(inclination);
        double z = sin(argument) * sin(inclination);
        double lon = atan(y, x), lat = atan(z, Math.sqrt(x * x + y * y));
        double solarMean = wrap(356.0470 + .9856002585 * d);
        double solarPerihelion = wrap(282.9404 + 4.70935E-5 * d);
        double lunarMeanLongitude = anomaly + perihelion + node;
        double elongation = lunarMeanLongitude - solarMean - solarPerihelion;
        double f = lunarMeanLongitude - node;
        lon += -1.274 * sin(anomaly - 2 * elongation) + .658 * sin(2 * elongation) - .186 * sin(solarMean)
                - .059 * sin(2 * anomaly - 2 * elongation) - .057 * sin(anomaly - 2 * elongation + solarMean)
                + .053 * sin(anomaly + 2 * elongation) + .046 * sin(2 * elongation - solarMean)
                + .041 * sin(anomaly - solarMean) - .035 * sin(elongation) - .031 * sin(anomaly + solarMean)
                - .015 * sin(2 * f - 2 * elongation) + .011 * sin(anomaly - 4 * elongation);
        lat += -.173 * sin(f - 2 * elongation) - .055 * sin(anomaly - f - 2 * elongation)
                - .046 * sin(anomaly + f - 2 * elongation) + .033 * sin(f + 2 * elongation) + .017 * sin(2 * anomaly + f);
        double separationCos = clamp(cos(sunLongitude(now) - lon) * cos(lat));
        return new Moon((1 - separationCos) / 2, wrap(lon - sunLongitude(now)) < 180, wrap(lon), lat);
    }
}
