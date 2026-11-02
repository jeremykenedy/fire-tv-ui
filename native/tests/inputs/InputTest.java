package com.jeremykenedy.firetv.ui.nativeapp.inputs;

public final class InputTest {
    public static void main(String[] args) {
        String id = "com.example.input/.Port/HDMI1001";
        InputEntry original = new InputEntry(id, "HDMI 1", null, InputEntry.State.CONNECTED, true);
        InputEntry renamed = new InputEntry(id, "HDMI 1", "Game console", InputEntry.State.MISSING, true);
        check(original.artworkKey().equals(renamed.artworkKey()), "Renaming and removal preserve artwork identity");
        check(original.canOpen() && !renamed.canOpen(), "Missing input cannot tune");
        check(!new InputEntry(id, "HDMI 1", null, InputEntry.State.DISCONNECTED, true).canOpen(), "Disconnected input cannot tune");
        check(new InputEntry(id, "HDMI 1", null, InputEntry.State.STANDBY, true).canOpen(), "Standby may be opened");
        check(original.displayName.equals("HDMI 1"), "Factory reset falls back to provider label");
        check(InputEntry.name("  Console  ").equals("Console"), "Name trim");
        for (String invalid : new String[]{"", "  ", "Two\nlines", new String(new char[61]).replace('\0', 'x')}) {
            try { InputEntry.name(invalid); throw new AssertionError("Invalid name accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        check(!InputEntry.validId("\n") && !InputEntry.validId(null), "Invalid IDs rejected");
        System.out.println("InputTest PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
