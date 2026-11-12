package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import java.io.*;
import java.util.*;

/** Outer recovery boundary when explicitly selected household documents accompany presentation. */
public final class BackupTransaction {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private BackupTransaction() {}
    private static AtomicFile journal(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "portable-household-import-v1.snapshot"));
    }
    public static boolean hasRecovery(Context context) {
        File path = journal(context).getBaseFile();
        return path.exists() || new File(path.getPath()+".bak").exists();
    }
    public static synchronized void applyOriginal(Context context, OriginalBackup backup) throws Exception {
        synchronized (PresentationState.class) {
            if (!backup.unsupported.isEmpty()) throw new IOException("This original backup includes unsupported settings. Nothing was changed.");
            if (hasRecovery(context) || PresentationState.hasRecovery(context)) throw new IOException("Restore the interrupted presentation change first.");
            Map<String,PreferenceSnapshot> target = new com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupManager(context).prepareOriginalBackup(backup.settings);
            PresentationState.apply(context,target,true);
        }
    }
    public static synchronized void apply(Context context, BackupPayload payload) throws Exception { synchronized(PresentationState.class) {
        if (com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.hasRecovery(context)) throw new IOException("Restore the interrupted holiday change first.");
        PresentationState.validatePortable(payload.stores);
        Map<String,String> documents = HouseholdContent.sanitize(payload.household);
        if (hasRecovery(context) || PresentationState.hasRecovery(context)) throw new IOException("Restore the interrupted presentation change first.");
        if (documents.isEmpty()) { PresentationState.applyPortable(context,payload.stores); return; }
        Map<String,PreferenceSnapshot> originals = new LinkedHashMap<>();
        for (String name : documents.keySet()) {
            Map<String,?> saved = context.getSharedPreferences(HouseholdContent.store(name),0).getAll();
            Map<String,Object> values = new LinkedHashMap<>();
            if (saved.containsKey("document")) {
                if (!(saved.get("document") instanceof String)) throw new IOException("Existing household data could not be preserved.");
                values.put("document",saved.get("document"));
            }
            originals.put(name,new PreferenceSnapshot(values));
        }
        Recovery before = new Recovery(PresentationState.capture(context),originals);
        byte[] bytes = encode(before);
        AtomicFile file = journal(context); FileOutputStream output = null;
        try { output=file.startWrite(); output.write(bytes); file.finishWrite(output); }
        catch(Exception failure) { if(output!=null)file.failWrite(output); throw failure; }
        if (!Arrays.equals(bytes,readBytes(context))) throw new IOException("Private recovery could not be verified.");
        try {
            PresentationState.applyPortable(context,payload.stores);
            if(PresentationState.hasRecovery(context))throw new IOException("Presentation recovery could not be resolved.");
            for (Map.Entry<String,String> entry : documents.entrySet()) {
                write(context,entry.getKey(),Collections.<String,Object>singletonMap("document",entry.getValue()));
            }
            file.delete();
            if(hasRecovery(context))throw new IOException("Private recovery could not be resolved.");
        } catch (Exception failed) {
            try { recover(context); }
            catch (Exception rollbackFailed) { throw new IOException("Import failed and private recovery must be restored.",rollbackFailed); }
            throw new IOException("Import failed. Previous presentation and household content restored.",failed);
        }
    } }
    public static synchronized void recover(Context context) throws Exception { synchronized(PresentationState.class) {
        if (com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.hasRecovery(context)) throw new IOException("Restore the interrupted holiday change first.");
        if (!hasRecovery(context)) { if(PresentationState.hasRecovery(context))PresentationState.recover(context); return; }
        Recovery before=decode(readBytes(context));
        if(PresentationState.hasRecovery(context))PresentationState.recover(context);
        PresentationState.apply(context,before.stores,true);
        for(Map.Entry<String,PreferenceSnapshot> entry:before.household.entrySet())write(context,entry.getKey(),entry.getValue().values());
        Map<String,PreferenceSnapshot> current=PresentationState.capture(context);
        for(String file:before.stores.keySet())if(!before.stores.get(file).values().equals(current.get(file).values()))throw new IOException("Presentation restoration could not be verified.");
        journal(context).delete();
        if(hasRecovery(context))throw new IOException("Private recovery could not be resolved.");
    } }
    private static void write(Context context,String name,Map<String,Object> values) throws IOException {
        SharedPreferences preferences=context.getSharedPreferences(HouseholdContent.store(name),0);
        SharedPreferences.Editor editor=preferences.edit();
        Object value=values.get("document");
        if(value==null)editor.remove("document");else if(value instanceof String)editor.putString("document",(String)value);else throw new IOException("Invalid private recovery.");
        if(!editor.commit() || !Objects.equals(preferences.getAll().get("document"),value))throw new IOException("Household content could not be saved or verified.");
    }
    private static final class Recovery {
        final Map<String,PreferenceSnapshot> stores,household;
        Recovery(Map<String,PreferenceSnapshot> stores,Map<String,PreferenceSnapshot> household){this.stores=stores;this.household=household;}
    }
    private static byte[] encode(Recovery recovery) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
        output.writeInt(0x46544248);output.writeInt(1);writeMap(output,recovery.stores);writeMap(output,recovery.household);output.flush();
        if(bytes.size()>MAX_BYTES)throw new IOException("Private recovery is too large.");return bytes.toByteArray();
    }
    private static void writeMap(DataOutputStream output,Map<String,PreferenceSnapshot> stores) throws IOException {
        output.writeInt(stores.size());
        for(Map.Entry<String,PreferenceSnapshot> entry:stores.entrySet()){byte[] bytes=entry.getValue().encode();output.writeUTF(entry.getKey());output.writeInt(bytes.length);output.write(bytes);}
    }
    private static Recovery decode(byte[] bytes) throws Exception {
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        if(input.readInt()!=0x46544248||input.readInt()!=1)throw new IOException("Unsupported private recovery.");
        Map<String,PreferenceSnapshot> stores=readMap(input,32),household=readMap(input,2);
        if(input.available()!=0)throw new IOException("Malformed private recovery.");
        PresentationState.validate(stores);
        for(Map.Entry<String,PreferenceSnapshot> entry:household.entrySet()){
            HouseholdContent.store(entry.getKey());Map<String,Object> values=entry.getValue().values();
            if(values.size()>1||!values.isEmpty()&&(!(values.get("document") instanceof String)))throw new IOException("Malformed private recovery.");
        }
        return new Recovery(stores,household);
    }
    private static Map<String,PreferenceSnapshot> readMap(DataInputStream input,int limit) throws IOException {
        int count=input.readInt();if(count<0||count>limit)throw new IOException("Malformed private recovery.");
        Map<String,PreferenceSnapshot> result=new LinkedHashMap<>();
        for(int i=0;i<count;i++){String name=input.readUTF();int size=input.readInt();if(result.containsKey(name)||size<0||size>PreferenceSnapshot.MAX_BYTES||size>input.available())throw new IOException("Malformed private recovery.");byte[] bytes=new byte[size];input.readFully(bytes);result.put(name,PreferenceSnapshot.decode(bytes));}
        return result;
    }
    private static byte[] readBytes(Context context) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=journal(context).openRead()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)throw new IOException("Private recovery is too large.");bytes.write(buffer,0,count);}}
        return bytes.toByteArray();
    }
}
