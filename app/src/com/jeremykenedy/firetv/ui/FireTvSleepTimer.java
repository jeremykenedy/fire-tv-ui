package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** Sends one-time sleep timer choices to the exported Fire TV Settings provider. */
public final class FireTvSleepTimer {
    private static final String SETTINGS_PACKAGE = "com.amazon.tv.settings.v2";
    private static final String PROVIDER = SETTINGS_PACKAGE + ".provider.SleepTimerProvider";
    private static final String DESCRIPTOR = "com.amazon.tv.settingsframework.provider.ISettingsProviderService";

    public interface Callback {
        void onComplete(Exception failure);
    }

    private FireTvSleepTimer() {
    }

    public static boolean isAvailable(Context context) {
        try {
            context.getPackageManager().getServiceInfo(new ComponentName(SETTINGS_PACKAGE, PROVIDER), 0);
            return true;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    public static void set(Context context, final String actionId, final Callback callback) {
        try {
            SleepTimerOptions.minutesForAction(actionId);
        } catch (IllegalArgumentException unsupported) {
            callback.onComplete(unsupported);
            return;
        }

        final Context app = context.getApplicationContext();
        Intent service = new Intent().setComponent(new ComponentName(SETTINGS_PACKAGE, PROVIDER));
        final ServiceConnection connection = new ServiceConnection() {
            private boolean completed;

            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                finish(this, callback, invoke(binder, actionId));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                finish(this, callback, new IllegalStateException("Fire TV Settings disconnected."));
            }

            private void finish(ServiceConnection connection, Callback callback, Exception failure) {
                if (completed) {
                    return;
                }
                completed = true;
                try {
                    app.unbindService(connection);
                } catch (IllegalArgumentException ignored) {
                    // The service may already have disconnected.
                }
                callback.onComplete(failure);
            }
        };
        try {
            boolean bound = app.bindService(service, connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                callback.onComplete(new IllegalStateException("Fire TV Settings did not start its sleep timer service."));
            }
        } catch (SecurityException | IllegalArgumentException unavailable) {
            callback.onComplete(unavailable);
        }
    }

    private static Exception invoke(IBinder binder, String actionId) {
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(DESCRIPTOR);
            input.writeInt(1);
            input.writeInt(1);
            input.writeInt(1);
            input.writeInt(1);
            writeStringField(input, actionId);
            writeStringField(input, null);
            input.writeInt(-1);

            if (!binder.transact(3, input, output, 0)) {
                return new IllegalStateException("Fire TV Settings does not support sleep timer changes.");
            }
            output.readException();
            if (output.readInt() != 1 || output.readInt() != 1 || output.readInt() != 1) {
                return new IllegalStateException("Fire TV Settings did not accept the sleep timer choice.");
            }
            int version = output.readInt();
            String returnedId = readStringField(output);
            int status = readEnumField(output);
            readEnumField(output);
            skipUnknownFields(output);
            if (version < 1 || !actionId.equals(returnedId) || status != 0) {
                return new IllegalStateException("Fire TV Settings rejected the sleep timer choice.");
            }
            return null;
        } catch (RemoteException | RuntimeException failed) {
            return new IllegalStateException("Could not change the Fire TV sleep timer.", failed);
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private static void writeStringField(Parcel parcel, String value) {
        parcel.writeInt(0);
        parcel.writeInt(value == null ? 0 : 1);
        if (value != null) {
            parcel.writeString(value);
        }
    }

    private static String readStringField(Parcel parcel) {
        int type = parcel.readInt();
        if (type == -1) {
            return null;
        }
        int present = parcel.readInt();
        if (type != 0 || present != 1) {
            throw new IllegalStateException("Unexpected Fire TV Settings response.");
        }
        return parcel.readString();
    }

    private static int readEnumField(Parcel parcel) {
        int type = parcel.readInt();
        if (type == -1) {
            return -1;
        }
        int present = parcel.readInt();
        if (type != 11 || present != 1) {
            throw new IllegalStateException("Unexpected Fire TV Settings response.");
        }
        return parcel.readInt();
    }

    private static void skipUnknownFields(Parcel parcel) {
        while (true) {
            int type = parcel.readInt();
            if (type == -1) {
                return;
            }
            int present = parcel.readInt();
            if (present != 1) {
                continue;
            }
            switch (type) {
                case 0:
                    parcel.readString();
                    break;
                case 1:
                case 2:
                case 11:
                    parcel.readInt();
                    break;
                case 3:
                    parcel.readDouble();
                    break;
                case 4:
                    parcel.readLong();
                    break;
                case 5:
                    parcel.readFloat();
                    break;
                case 6:
                    parcel.readByte();
                    break;
                default:
                    throw new IllegalStateException("Unknown Fire TV Settings response field.");
            }
        }
    }
}
