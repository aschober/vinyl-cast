package com.schober.vinylcast;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Button mLoopbackButton;

    boolean serviceBound = false;
    MediaRecorderService.MediaRecorderBinder binder = null;

    private SessionManager castSessionManager;
    private CastSession castSession;

    //https://github.com/srubin/cs160-audio-examples/blob/master/LoopbackLive/src/com/example/loopbacklive/LoopbackLive.java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        castSessionManager = CastContext.getSharedInstance(this).getSessionManager();

        // button to initialize loopback
        mLoopbackButton = (Button)findViewById(R.id.loopbackButton);
        mLoopbackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isServiceRunning(MediaRecorderService.class)) {
                    startLoopback();
                } else {
                    stopLoopback();
                }
            }
        });
    }

    public void startLoopback() {
        if (!isServiceRunning(MediaRecorderService.class)) {
            if (!serviceBound) {
                // Bind to LocalService
                Intent bindIntent = new Intent(MainActivity.this, MediaRecorderService.class);
                bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }

            mLoopbackButton.setText("Stop loopback");

            Intent startIntent = new Intent(MainActivity.this, MediaRecorderService.class);
            startIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_START);
            MainActivity.this.startService(startIntent);

        } else {
            Log.d(TAG, "Service is already running");
        }
    }

    public void stopLoopback() {
        if (isServiceRunning(MediaRecorderService.class)) {
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
            }

            mLoopbackButton.setText("Start loopback");

            Intent stopIntent = new Intent(MainActivity.this, MediaRecorderService.class);
            stopIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_STOP);
            MainActivity.this.startService(stopIntent);

        } else {
            Log.d(TAG, "Service is not running");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                menu,
                R.id.media_route_menu_item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        castSession = castSessionManager.getCurrentCastSession();
    }

    @Override
    protected void onPause() {
        super.onPause();
        castSession = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "onServiceConnected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            binder = (MediaRecorderService.MediaRecorderBinder) service;
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisconnected");
            binder = null;
            serviceBound = false;
        }
    };

    private boolean isServiceRunning(Class<?> serviceClass) {
    	/* from http://stackoverflow.com/a/5921190
    	 * used to check if BackgroundVideoRecorder Service is already running
    	 * when user tries to log in.
    	 */
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


}
