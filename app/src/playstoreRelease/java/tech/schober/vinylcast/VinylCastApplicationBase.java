package tech.schober.vinylcast;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

public class VinylCastApplicationBase extends Application {
    private static final String TAG = "VinylCastApplicationHelper";

    @Override
    public void onCreate() {
        super.onCreate();

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