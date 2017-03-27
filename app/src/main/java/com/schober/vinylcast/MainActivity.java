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
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Button mLoopbackButton;

    boolean serviceBound = false;
    MediaRecorderService.MediaRecorderBinder binder = null;

    private HttpServer server;


    //https://github.com/srubin/cs160-audio-examples/blob/master/LoopbackLive/src/com/example/loopbacklive/LoopbackLive.java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupHttpServer();

        // button to initialize loopback
        mLoopbackButton = (Button)findViewById(R.id.loopbackButton);
        mLoopbackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startIntent = new Intent(MainActivity.this, MediaRecorderService.class);

                if (isServiceRunning(MediaRecorderService.class)) {
                    if (serviceBound) {
                        unbindService(serviceConnection);
                        serviceBound = false;
                    }
                    startIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_STOP);
                    mLoopbackButton.setText("Start loopback");
                } else {
                    // start service
                    startIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_START);
                    mLoopbackButton.setText("Stop loopback");

                    if (!serviceBound) {
                        // Bind to LocalService
                        Intent bindIntent = new Intent(MainActivity.this, MediaRecorderService.class);
                        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
                    }
                }

                MainActivity.this.startService(startIntent);
            }
        });
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

    private void setupHttpServer() {
        try {
            server = new HttpServer();
        } catch (IOException e) {
            Log.e(TAG, "Exception creating webserver", e);
        }
    }

    public class HttpServer extends NanoHTTPD {
        private final static int PORT = 5000;

        public HttpServer() throws IOException {
            super(PORT);
            start();
            Log.d(TAG, "Start webserver on port: " + PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String path = session.getUri();

            if (path.equals("/vinyl")) {
                Log.d(TAG, "Received Request: " + session);
                if (binder == null) {
                    Log.e(TAG, "MediaRecorderService Binder not available");
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
                }

                InputStream inputStream = binder.getInputStream();
                if (inputStream == null) {
                    Log.e(TAG, "Input Stream not available");
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
                }

                return newChunkedResponse(Response.Status.OK, "audio/aac", inputStream);
            } else {
                return super.serve(session);
            }
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
