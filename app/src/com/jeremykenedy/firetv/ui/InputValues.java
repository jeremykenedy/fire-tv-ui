package com.jeremykenedy.firetv.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps physical TV ports to Fire OS's native input types. */
public final class InputValues {
    private static final Pattern HDMI = Pattern.compile("HDMI\\s*([1-4])(?:\\b.*)?",
            Pattern.CASE_INSENSITIVE);

    private InputValues() {
    }

    public static int amazonType(int type, String label, int port) {
        if (type == 1001) {
            return 11;
        }
        if (type == 1004) {
            return 12;
        }
        if (type != 1007) {
            return -1;
        }
        if (port < 1 || port > 4) {
            Matcher match = HDMI.matcher(label == null ? "" : label.trim());
            if (!match.matches()) {
                return -1;
            }
            port = Integer.parseInt(match.group(1));
        }
        return port == 4 ? 10 : port + 1;
    }
}
