package com.jeremykenedy.firetv.ui.nativeapp.device.network;

public final class NetworkTest {
    public static void main(String[] args) throws Exception {
        check("Not reported".equals(DiagnosticReport.clean(null)), "Missing values must remain explicit");
        check("a b c".equals(DiagnosticReport.clean("a\nb\tc")), "Control characters must not forge report lines");
        check(DiagnosticReport.clean(new String(new char[200]).replace('\0', 'x')).length() == 160,
                "Manufacturer values must be bounded");
        check("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".equals(
                DiagnosticReport.sha256(new byte[0])), "Signer fingerprint must use SHA-256");
        System.out.println("PASS: diagnostic value bounds and known SHA-256 vector");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
