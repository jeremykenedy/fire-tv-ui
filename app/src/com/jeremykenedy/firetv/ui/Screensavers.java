package com.jeremykenedy.firetv.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Component and ordering rules shared by the picker and its tests. */
public final class Screensavers {
    public static final String BIND_PERMISSION = "android.permission.BIND_DREAM_SERVICE";

    private Screensavers() {
    }

    public static final class Choice {
        public final String component;
        public final String label;

        public Choice(String component, String label) {
            this.component = component;
            this.label = label;
        }
    }

    public static String fullComponent(String component) {
        if (component == null) {
            return "";
        }
        int slash = component.indexOf('/');
        if (slash <= 0 || slash == component.length() - 1) {
            return component;
        }
        String name = component.substring(slash + 1);
        return name.startsWith(".")
                ? component.substring(0, slash + 1) + component.substring(0, slash) + name
                : component;
    }

    public static boolean isScreensaver(String permission) {
        return BIND_PERMISSION.equals(permission);
    }

    public static String previewFile(String component) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    fullComponent(component).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : hash) {
                name.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return name.append(".jpg").toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public static boolean isHidden(String packageName) {
        return "com.amazon.tv.quicksettings".equals(packageName)
                || "com.android.systemui".equals(packageName);
    }

    public static String displayName(String packageName, String label) {
        return "com.amazon.ftv.screensaver".equals(packageName) ? "Amazon screensaver" : label;
    }

    public static List<Choice> sorted(List<Choice> choices) {
        List<Choice> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Choice choice : choices) {
            if (seen.add(fullComponent(choice.component))) {
                result.add(choice);
            }
        }
        Collections.sort(result, new Comparator<Choice>() {
            @Override
            public int compare(Choice left, Choice right) {
                int order = left.label.toLowerCase(Locale.ROOT)
                        .compareTo(right.label.toLowerCase(Locale.ROOT));
                return order != 0 ? order : left.component.compareTo(right.component);
            }
        });
        return result;
    }

    public static int indexOfActive(List<Choice> choices, String active) {
        String wanted = fullComponent(active == null ? null : active.split(",", 2)[0]);
        for (int index = 0; index < choices.size(); index++) {
            if (fullComponent(choices.get(index).component).equals(wanted)) {
                return index;
            }
        }
        return -1;
    }
}
