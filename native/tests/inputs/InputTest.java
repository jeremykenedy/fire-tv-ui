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
        String missing = "com.example.input/.Missing";
        String second = "com.example.input/.Second";
        java.util.List<String> ids = java.util.Arrays.asList(id, missing, second);
        InputOptions options = new InputOptions(ids, new java.util.LinkedHashSet<String>());
        check(options.savedOrder().equals(id + "\n" + missing + "\n" + second), "Exact identities serialize in order");
        check(InputOptions.parseOrder(options.savedOrder()).equals(ids), "Order round trip");
        InputOptions moved = options.move(second, -1, java.util.Arrays.asList(id, second));
        check(moved.order.equals(java.util.Arrays.asList(id, second, missing)), "Reordering keeps unavailable saved identities");
        InputOptions hidden = moved.hidden(second, true).hidden(missing, true);
        check(hidden.hidden.size() == 2 && hidden.hidden(second, false).hidden.contains(missing), "Show one preserves another hidden choice");
        check(hidden.showAll().hidden.isEmpty() && hidden.showAll().order.equals(moved.order), "Show-all retains order");
        check(options.move(id, -1, ids) == options, "Edge moves preserve order");
        check(options.arranged(java.util.Arrays.asList(second)).contains(missing), "Absent identities are never pruned");
        try { InputOptions.parseOrder(id + "\n" + id); throw new AssertionError("Duplicate order accepted"); }
        catch (IllegalArgumentException expected) { }
        try { InputOptions.parseOrder(id + "\n"); throw new AssertionError("Empty order item accepted"); }
        catch (IllegalArgumentException expected) { }
        check(new InputEntry(id, "HDMI 1", "Console", InputEntry.State.MISSING, true, true).hidden, "Visibility independent of name/state");
        System.out.println("InputTest PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
