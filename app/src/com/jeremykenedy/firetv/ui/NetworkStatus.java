package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NetworkStatus {
    final String details;
    final boolean ethernet;

    private NetworkStatus(String details, boolean ethernet) {
        this.details = details;
        this.ethernet = ethernet;
    }

    static NetworkStatus read(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return new NetworkStatus("Network details are unavailable on this TV.", false);
        try {
            Network active = manager.getActiveNetwork();
            NetworkCapabilities activeCapabilities = active == null ? null : manager.getNetworkCapabilities(active);
            boolean vpn = activeCapabilities != null
                    && activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            List<String> connections = new ArrayList<>();
            boolean ethernet = false;
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                NetworkInfo info = manager.getNetworkInfo(network);
                if (capabilities == null || info == null || !info.isConnected()
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                boolean wired = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                boolean wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                if (!wired && !wifi) continue;
                LinkProperties properties = manager.getLinkProperties(network);
                if (properties == null) continue;
                ethernet |= wired;
                String title = wired ? "Ethernet" : "Wi-Fi";
                title += network.equals(active) ? " (current connection)" : " (connected)";
                Set<String> addresses = new LinkedHashSet<>();
                for (LinkAddress address : properties.getLinkAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip != null && !ip.isLoopbackAddress() && !ip.isAnyLocalAddress()) {
                        addresses.add(ip.getHostAddress() + "/" + address.getPrefixLength());
                    }
                }
                Set<String> gateways = new LinkedHashSet<>();
                for (RouteInfo route : properties.getRoutes()) {
                    InetAddress gateway = route.getGateway();
                    if (route.isDefaultRoute() && gateway != null && !gateway.isAnyLocalAddress()) {
                        gateways.add(gateway.getHostAddress());
                    }
                }
                Set<String> dns = new LinkedHashSet<>();
                for (InetAddress server : properties.getDnsServers()) dns.add(server.getHostAddress());
                connections.add(title + "\nIP address / prefix: " + values(addresses)
                        + "\nGateway: " + values(gateways) + "\nDNS: " + values(dns));
            }
            String details = connections.isEmpty() ? "No connected Wi-Fi or Ethernet network was reported."
                    : String.join("\n\n", connections);
            if (vpn) details = "A VPN is active. Local network details are shown below; these are not the VPN address.\n\n" + details;
            return new NetworkStatus(details, ethernet);
        } catch (SecurityException unavailable) {
            return new NetworkStatus("Android did not allow access to network details. Open TV network settings to view them.", false);
        } catch (RuntimeException unavailable) {
            return new NetworkStatus("Network details could not be read. Refresh or open TV network settings.", false);
        }
    }

    private static String values(Set<String> items) {
        return items.isEmpty() ? "Not reported" : String.join(", ", items);
    }
}
