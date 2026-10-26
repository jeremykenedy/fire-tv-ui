package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public final class HomeKeyDispatchTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Context context = new Context();
        Intent cached = new Intent();
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME);
        check(HomeKeyDispatch.dispatch(context, up, true, cached), "Successful Home is consumed");
        check(context.launched == cached, "Launch the exact cached intent without changing it");
        check(context.calls == 1, "Launch only once on Home up");
        check(Log.entries.toString().equals("[Home up dispatch, Home up launched]"), "Success diagnostics");

        context.fail = true;
        Log.entries.clear();
        check(!HomeKeyDispatch.dispatch(context, up, true, cached), "A failed launch leaves Home unconsumed");
        check(Log.entries.toString().equals("[Home up dispatch, Home up launch failed]"), "Failure diagnostics omit exception and intent data");
        context.fail = false;
        int calls = context.calls;
        Log.entries.clear();
        check(!HomeKeyDispatch.dispatch(context, up, true, null), "An unavailable launcher leaves Home unconsumed");
        check(context.calls == calls, "Do not launch null");
        check(Log.entries.toString().equals("[Home up dispatch, Home up unavailable]"), "Unavailable diagnostics");

        Log.entries.clear();
        check(HomeKeyDispatch.dispatch(context, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME), true, cached), "Preserve Home down consumption");
        check(HomeKeyDispatch.dispatch(context, new KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_HOME), true, cached), "Preserve non-up Home behavior");
        check(!HomeKeyDispatch.dispatch(context, new KeyEvent(KeyEvent.ACTION_UP, 4), true, cached), "Leave other keys alone");
        check(!HomeKeyDispatch.dispatch(context, up, false, cached), "Leave disabled Home alone");
        check(!HomeKeyDispatch.dispatch(context, null, true, cached), "Leave absent events alone");
        check(context.calls == calls && Log.entries.isEmpty(), "Other actions neither launch nor log dispatch");
        System.out.println("Home key dispatch behavior passed");
    }
}
