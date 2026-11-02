package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

public final class StatusReportTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        String physical = "Default connection is reported by Android.\n\nEthernet (default)\nLocal/physical connection; not a VPN tunnel.\nInternet: validated by Android\nIP / prefix: 192.0.2.10/24\nGateway: 192.0.2.1\nDNS: 192.0.2.1\n";
        String vpn = "\n\nVPN (default)\nVPN addresses below are tunnel addresses, not LAN addresses.\nIP / prefix: 198.51.100.2/32\n";
        StatusReport report = StatusReport.network(physical + vpn);
        check(report.availability == StatusReport.Availability.READY, "Physical and VPN results are readable");
        check(report.details.equals(physical + vpn), "Never substitute a VPN IP for LAN or strip its tunnel label");
        check(StatusReport.network("Network details unavailable: Android denied access. Open TV network settings to inspect the connection.").availability
                == StatusReport.Availability.PERMISSION_DENIED, "Permission denial is distinct");
        check(StatusReport.network("Network details are unavailable on this device.").availability == StatusReport.Availability.UNAVAILABLE, "Absent service");
        check(StatusReport.network("Network details could not be read. Refresh or open TV network settings.").availability == StatusReport.Availability.UNAVAILABLE, "Runtime failure");
        check(StatusReport.network("No active default connection reported.\n\nNo visible connected networks. Offline or unavailable details; open TV settings to check.").availability
                == StatusReport.Availability.EMPTY, "No visible connection is not permission denial");
        check(StatusReport.network("Ethernet (available)\nAddress details unavailable.\n").availability == StatusReport.Availability.PARTIAL, "Missing IP details is partial");
        check(StatusReport.network(null).availability == StatusReport.Availability.UNAVAILABLE, "Missing report");
        check(StatusReport.mediaVolume(4, 10, true, false).contains("Media mute: On"), "Mute reported independently of nonzero volume");
        check(StatusReport.mediaVolume(0, 10, false, true).contains("Media mute: Off"), "Zero volume is not invented mute");
        check(StatusReport.mediaVolume(0, 10, false, true).contains("Fixed by Android"), "Fixed volume policy is explicit");
        check(StatusReport.mediaVolume(-1, 10, false, false).contains("not reported reliably"), "Bad volume values");
        check(!StatusReport.mediaVolume(5, 10, false, false).contains("50%"), "Volume index is not assumed acoustic percentage");
        System.out.println("Status reports: physical/VPN preservation, denied/empty/partial/unavailable and volume/mute distinctions passed.");
    }
}
