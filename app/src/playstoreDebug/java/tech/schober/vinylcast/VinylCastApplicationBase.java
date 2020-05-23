package tech.schober.vinylcast;

import android.app.Application;
import android.os.StrictMode;

import com.instabug.library.Instabug;
import com.instabug.library.invocation.InstabugInvocationEvent;

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

        new Instabug.Builder(this, "40d0b4f618b8d8eacd26fceff152a822")
                .setInvocationEvents(InstabugInvocationEvent.NONE)
                .build();
    }
}