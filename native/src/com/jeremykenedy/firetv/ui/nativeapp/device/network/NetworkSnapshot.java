package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Android-reported connection data only; never probes a server or changes addressing. */
public final class NetworkSnapshot {
    private NetworkSnapshot() {}

    public static String read(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return "Network details are unavailable on this device.";
        try {
            Network active = manager.getActiveNetwork();
            StringBuilder result = new StringBuilder(active == null
                    ? "No active default connection reported.\n" : "Default connection is reported by Android.\n");
            int count = 0;
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps == null) continue;
                boolean vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                List<String> transports = new ArrayList<>();
                if (vpn) transports.add("VPN");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("Ethernet");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("Wi-Fi");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("Cellular");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("Bluetooth");
                result.append("\n").append(transports.isEmpty() ? "Other network" : String.join(" / ", transports));
                result.append(network.equals(active) ? " (default)" : " (available)");
                result.append(vpn ? "\nVPN addresses below are tunnel addresses, not LAN addresses." : "\nLocal/physical connection; not a VPN tunnel.");
                result.append("\nInternet: ").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ? "validated by Android" : "not validated by Android");
                LinkProperties link = manager.getLinkProperties(network);
                if (link == null) {
                    result.append("\nAddress details unavailable.\n");
                } else {
                    List<String> addresses = new ArrayList<>(), gateways = new ArrayList<>(), dns = new ArrayList<>();
                    for (LinkAddress address : link.getLinkAddresses()) {
                        InetAddress ip = address.getAddress();
                        if (!ip.isLoopbackAddress() && !ip.isAnyLocalAddress())
                            addresses.add(ip.getHostAddress() + "/" + address.getPrefixLength());
                    }
                    for (RouteInfo route : link.getRoutes()) {
                        InetAddress gateway = route.getGateway();
                        if (route.isDefaultRoute() && gateway != null && !gateway.isAnyLocalAddress())
                            gateways.add(gateway.getHostAddress());
                    }
                    for (InetAddress server : link.getDnsServers()) dns.add(server.getHostAddress());
                    result.append("\nIP / prefix: ").append(values(addresses));
                    result.append("\nGateway: ").append(values(gateways));
                    result.append("\nDNS: ").append(values(dns)).append('\n');
                }
                count++;
            }
            if (count == 0) result.append("\nNo visible connected networks. Offline or unavailable details; open TV settings to check.");
            return result.toString();
        } catch (SecurityException denied) {
            return "Network details unavailable: Android denied access. Open TV network settings to inspect the connection.";
        } catch (RuntimeException unavailable) {
            return "Network details could not be read. Refresh or open TV network settings.";
        }
    }

    private static String values(List<String> values) {
        return values.isEmpty() ? "Not reported" : String.join(", ", values);
    }
}
