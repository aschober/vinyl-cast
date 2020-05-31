package tech.schober.vinylcast;

import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import tech.schober.vinylcast.audio.NativeAudioEngine;
import timber.log.Timber;

public class VinylCastApplication extends VinylCastApplicationBase {

    private SessionManager castSessionManager;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        Timber.plant(new CrashReportingTree());

        // This static call will reset default values only on the first ever read. Also according
        // to StrictMode, it's slow due to disk reads on Main Thread.
        PreferenceManager.setDefaultValues(getBaseContext(), R.xml.preferences, false);

        // get Cast session manager here in Application as according to StrictMode it's slow due to
        // performing disk reads on Main Thread and only needs to be fetched once anyway.
        castSessionManager = CastContext.getSharedInstance(this).getSessionManager();

        NativeAudioEngine.create();
    }

    public SessionManager getCastSessionManager() {
        return castSessionManager;
    }

    private class CrashReportingTree extends Timber.Tree {
        @Override
        protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable throwable) {
            if (priority == Log.ERROR || priority == Log.DEBUG) {
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
