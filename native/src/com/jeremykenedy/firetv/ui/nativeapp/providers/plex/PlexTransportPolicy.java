package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import java.net.InetAddress;
import java.net.URI;

/** Cleartext is limited to explicitly entered numeric local addresses, without DNS resolution. */
final class PlexTransportPolicy {
    private PlexTransportPolicy() {}
    static boolean permitsAddress(String address) {
        try {
            URI uri = new URI(address);
            if (uri.getUserInfo() != null || uri.getHost() == null) return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            return "http".equalsIgnoreCase(uri.getScheme()) && localLiteral(uri.getHost());
        } catch (Exception invalid) { return false; }
    }
    static boolean localLiteral(String host) {
        if (host == null) return false;
        String[] octets = host.split("\\.", -1);
        if (octets.length == 4) {
            int[] value = new int[4];
            for (int i = 0; i < 4; i++) {
                if (!octets[i].matches("0|[1-9][0-9]{0,2}")) return false;
                value[i] = Integer.parseInt(octets[i]);
                if (value[i] > 255) return false;
            }
            return value[0] == 10 || value[0] == 127
                    || (value[0] == 172 && value[1] >= 16 && value[1] <= 31)
                    || (value[0] == 192 && value[1] == 168)
                    || (value[0] == 169 && value[1] == 254);
        }
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (!literal.contains(":") || !literal.matches("[0-9A-Fa-f:]+")) return false;
        try {
            // A colon-bearing hex literal cannot initiate hostname lookup or rebind through DNS.
            byte[] bytes = InetAddress.getByName(literal).getAddress();
            if (bytes.length != 16) return false;
            boolean loopback = bytes[15] == 1;
            for (int i = 0; i < 15; i++) loopback &= bytes[i] == 0;
            return loopback || ((bytes[0] & 0xfe) == 0xfc)
                    || ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80);
        } catch (Exception invalid) { return false; }
    }
}
