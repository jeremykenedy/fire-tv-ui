package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.widget.notes.*;
import com.jeremykenedy.firetv.ui.nativeapp.widget.reminders.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public final class HouseholdBackupTest {
    private static int checks;
    private static final PortableBackup.Validator VALIDATOR=new PortableBackup.Validator(){
        @Override public void validate(Map<String,PreferenceSnapshot> stores){if(!stores.keySet().equals(Collections.singleton("native_test")))throw new IllegalArgumentException();}
    };
    public static void main(String[] args){
        Context context=new Context();
        NotesOptions notes=NotesOptions.defaults().add("Sanitized note",false);
        NotesPreferences.save(context,notes);
        reject(new Runnable(){@Override public void run(){HouseholdContent.capture(context,true,false);}});
        check(HouseholdContent.capture(context,false,false).isEmpty(),"No opt-in never exports private documents");
        NotesPreferences.save(context,notes.privacy(false,true));
        Map<String,String> selected=HouseholdContent.capture(context,true,false);
        NotesOptions safe=NotesPreferences.decode(selected.get(HouseholdContent.NOTES));
        check(safe.summaryOnly&&!safe.allowExport&&safe.items.size()==1,"Opt-in notes strip imported consent and hide details");
        RemindersOptions reminder=RemindersOptions.defaults().add("Sanitized reminder",LocalDate.of(2026,11,2),LocalTime.of(12,0),"America/Los_Angeles",RemindersOptions.Repeat.ONCE).settings(true,false,true);
        RemindersPreferences.save(context,reminder);
        selected=HouseholdContent.capture(context,true,true);
        RemindersOptions safeReminder=RemindersPreferences.decode(selected.get(HouseholdContent.REMINDERS));
        check(!safeReminder.enabled&&safeReminder.summaryOnly&&!safeReminder.allowExport,"Imported reminder cannot enable scheduling, details or export");
        Map<String,PreferenceSnapshot> state=Collections.singletonMap("native_test",new PreferenceSnapshot(Collections.singletonMap("explicit",false)));
        BackupPayload payload=new BackupPayload(state,selected);
        BackupPayload decoded=BackupPayload.decode(payload.encode(VALIDATOR),VALIDATOR);
        check(decoded.stores.get("native_test").values().equals(state.get("native_test").values()),"Optional envelope retains base typed presentation");
        check(decoded.household.equals(payload.household),"Sanitized content round trips");
        Map<String,String> hostile=Collections.singletonMap(HouseholdContent.NOTES,NotesPreferences.encode(notes.privacy(false,true)));
        NotesOptions sanitized=NotesPreferences.decode(new BackupPayload(state,hostile).household.get(HouseholdContent.NOTES));
        check(sanitized.summaryOnly&&!sanitized.allowExport,"Untrusted document consent is discarded before apply");
        check(BackupPayload.decode(PortableBackup.encode(state,VALIDATOR),VALIDATOR).household.isEmpty(),"Existing presentation-only documents remain compatible");
        reject(new Runnable(){@Override public void run(){HouseholdContent.sanitize(Collections.singletonMap("plex", "token"));}});
        reject(new Runnable(){@Override public void run(){HouseholdContent.sanitize(Collections.singletonMap(HouseholdContent.NOTES,"{broken"));}});
        System.out.println("Household backup privacy checks passed: "+checks);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Rejected content accepted");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
}
