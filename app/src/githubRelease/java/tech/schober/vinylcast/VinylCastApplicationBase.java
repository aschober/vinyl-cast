package tech.schober.vinylcast;

import android.app.Application;

public class VinylCastApplicationBase extends Application {
    private static final String TAG = "VinylCastApplicationBase";

    @Override
    public void onCreate() {
        super.onCreate();
        // no-op
    }
}