package tech.schober.vinylcast;

import android.app.Application;

import tech.schober.vinylcast.audio.NativeAudioEngine;

public class VinylCastApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NativeAudioEngine.create();
    }
}
