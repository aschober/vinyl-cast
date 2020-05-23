package tech.schober.vinylcast;

import android.app.Application;
import android.util.Log;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

public class VinylCastApplicationBase extends Application {
    private static final String TAG = "VinylCastApplicationHelper";

    @Override
    public void onCreate() {
        super.onCreate();

        new Instabug.Builder(this, "479cfe4581ddfc3fe46246e0e5611773")
                .setInvocationEvents(InstabugInvocationEvent.NONE)
                .build();
    }
}