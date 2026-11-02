package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.util.AtomicFile;
import android.graphics.Bitmap;
import android.net.Uri;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import java.io.*;
import java.util.*;

/** Self-targeted fixture only. A real process stop leaves the journal for the next invocation. */
public final class MigrationCrashInstrumentation extends Instrumentation {
    private static final String HIDDEN_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PLEX_KEY = "plex:9999999999999999999999999999999999999999999999999999999999999999:123";
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!"com.jeremykenedy.firetv.migrationproof".equals(context.getPackageName())) throw new AssertionError("Unexpected fixture package.");
            String phase = arguments.getString("phase", "");
            if (phase.equals("interrupt")) interrupt(context, Integer.parseInt(arguments.getString("after", "1")), "folder".equals(arguments.getString("domain", "")));
            else if (phase.equals("recover")) recover(context, result);
            else throw new AssertionError("Use interrupt or recover phase.");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failed) {
            result.putString("failure", failed.getClass().getSimpleName());
            result.putString("detail", failed.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void interrupt(Context context, final int after, boolean folder) throws IOException {
        if (after < 1 || after > (folder ? 6 : 3)) throw new AssertionError("Invalid interrupted store.");
        for (String file : LegacyPresentationMapper.FILES) {
            if (!context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()) throw new IOException("Fixture reset failed.");
        }
        File recovery = new File(context.getNoBackupFilesDir(), "presentation-migration-v1.snapshot");
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File file = new File(recovery.getPath() + suffix);
            if (file.exists() && !file.delete()) throw new IOException("Fixture recovery reset failed.");
        }
        SharedPreferences original = context.getSharedPreferences(LauncherMigration.PREFERENCES, Context.MODE_PRIVATE);
        if (!original.edit().putInt("apps_per_row", 7).putInt("app_max_rows", 0)
                .putString("icon_focus_style", "border").putInt("icon_focus_border", 0xff123456)
                .putLong("unknown_future", 9000000000L).putFloat("unknown_float", -0.0f)
                .putBoolean("white_border", false).putStringSet("unknown_set", new LinkedHashSet<>(Arrays.asList("fixture-a", "fixture-b")))
                .commit()) throw new IOException("Fixture seed failed.");
        if (folder && !original.edit().putString("folders", "{\"123\":{\"name\":\"Fixture\",\"isLocked\":false,\"apps\":[\"fixture.alpha\"]}}")
                .putString("display_order", "folder://123,fixture.alpha").remove("unknown_future").remove("unknown_float").remove("unknown_set")
                .commit()) throw new IOException("Folder seed failed.");
        String iconUri = folder ? seedIcon(context) : null;
        if (folder && !original.edit().putString("app_visibility", "{\"fixture.alpha\":{\"customIconUri\":\"" + iconUri + "\"}}").commit())
            throw new IOException("Icon seed failed.");
        if (folder && !original.edit().putStringSet("hidden_featured_cards", Collections.singleton(HIDDEN_HASH)).commit())
            throw new IOException("Hidden item seed failed.");
        final AndroidPresentationStore actual = new AndroidPresentationStore(context);
        PresentationSnapshot before = actual.read();
        Map<String, PreferenceSnapshot> nativeBefore = new LinkedHashMap<>(before.stores);
        nativeBefore.remove(LauncherMigration.PREFERENCES);
        PreferenceSnapshot source = before.stores.get(LauncherMigration.PREFERENCES);
        String component = "fixture.alpha/fixture.alpha.Main";
        PresentationMappingContext identities = folder ? new PresentationMappingContext(Collections.singletonMap("fixture.alpha", component),
                Collections.<String, PresentationMappingContext.Feed>emptyMap(), fixtureItems(),
                AndroidLegacyIconMigration.prepare(context, Collections.singleton(iconUri))) : PresentationMappingContext.empty();
        LegacyFolderMigration.Plan folders = folder ? LegacyFolderMigration.decode(source,
                Collections.singletonMap("fixture.alpha", Collections.singletonList(component))) : null;
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(source, identities, nativeBefore, folders, false);
        AtomicFile expected = new AtomicFile(new File(context.getNoBackupFilesDir(), "fixture-expected.snapshot"));
        FileOutputStream output = expected.startWrite();
        try { output.write(before.encode()); expected.finishWrite(output); }
        catch (IOException failed) { expected.failWrite(output); throw failed; }
        if (!context.getSharedPreferences("fixture_control", Context.MODE_PRIVATE).edit().putInt("pid", Process.myPid())
                .putInt("after", after).putBoolean("folder", folder).commit()) throw new IOException("Fixture control save failed.");
        PresentationUpgrade.Store interrupted = new PresentationUpgrade.Store() {
            private int writes;
            @Override public Object lock() { return actual.lock(); }
            @Override public PresentationSnapshot read() throws IOException { return actual.read(); }
            @Override public void saveRecovery(PresentationSnapshot snapshot) throws IOException { actual.saveRecovery(snapshot); }
            @Override public boolean hasRecovery() { return actual.hasRecovery(); }
            @Override public PresentationSnapshot readRecovery() throws IOException { return actual.readRecovery(); }
            @Override public void resolveRecovery(PresentationSnapshot original, PresentationSnapshot current) throws IOException { actual.resolveRecovery(original, current); }
            @Override public boolean replace(String file, PreferenceSnapshot snapshot) throws IOException {
                boolean committed = actual.replace(file, snapshot);
                if (committed && ++writes == after) {
                    Process.killProcess(Process.myPid());
                    throw new AssertionError("Fixture process termination returned.");
                }
                return committed;
            }
        };
        if (folder) ProductionMigrationGate.commit(interrupted, before, identities, folders);
        else PresentationTransaction.apply(interrupted, plan);
        throw new AssertionError("Fixture interruption did not occur.");
    }
    private static void recover(Context context, Bundle result) throws IOException {
        SharedPreferences control = context.getSharedPreferences("fixture_control", Context.MODE_PRIVATE);
        int priorPid = control.getInt("pid", -1);
        if (priorPid < 0 || priorPid == Process.myPid()) throw new AssertionError("A new fixture process is required.");
        byte[] bytes;
        try (InputStream input = new AtomicFile(new File(context.getNoBackupFilesDir(), "fixture-expected.snapshot")).openRead()) {
            ByteArrayOutputStream data = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) {
                if ((long) data.size() + size > PresentationSnapshot.MAX_BYTES) throw new IOException("Fixture data too large.");
                data.write(buffer, 0, size);
            }
            bytes = data.toByteArray();
        }
        PresentationSnapshot expected = PresentationSnapshot.decode(bytes);
        AndroidPresentationStore store = new AndroidPresentationStore(context);
        PresentationSnapshot journal = store.readRecovery();
        if (!expected.sameValues(journal)) throw new AssertionError("Unexpected fixture journal.");
        PresentationTransaction.recover(store, journal);
        if (!expected.sameValues(store.read())) throw new AssertionError("Exact typed recovery failed.");
        if (control.getBoolean("folder", false)) {
            store.resolveRecovery(journal, journal);
            PreferenceSnapshot source = store.read().stores.get(LauncherMigration.PREFERENCES);
            String component = "fixture.alpha/fixture.alpha.Main";
            String iconUri = Uri.fromFile(new File(context.getFilesDir(), "fixture-legacy-icon.png")).toString();
            PresentationMappingContext identities = new PresentationMappingContext(Collections.singletonMap("fixture.alpha", component),
                    Collections.<String, PresentationMappingContext.Feed>emptyMap(), fixtureItems(),
                    AndroidLegacyIconMigration.prepare(context, Collections.singleton(iconUri)));
            LegacyFolderMigration.Plan folders = LegacyFolderMigration.decode(source,
                    Collections.singletonMap("fixture.alpha", Collections.singletonList(component)));
            ProductionMigrationGate.commit(store, store.read(), identities, folders);
            PresentationSnapshot published = store.read();
            Object saved = published.stores.get(LegacyPresentationMapper.FOLDERS).values().get("folders");
            Object order = published.stores.get(LegacyPresentationMapper.GRID).values().get("home_order");
            if (!LegacyFolderMigration.encode(folders.folders).equals(saved) || !("folder:123\n" + component).equals(order))
                throw new AssertionError("Folder and Home order publication failed.");
            if (!ProductionMigrationGate.completed(published)
                    || !identities.icons.get(iconUri).equals(published.stores.get(LegacyPresentationMapper.ICONS).values().get(component))
                    || !Integer.valueOf(0x263a5c).equals(published.stores.get(LegacyPresentationMapper.FOCUS).values().get("fill_rgb")))
                throw new AssertionError("Icon or owned focus fallback publication failed.");
            Map<String, Object> cardValues = new LinkedHashMap<>(CardOptions.defaults().backupValues());
            cardValues.putAll(published.stores.get(LegacyPresentationMapper.CARDS).values());
            CardOptions cards = CardOptions.fromBackup(cardValues);
            if (!cards.isHidden("com.jeremykenedy.firetv.ui", PLEX_KEY) || !cards.isHidden("com.plexapp.android", PLEX_KEY))
                throw new AssertionError("Hidden Plex representations were not preserved.");
            result.putBoolean("folder_published", true);
            result.putBoolean("icon_and_focus_published", true);
            result.putBoolean("plex_hidden", true);
        }
        result.putInt("previous_pid", priorPid); result.putInt("current_pid", Process.myPid());
        result.putInt("interrupted_after", control.getInt("after", -1)); result.putBoolean("restored", true);
    }
    private static String seedIcon(Context context) throws IOException {
        File file = new File(context.getFilesDir(), "fixture-legacy-icon.png");
        Bitmap image = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888);
        image.eraseColor(0xff346799);
        try (OutputStream output = new FileOutputStream(file)) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Icon seed failed.");
        } finally { image.recycle(); }
        return Uri.fromFile(file).toString();
    }
    private static Map<String, PresentationMappingContext.Item> fixtureItems() {
        return Collections.singletonMap(HIDDEN_HASH, new PresentationMappingContext.Item("com.jeremykenedy.firetv.ui",
                PLEX_KEY, "Fixture", false, "com.plexapp.android", PLEX_KEY));
    }
}
