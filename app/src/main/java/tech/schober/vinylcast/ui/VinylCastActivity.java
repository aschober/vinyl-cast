package tech.schober.vinylcast.ui;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;

import tech.schober.vinylcast.VinylCastService;

public abstract class VinylCastActivity extends AppCompatActivity implements ServiceConnection {
    private static final String TAG = "VinyCastActivity";

    private boolean serviceBound = false;
    protected VinylCastService.VinylCastBinder binder = null;

    @Override
    protected void onStart() {
        super.onStart();
        // bind to VinylCastService
        this.bindVinylCastService();
    }

    @Override
    protected void onStop() {
        // Unbind from VinylCastService
        this.unbindVinylCastService();
        super.onStop();
    }

    protected void bindVinylCastService() {
        if (!serviceBound) {
            Intent bindIntent = new Intent(this, VinylCastService.class);
            bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
        }
    }

    protected void unbindVinylCastService() {
        if (serviceBound) {
            unbindService(this);
            serviceBound = false;
        }
    }

    protected boolean isServiceBound() {
        return serviceBound;
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        // We've bound to VinylCastService, cast the IBinder and get VinylCastService instance
        binder = (VinylCastService.VinylCastBinder) service;
        serviceBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
        binder = null;
        serviceBound = false;
    }

    public boolean isVinylCastServiceRunning() {
        /* from http://stackoverflow.com/a/5921190
         * used to check if VinylCastService is already running
         */
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (VinylCastService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
