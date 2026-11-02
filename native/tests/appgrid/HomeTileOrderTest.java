package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HomeTileOrderTest {
    public static void main(String[] args) {
        String a = "a.app/.Main", b = "b.app/.Main", missing = "missing.app/.Main";
        InputEntry hdmi = new InputEntry("vendor.tv/.Input/HW1", "HDMI 1", null, InputEntry.State.DISCONNECTED, true);
        FolderOptions folders = FolderOptions.empty().create("games", "Games").addApp("games", a);
        HomeTileOrder order = new HomeTileOrder(Arrays.asList(hdmi.artworkKey(), missing, "folder:games", b, a));
        AppGridOptions apps = AppGridOptions.defaults().discover(Arrays.asList(a, b, missing));
        List<String> visible = order.visible(apps, Arrays.asList(a, b), folders, Arrays.asList(hdmi));
        check(visible.equals(Arrays.asList(hdmi.artworkKey(), "folder:games", b)), "Mixed order and disconnected input survive; folder member omitted");
        HomeTileOrder moved = order.move(b, -1, visible, apps.favorites);
        check(moved.order.equals(Arrays.asList(hdmi.artworkKey(), missing, b, "folder:games", a)), "Move preserves unavailable slot and folder member identity");
        check(moved.visible(apps, Arrays.asList(a, b, missing), folders, Arrays.asList(hdmi))
                .equals(Arrays.asList(hdmi.artworkKey(), missing, b, "folder:games")), "Returning app recovers remembered position");
        check(order.visible(apps, Arrays.asList(a, b), FolderOptions.empty(), Arrays.asList(hdmi))
                .equals(Arrays.asList(hdmi.artworkKey(), b, a)), "Removing folder restores member without deleting its order");
        check(order.visible(apps.toggleHidden(b), Arrays.asList(a, b), folders, Arrays.asList(hdmi))
                .equals(Arrays.asList(hdmi.artworkKey(), "folder:games")), "App hidden state still applies");
        apps = apps.toggleFavorite(b);
        visible = order.visible(apps, Arrays.asList(a, b), folders, Arrays.asList(hdmi));
        check(visible.equals(Arrays.asList(b, hdmi.artworkKey(), "folder:games")), "Only explicit favorite moves ahead");
        check(order.move(b, 1, visible, apps.favorites) == order, "Ordinary tile cannot cross favorite boundary");
        InputEntry hidden = new InputEntry(hdmi.id, "HDMI 1", null, InputEntry.State.CONNECTED, true, true);
        check(!order.visible(apps, Arrays.asList(a, b), folders, Arrays.asList(hidden)).contains(hdmi.artworkKey()), "Input hidden state still applies");
        Context context = new Context();
        context.prefs.values.put("unknown", "kept");
        HomeTilePreferences.move(context, "folder:games", -1, visible, Collections.<String>emptySet());
        check("kept".equals(context.prefs.values.get("unknown")), "Order write preserves other preferences");
        Map<String, Object> before = new HashMap<>(context.prefs.values);
        context.prefs.fail = true;
        try { HomeTilePreferences.move(context, b, 1, visible, Collections.<String>emptySet()); throw new AssertionError("Failed commit accepted"); }
        catch (IllegalStateException expected) {}
        check(before.equals(context.prefs.values), "Failed commit restores old mixed order");
        context.prefs.fail = false;
        context.prefs.values.put(HomeTilePreferences.KEY, "invalid\nidentity");
        before = new HashMap<>(context.prefs.values);
        try { HomeTilePreferences.load(context); throw new AssertionError("Malformed order accepted"); }
        catch (IllegalStateException expected) {}
        check(before.equals(context.prefs.values), "Unreadable order stays recoverable");
        System.out.println("Mixed Home order, folder membership, returning tiles, visibility, explicit favorites and recovery passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
