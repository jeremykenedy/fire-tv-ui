package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.content.Context;
import android.util.AtomicFile;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import java.io.*;
import java.util.*;

/** One atomic layout-only catalog. This store never opens any other preference file. */
public final class LayoutScheduleStore {
    private static final Object LOCK = new Object();
    private final File path;
    private final AtomicFile file;

    public LayoutScheduleStore(Context context) {
        path = new File(context.getFilesDir(), "layout-schedules/config.json");
        file = new AtomicFile(path);
    }

    public LayoutSchedule.State read() throws Exception {
        synchronized (LOCK) {
            return readLocked();
        }
    }

    private LayoutSchedule.State readLocked() throws Exception {
        if (!path.exists() && !new File(path.getPath() + ".bak").exists())
            return LayoutSchedule.State.empty();
        try (InputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > LayoutScheduleCodec.MAX_BYTES)
                    throw new IOException(
                            "Saved dashboards are too large. Stored data has been kept.");
                bytes.write(buffer, 0, count);
            }
            return LayoutScheduleCodec.decode(bytes.toByteArray());
        }
    }

    public LayoutSchedule.Decision resolve(long now) throws Exception {
        return LayoutSchedule.resolve(read(), now);
    }

    public String save(String name, String replace, LayoutOptions layout) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            String id = replace == null ? UUID.randomUUID().toString() : replace;
            List<LayoutSchedule.Profile> profiles = new ArrayList<>();
            boolean found = false;
            for (LayoutSchedule.Profile profile : state.profiles) {
                if (profile.id.equals(id)) {
                    profiles.add(new LayoutSchedule.Profile(id, name, layout));
                    found = true;
                } else profiles.add(profile);
            }
            if (replace != null && !found)
                throw new IOException("This dashboard no longer exists. Nothing was replaced.");
            if (!found) profiles.add(new LayoutSchedule.Profile(id, name, layout));
            write(state.profiles(profiles));
            return id;
        }
    }

    public void rename(String id, String name) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            LayoutSchedule.Profile profile = state.profile(id);
            if (profile == null) throw new IOException("This dashboard no longer exists.");
            save(name, id, profile.layout);
        }
    }

    public void delete(String id) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            List<LayoutSchedule.Profile> profiles = new ArrayList<>();
            boolean found = false;
            for (LayoutSchedule.Profile profile : state.profiles)
                if (profile.id.equals(id)) found = true;
                else profiles.add(profile);
            if (!found) throw new IOException("This dashboard no longer exists.");
            write(state.profiles(profiles));
        }
    }

    public void selectManual(String id) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            if (state.profile(id) == null)
                throw new IOException("This dashboard no longer exists.");
            write(state.manual(id));
        }
    }

    public void resumeAutomatic() throws Exception {
        synchronized (LOCK) {
            write(readLocked().manual(""));
        }
    }

    public void mode(LayoutSchedule.Mode mode) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            if (mode == LayoutSchedule.Mode.ROTATION && state.rotation.isEmpty())
                throw new IOException("Choose rotation dashboards first.");
            if (mode == LayoutSchedule.Mode.DAILY
                    && (state.zone.isEmpty() || state.slots.isEmpty()))
                throw new IOException("Choose a time zone and add a daily change first.");
            write(state.mode(mode));
        }
    }

    public void rotation(List<String> ids, int seconds, long now) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            for (String id : ids)
                if (state.profile(id) == null)
                    throw new IOException("A rotation dashboard no longer exists.");
            write(state.rotation(ids, seconds, now));
        }
    }

    public void daily(String zone, List<LayoutSchedule.Slot> slots) throws Exception {
        synchronized (LOCK) {
            LayoutSchedule.State state = readLocked();
            for (LayoutSchedule.Slot slot : slots)
                if (state.profile(slot.profile) == null)
                    throw new IOException("A scheduled dashboard no longer exists.");
            write(state.daily(zone, slots));
        }
    }

    private void write(LayoutSchedule.State next) throws Exception {
        byte[] bytes = LayoutScheduleCodec.encode(next);
        LayoutScheduleCodec.decode(bytes);
        if (!path.getParentFile().isDirectory() && !path.getParentFile().mkdirs())
            throw new IOException("Dashboard storage is unavailable.");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception failed) {
            if (output != null) file.failWrite(output);
            throw failed;
        }
        if (!Arrays.equals(bytes, LayoutScheduleCodec.encode(readLocked())))
            throw new IOException(
                    "Dashboard changes could not be verified. Reopen the screen before"
                        + " continuing.");
    }
}
