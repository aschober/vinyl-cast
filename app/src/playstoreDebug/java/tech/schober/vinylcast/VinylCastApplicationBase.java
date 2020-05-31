package tech.schober.vinylcast;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import timber.log.Timber;

public class VinylCastApplicationBase extends Application {
    private static final String TAG = "VinylCastApplicationBase";
    private static final boolean STRICT_MODE = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyFlashScreen()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        new Instabug.Builder(this, BuildConfig.INSTABUG_TOKEN)
                .setInvocationEvents(InstabugInvocationEvent.NONE)
                .build();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        Timber.plant(new CrashReportingTree());
    }

    private class CrashReportingTree extends Timber.Tree {
        @Override
        protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable throwable) {
            if (priority >= Log.DEBUG) {
                FirebaseCrashlytics.getInstance().log(message);
                if (throwable != null) {
                    FirebaseCrashlytics.getInstance().recordException(throwable);
                }
            } else {
                return;
            }
        }
    }
}