package com.jeremykenedy.firetv.ui;

public final class DiagnosticValuesTest {
    public static void main(String[] args) {
        expect("1920x1080", DiagnosticValues.contentSize(1920, 1080));
        unavailable(DiagnosticValues.contentSize(0, 1080));
        unavailable(DiagnosticValues.contentSize(1920, 0));
        expect("On", DiagnosticValues.enabled("1"));
        expect("Off", DiagnosticValues.enabled("0"));
        unavailable(DiagnosticValues.enabled(null));
        unavailable(DiagnosticValues.enabled("invalid"));
        expect("None reported", DiagnosticValues.currentDream("mCurrentDream: null\n"));
        expect("None reported", DiagnosticValues.currentDream("  mCurrentDream=none\n"));
        expect(
                "com.tv/.Dream",
                DiagnosticValues.currentDream(
                        "mCurrentDream: DreamRecord{name=ComponentInfo{com.tv/.Dream},"
                                + " isPreview=false}\n"));
        expect(
                "com.tv/com.tv.Dream$Nested",
                DiagnosticValues.currentDream(
                        "mCurrentDream=ComponentInfo{com.tv/com.tv.Dream$Nested}"));
        unavailable(DiagnosticValues.currentDream(null));
        unavailable(DiagnosticValues.currentDream("Permission Denial: can't dump dreams"));
        unavailable(DiagnosticValues.currentDream("mDreaming=true"));
        unavailable(DiagnosticValues.currentDream("mCurrentDream: unknown-record"));
        System.out.println("Diagnostic report parsing checks passed.");
    }

    private static void unavailable(String actual) {
        if (!actual.startsWith("Unavailable:")) throw new AssertionError(actual);
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(actual);
    }
}
