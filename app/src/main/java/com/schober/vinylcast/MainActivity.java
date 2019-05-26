package com.schober.vinylcast;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.app.MediaRouteDialogFactory;

import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.schober.vinylcast.service.MediaRecorderService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int RECORD_REQUEST_CODE = 1;

    private TextView statusText;
    private TextView albumTextView;
    private TextView trackTextView;
    private TextView artistTextView;
    private ImageView coverArtImage;

    private ImageButton startRecordingButton;
    private Animation buttonAnimation;
    private boolean buttonClickedNoCast;

    boolean serviceBound = false;
    MediaRecorderService.MediaRecorderBinder binder = null;

    private SessionManager castSessionManager;
    private CastSession castSession;
    private SessionManagerListener sessionManagerListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        castSessionManager = CastContext.getSharedInstance(this).getSessionManager();

        statusText = findViewById(R.id.statusText);
        coverArtImage = findViewById(R.id.coverArtImage);
        albumTextView = findViewById(R.id.albumName);
        trackTextView = findViewById(R.id.trackTitle);
        artistTextView = findViewById(R.id.artistName);

        // button to initialize audio
        startRecordingButton = findViewById(R.id.startRecordingButton);
        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isServiceRunning(MediaRecorderService.class)) {
                    castSession = castSessionManager.getCurrentCastSession();

                    if (castSession == null && false) {
                        openCastDialog();
                        buttonClickedNoCast = true;
                    }
                    else if (hasRecordAudioPermission()) {
                        startRecording();
                    } else {
                        requestRecordAudioPermission();
                    }
                } else {
                    stopRecording();
                }
            }
        });

        buttonAnimation = new RotateAnimation(0, 359, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        buttonAnimation.setFillAfter(true);
        buttonAnimation.setDuration(1000);
        buttonAnimation.setInterpolator(new LinearInterpolator());
        buttonAnimation.setRepeatCount(Animation.INFINITE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        castSession = castSessionManager.getCurrentCastSession();

        if (sessionManagerListener == null) {
            sessionManagerListener = new SessionManagerListenerImpl();
            castSessionManager.addSessionManagerListener(sessionManagerListener);
        }

        if (isServiceRunning(MediaRecorderService.class)) {
            animateRecord(true);
        } else {
            animateRecord(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        animateRecord(false);
        if (sessionManagerListener != null) {
            castSessionManager.removeSessionManagerListener(sessionManagerListener);
            sessionManagerListener = null;
        }
        castSession = null;
        buttonClickedNoCast = false;
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

    private boolean hasRecordAudioPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Has RECORD_AUDIO permission? " + hasPermission);
        return hasPermission;
    }

    private void requestRecordAudioPermission() {
        final String requiredPermission = Manifest.permission.RECORD_AUDIO;

        // If the user previously denied this permission then show a message explaining why this permission is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Permission to access the microphone is required for this app to record audio.").setTitle("Permission required");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.i(TAG, "Clicked");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{requiredPermission}, RECORD_REQUEST_CODE);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        // request the permission.
        ActivityCompat.requestPermissions(this, new String[]{requiredPermission}, RECORD_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // This method is called when the user responds to the permissions dialog
        switch (requestCode) {
            case RECORD_REQUEST_CODE: {
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                    startRecording();
                }
                return;
            }
        }
    }

    private void openCastDialog() {
        final FragmentManager fm = getSupportFragmentManager();
        MediaRouteChooserDialogFragment f = MediaRouteDialogFactory.getDefault().onCreateChooserDialogFragment();
        f.setRouteSelector(CastContext.getSharedInstance(this).getMergedSelector());
        f.show(fm, "android.support.v7.mediarouter:MediaRouteChooserDialogFragment");
    }

    private void startRecording() {
        if (!isServiceRunning(MediaRecorderService.class)) {
            if (!serviceBound) {
                // Bind to LocalService
                Intent bindIntent = new Intent(MainActivity.this, MediaRecorderService.class);
                bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
            Intent startIntent = new Intent(MainActivity.this, MediaRecorderService.class);
            startIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_START);
            MainActivity.this.startService(startIntent);
            animateRecord(true);
        } else {
            Log.d(TAG, "Service is already running");
        }
    }

    private void stopRecording() {
        if (isServiceRunning(MediaRecorderService.class)) {
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
            }
            animateRecord(false);
            Intent stopIntent = new Intent(MainActivity.this, MediaRecorderService.class);
            stopIntent.putExtra(MediaRecorderService.REQUEST_TYPE, MediaRecorderService.REQUEST_TYPE_STOP);
            MainActivity.this.startService(stopIntent);
        } else {
            Log.d(TAG, "Service is not running");
        }
    }

    private void animateRecord(boolean animate) {
        if (animate) {
            startRecordingButton.startAnimation(buttonAnimation);
        } else {
            startRecordingButton.clearAnimation();
        }
    }

    /**
     * Helper to set the application status message
     */
    public void setStatus(String statusMessage, boolean clearStatus) {
        runOnUiThread(new UpdateStatusRunnable(statusMessage, clearStatus));
    }

    class UpdateStatusRunnable implements Runnable {

        boolean clearStatus;
        String status;

        UpdateStatusRunnable(String status, boolean clearStatus) {
            this.status = status;
            this.clearStatus = clearStatus;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            statusText.setVisibility(View.VISIBLE);
            if (clearStatus) {
                statusText.setText(status);
            } else {
                statusText.setText(statusText.getText() + "\n" + status);
            }
        }
    }

    /**
     * Adds the provided album as a new row on the application display
     */
    private String currentTrack;
    private String currentAlbum;
    private String currentArtist;

    public void updateMetaDataFields(String trackTitle, String albumTitle, String artist, String coverArtUrl) {

        currentTrack = trackTitle;
        currentAlbum = albumTitle;
        currentArtist = artist;

        if (albumTitle == null) {
            //coverArtImage.setVisibility(View.GONE);
            //albumTextView.setVisibility(View.GONE);
            //trackTextView.setVisibility(View.GONE);
            // Use the artist text field to display the error message
            //artistText.setText("Music Not Identified");
        } else {
            // populate the display tow with metadata and cover art
            albumTextView.setText(albumTitle);
            artistTextView.setText(artist);
            trackTextView.setText(trackTitle);

            binder.updateMetadata(trackTitle, albumTitle, artist, null);
            binder.loadAndDisplayCoverArt(coverArtUrl, coverArtImage);
        }
    }

    public void setCoverArt(Drawable coverArt, ImageView coverArtImage){
        if (coverArt instanceof BitmapDrawable) {
            binder.updateMetadata(currentTrack, currentAlbum, currentArtist, (BitmapDrawable) coverArt);
        }
        runOnUiThread(new SetCoverArtRunnable(coverArt, coverArtImage));
    }

    class SetCoverArtRunnable implements Runnable {

        Drawable coverArt;
        ImageView coverArtImage;

        SetCoverArtRunnable( Drawable locCoverArt, ImageView locCoverArtImage) {
            coverArt = locCoverArt;
            coverArtImage = locCoverArtImage;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            coverArtImage.setImageDrawable(coverArt);
        }
    }

    /**
     * Helper to clear the results from the application display
     */
    public void clearMetadata() {
        runOnUiThread(new ClearMetadataRunnable());
    }

    class ClearMetadataRunnable implements Runnable {
        @Override
        public void run() {
            albumTextView.setText("");
            artistTextView.setText("");
            trackTextView.setText("");
            coverArtImage.setImageDrawable(null);
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
            binder.setActivity(MainActivity.this);
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

    private class SessionManagerListenerImpl implements SessionManagerListener {
        @Override
        public void onSessionStarting(Session session) {
            Log.d(TAG, "Cast onSessionStarting");
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            Log.d(TAG, "Cast onSessionStarted");
            if (buttonClickedNoCast) {
                startRecording();
            }
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
            Log.d(TAG, "Cast onSessionStartFailed");
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(TAG, "Cast onSessionEnding");
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            Log.d(TAG, "Cast onSessionResumed");
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
            Log.d(TAG, "Cast onSessionResumeFailed");
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            Log.d(TAG, "Cast onSessionSuspended");
        }

        @Override
        public void onSessionEnded(Session session, int error) {
            Log.d(TAG, "Cast onSessionEnded");
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            Log.d(TAG, "Cast onSessionResuming");
        }
    }
}
