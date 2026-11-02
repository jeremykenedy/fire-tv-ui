package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.content.Context;
import android.util.AtomicFile;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

public final class LayoutScheduleTest {
    private static int checks;

    private interface Work {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Work work, String message) throws Exception {
        checks++;
        try {
            work.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static final String A = "11111111-1111-1111-1111-111111111111",
            B = "22222222-2222-2222-2222-222222222222",
            C = "33333333-3333-3333-3333-333333333333";

    private static long at(String value) {
        return Instant.parse(value).toEpochMilli();
    }

    public static void main(String[] args) throws Exception {
        LayoutOptions layout =
                new LayoutOptions(
                        400,
                        200,
                        Arrays.asList("widget:clock", "channel:unavailable.source"),
                        new LinkedHashSet<>(Arrays.asList("widget:clock")),
                        LayoutOptions.Mode.CARDS_ONLY);
        List<LayoutSchedule.Profile> profiles =
                Arrays.asList(
                        new LayoutSchedule.Profile(A, "Morning", layout),
                        new LayoutSchedule.Profile(B, "Evening", LayoutOptions.defaults(320, 180)),
                        new LayoutSchedule.Profile(C, "Night", LayoutOptions.defaults(240, 120)));
        LayoutSchedule.State empty = LayoutSchedule.State.empty();
        check(
                LayoutSchedule.resolve(empty, 100).status == LayoutSchedule.Status.BASE_LAYOUT,
                "off default keeps existing layout");
        check(
                empty.profiles.isEmpty() && empty.rotation.isEmpty() && empty.zone.isEmpty(),
                "no derived profiles or schedules");
        final LayoutSchedule.State base =
                new LayoutSchedule.State(
                        profiles,
                        LayoutSchedule.Mode.OFF,
                        Arrays.asList(A, B),
                        60,
                        100000,
                        "America/Los_Angeles",
                        Arrays.asList(
                                new LayoutSchedule.Slot(480, A), new LayoutSchedule.Slot(1080, B)),
                        "");
        LayoutSchedule.State rotation = base.mode(LayoutSchedule.Mode.ROTATION);
        check(
                LayoutSchedule.resolve(rotation, 100000).profileId.equals(A),
                "rotation starts at explicit anchor");
        check(
                LayoutSchedule.resolve(rotation, 159999).profileId.equals(A),
                "rotation holds through interval");
        check(
                LayoutSchedule.resolve(rotation, 160000).profileId.equals(B),
                "rotation changes exactly at boundary");
        check(
                LayoutSchedule.resolve(rotation, 220000).profileId.equals(A),
                "rotation wraps stable sequence");
        check(
                LayoutSchedule.resolve(rotation, 90000).profileId.equals(A),
                "clock before anchor retains first profile");
        check(
                LayoutSchedule.resolve(rotation, 130000).nextCheckMillis == 160000,
                "next rotation check aligns with boundary");
        LayoutSchedule.State manual = rotation.manual(B);
        check(
                LayoutSchedule.resolve(manual, Long.MAX_VALUE / 2).profileId.equals(B)
                        && LayoutSchedule.resolve(manual, Long.MAX_VALUE / 2).reason
                                == LayoutSchedule.Reason.MANUAL,
                "manual override has no implicit expiry");
        check(
                LayoutSchedule.resolve(manual, 100).nextCheckMillis == Long.MAX_VALUE,
                "manual override schedules no auto change");
        check(
                LayoutSchedule.resolve(manual.manual(""), 160000).profileId.equals(B),
                "explicit resume restores automatic policy");
        LayoutSchedule.State daily = base.mode(LayoutSchedule.Mode.DAILY);
        check(
                LayoutSchedule.resolve(daily, at("2026-11-01T15:59:59Z")).profileId.equals(B),
                "before first daily rule uses preceding day");
        check(
                LayoutSchedule.resolve(daily, at("2026-11-01T16:00:00Z")).profileId.equals(A),
                "daily selection uses explicit timezone");
        check(
                LayoutSchedule.resolve(daily, at("2026-11-02T02:00:00Z")).profileId.equals(B),
                "daily evening switch uses local day");
        LayoutSchedule.State overlap =
                new LayoutSchedule.State(
                        profiles,
                        LayoutSchedule.Mode.DAILY,
                        Collections.<String>emptyList(),
                        60,
                        0,
                        "America/Los_Angeles",
                        Arrays.asList(
                                new LayoutSchedule.Slot(0, A), new LayoutSchedule.Slot(90, B)),
                        "");
        check(
                LayoutSchedule.resolve(overlap, at("2026-11-01T08:30:00Z")).profileId.equals(B),
                "repeated-hour start uses first occurrence");
        check(
                LayoutSchedule.resolve(overlap, at("2026-11-01T09:15:00Z")).profileId.equals(B),
                "fall-back hour does not replay previous dashboard");
        LayoutSchedule.State gap =
                new LayoutSchedule.State(
                        profiles,
                        LayoutSchedule.Mode.DAILY,
                        Collections.<String>emptyList(),
                        60,
                        0,
                        "America/Los_Angeles",
                        Arrays.asList(
                                new LayoutSchedule.Slot(0, A), new LayoutSchedule.Slot(150, B)),
                        "");
        check(
                LayoutSchedule.resolve(gap, at("2026-03-08T10:00:00Z")).profileId.equals(B),
                "skipped wall time switches at first valid instant");
        LayoutSchedule.State tied =
                gap.daily(
                        "America/Los_Angeles",
                        Arrays.asList(
                                new LayoutSchedule.Slot(0, A),
                                new LayoutSchedule.Slot(150, B),
                                new LayoutSchedule.Slot(180, C)));
        check(
                LayoutSchedule.resolve(tied, at("2026-03-08T10:00:00Z")).profileId.equals(C),
                "collapsed DST starts favor later configured local time");
        LayoutSchedule.Decision unavailable = LayoutSchedule.resolve(base.manual(A), 1);
        check(
                unavailable.status == LayoutSchedule.Status.READY
                        && unavailable.profile.layout.order.contains("channel:unavailable.source"),
                "unsupported provider slot survives independently of dashboard identity");
        LayoutSchedule.State missing =
                base.profiles(Arrays.asList(profiles.get(1), profiles.get(2))).manual(A);
        check(
                LayoutSchedule.resolve(missing, 1).status == LayoutSchedule.Status.MISSING_PROFILE,
                "missing profile is explicit and not silently skipped");
        check(
                LayoutSchedule.resolve(empty.mode(LayoutSchedule.Mode.ROTATION), 1).status
                        == LayoutSchedule.Status.NEEDS_CONFIGURATION,
                "unconfigured rotation distinct from missing profile");
        rejects(() -> base.daily("Invalid/Zone", base.slots), "invalid time zone rejected");
        rejects(
                () ->
                        base.daily(
                                base.zone,
                                Arrays.asList(
                                        new LayoutSchedule.Slot(480, A),
                                        new LayoutSchedule.Slot(480, B))),
                "duplicate daily local times rejected");
        rejects(
                () -> base.rotation(Arrays.asList(A, A), 60, 0),
                "duplicate rotation identities rejected");
        rejects(() -> LayoutSchedule.minute("24:00"), "invalid local time rejected");
        check(LayoutSchedule.minute("08:30") == 510, "explicit HH:mm parser");
        byte[] encoded = LayoutScheduleCodec.encode(manual);
        LayoutSchedule.State roundtrip = LayoutScheduleCodec.decode(encoded);
        check(
                roundtrip.manual.equals(B)
                        && roundtrip
                                .profiles
                                .get(0)
                                .layout
                                .backupValues()
                                .equals(layout.backupValues()),
                "roundtrip preserves manual override and exact layout fields");
        JSONObject unsafe =
                new JSONObject(new String(encoded, java.nio.charset.StandardCharsets.UTF_8));
        unsafe.put("account_token", "fixture");
        rejects(
                () ->
                        LayoutScheduleCodec.decode(
                                unsafe.toString()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "unreviewed content fields rejected");
        final Context context = new Context(new File(args[0], "isolated"));
        final LayoutScheduleStore store = new LayoutScheduleStore(context);
        check(store.read().mode == LayoutSchedule.Mode.OFF, "new store is opt-out");
        String id = store.save("Layout one", null, layout);
        store.selectManual(id);
        check(
                new LayoutScheduleStore(context).resolve(1).profileId.equals(id),
                "manual selection persists across store recreation");
        AtomicFile.failNext = true;
        rejects(() -> store.rename(id, "Changed"), "failed AtomicFile write rejected");
        check(
                store.read().profile(id).name.equals("Layout one"),
                "failed write preserves previous profile");
        store.resumeAutomatic();
        check(
                store.resolve(1).status == LayoutSchedule.Status.BASE_LAYOUT,
                "resume with Off returns base layout");
        rejects(
                () -> store.mode(LayoutSchedule.Mode.DAILY),
                "cannot silently enable unconfigured schedule");
        store.rotation(Arrays.asList(id), 60, 100);
        store.mode(LayoutSchedule.Mode.ROTATION);
        store.selectManual(id);
        store.delete(id);
        check(
                store.resolve(200).status == LayoutSchedule.Status.MISSING_PROFILE,
                "deleted selection keeps explicit missing identity");
        File file = new File(context.getFilesDir(), "layout-schedules/config.json");
        byte[] before = Files.readAllBytes(file.toPath());
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        rejects(
                () -> store.save("Must not replace corrupt state", null, layout),
                "malformed catalog blocks mutation");
        check(
                Arrays.equals(new byte[] {1, 2, 3}, Files.readAllBytes(file.toPath())),
                "malformed stored state retained untouched");
        Files.write(file.toPath(), before);
        System.out.println("Layout schedule: " + checks + " checks passed.");
    }
}
