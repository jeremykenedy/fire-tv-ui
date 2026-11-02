package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.content.Context;
import android.net.*;
import java.net.InetAddress;

public final class NetworkSnapshotTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        check(NetworkSnapshot.read(context).contains("unavailable"), "Missing service is explicit");
        ConnectivityManager manager = new ConnectivityManager();
        context.service = manager;
        check(NetworkSnapshot.read(context).contains("No visible connected networks"), "Offline is explicit");
        Network lan = new Network(), vpn = new Network();
        LinkProperties physical = new LinkProperties();
        physical.addresses.add(new LinkAddress("192.0.2.10", 24));
        physical.addresses.add(new LinkAddress("2001:db8::10", 64));
        physical.addresses.add(new LinkAddress("127.0.0.1", 8));
        physical.routes.add(new RouteInfo("192.0.2.1", true));
        physical.routes.add(new RouteInfo("192.0.2.2", false));
        physical.routes.add(new RouteInfo("0.0.0.0", true));
        physical.dns.add(InetAddress.getByName("192.0.2.53"));
        manager.networks.put(lan, new NetworkCapabilities(NetworkCapabilities.TRANSPORT_WIFI, true));
        manager.links.put(lan, physical); manager.active = lan;
        String snapshot = NetworkSnapshot.read(context);
        check(snapshot.contains("Wi-Fi (default)") && snapshot.contains("192.0.2.10/24")
                && snapshot.contains("/64") && snapshot.contains("Gateway: 192.0.2.1")
                && snapshot.contains("DNS: 192.0.2.53") && !snapshot.contains("127.0.0.1")
                && !snapshot.contains("192.0.2.2"), "LAN addresses, gateway and DNS use Android data");
        LinkProperties tunnel = new LinkProperties();
        tunnel.addresses.add(new LinkAddress("10.9.0.1", 32));
        manager.networks.put(vpn, new NetworkCapabilities(NetworkCapabilities.TRANSPORT_VPN, false));
        manager.links.put(vpn, tunnel); manager.active = vpn;
        snapshot = NetworkSnapshot.read(context);
        check(snapshot.contains("Wi-Fi (available)") && snapshot.contains("VPN (default)")
                && snapshot.contains("10.9.0.1/32") && snapshot.contains("tunnel addresses, not LAN addresses"),
                "VPN address is distinguished from the physical connection");
        manager.networks.remove(lan); manager.links.remove(vpn);
        check(NetworkSnapshot.read(context).contains("Address details unavailable"), "Missing details are explicit");
        manager.networks.clear(); manager.active = null;
        check(NetworkSnapshot.read(context).contains("No visible connected networks"), "Disconnected networks are not retained");
        manager.denied = true;
        check(NetworkSnapshot.read(context).contains("Android denied access"), "Permission denial is explicit");
        System.out.println("Native LAN, VPN, offline and denied network snapshot behavior passed.");
    }
}
