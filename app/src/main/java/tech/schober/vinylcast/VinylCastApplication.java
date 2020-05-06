package tech.schober.vinylcast;

import android.app.Application;

import androidx.preference.PreferenceManager;

import tech.schober.vinylcast.audio.NativeAudioEngine;

public class VinylCastApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // This static call will reset default values only on the first ever read
        PreferenceManager.setDefaultValues(getBaseContext(), R.xml.preferences, false);

        NativeAudioEngine.create();
    }
}
