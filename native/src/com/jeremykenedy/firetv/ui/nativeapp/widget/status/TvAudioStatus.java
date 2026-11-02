package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import java.util.LinkedHashSet;
import java.util.Set;

/** Public OS observations only. Connected outputs do not prove a selected route or receiver power. */
public final class TvAudioStatus {
    public static final String LIMITS = "Connected outputs are available devices, not a verified active media route. External receiver/soundbar power and input are not observable here.";
    private TvAudioStatus() {}

    public static StatusReport read(Context context) {
        StringBuilder out = new StringBuilder();
        int readable = 0, denied = 0, unavailable = 0;
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            DisplayManager displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (power == null && displays == null) { unavailable++; out.append("TV display status: service unavailable\n"); }
            else {
                if (power != null) out.append("Android interactive: ").append(power.isInteractive() ? "Yes" : "No").append('\n');
                Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
                out.append("Primary display: ").append(display == null ? "Not reported" : displayState(display.getState())).append('\n');
                if (power != null || display != null) readable++; else unavailable++;
            }
        } catch (SecurityException error) { denied++; out.append("TV display status: access denied\n"); }
        catch (RuntimeException error) { unavailable++; out.append("TV display status: unavailable\n"); }
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) { unavailable++; out.append("Audio status: service unavailable\n"); }
            else {
                out.append(StatusReport.mediaVolume(audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.isStreamMute(AudioManager.STREAM_MUSIC), audio.isVolumeFixed()));
                out.append("\nAndroid music activity: ").append(audio.isMusicActive() ? "Reported" : "Not reported").append('\n');
                Set<String> devices = new LinkedHashSet<>();
                AudioDeviceInfo[] outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (outputs != null) for (AudioDeviceInfo device : outputs) if (device != null) devices.add(outputType(device.getType()));
                out.append("Available audio outputs: ").append(devices.isEmpty() ? "None reported" : String.join(", ", devices)).append('\n');
                readable++;
            }
        } catch (SecurityException error) { denied++; out.append("Audio status: access denied\n"); }
        catch (RuntimeException error) { unavailable++; out.append("Audio status: unavailable\n"); }
        out.append('\n').append(LIMITS);
        StatusReport.Availability state = readable > 0
                ? denied + unavailable > 0 ? StatusReport.Availability.PARTIAL : StatusReport.Availability.READY
                : denied > 0 ? StatusReport.Availability.PERMISSION_DENIED : StatusReport.Availability.UNAVAILABLE;
        String details = out.toString();
        String summary = details.substring(0, details.indexOf("\n\n"))
                .replaceAll("Android interactive: [^\n]*\n", "")
                .replace("Primary display:", "Display:").replace(" (Android reported)", "")
                .replace("Media volume index:", "Media volume:").replace("Android music activity:", "Music activity:")
                .replace("Fixed by Android", "Fixed").replace("Adjustable by Android", "Adjustable");
        return new StatusReport(state, details, summary);
    }

    static String displayState(int state) {
        switch (state) {
            case Display.STATE_ON: return "On (Android reported)";
            case Display.STATE_OFF: return "Off (Android reported)";
            case Display.STATE_DOZE: return "Dozing (Android reported)";
            case Display.STATE_DOZE_SUSPEND: return "Doze suspended (Android reported)";
            default: return "Unknown Android state " + state;
        }
    }

    static String outputType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Built-in speaker";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "Digital line (OS label)";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "Analog line";
            case AudioDeviceInfo.TYPE_AUX_LINE: return "AUX line";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth SCO";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Wired headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB audio";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            default:
                if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_HDMI_EARC) return "HDMI eARC";
                return "Other output (OS type " + type + ")";
        }
    }
}
