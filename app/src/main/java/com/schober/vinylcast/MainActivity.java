package com.schober.vinylcast;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.app.MediaRouteDialogFactory;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.sample.audio_device.AudioDeviceListEntry;
import com.google.sample.audio_device.AudioDeviceSpinner;
import com.schober.vinylcast.audio.NativeAudioEngine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int RECORD_REQUEST_CODE = 1;

    private static final Set<Integer> RECORDING_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_MIC));
    private static final Set<Integer> PLAYBACK_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));

    private TextView statusText;
    private TextView albumTextView;
    private TextView trackTextView;
    private TextView artistTextView;
    private ImageView coverArtImage;

    private ImageButton startRecordingButton;
    private ImageView startRecordingIndicator;
    private Animation recordingButtonAnimation;

    private AudioDeviceSpinner recordingDeviceSpinner;
    private AudioDeviceSpinner playbackDeviceSpinner;

    private boolean serviceBound = false;
    private boolean isServiceRecording = false;
    VinylCastService.VinylCastBinder binder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CastContext.getSharedInstance(this).getSessionManager();

        statusText = findViewById(R.id.statusText);
        coverArtImage = findViewById(R.id.coverArtImage);
        albumTextView = findViewById(R.id.albumName);
        trackTextView = findViewById(R.id.trackTitle);
        artistTextView = findViewById(R.id.artistName);

        // button to begin audio record
        startRecordingButton = findViewById(R.id.startRecordingButton);
        startRecordingIndicator = findViewById(R.id.startRecordingIndicator);
        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecordingButtonClicked();
            }
        });

        recordingButtonAnimation = new RotateAnimation(0, 359.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        recordingButtonAnimation.setFillAfter(true);
        recordingButtonAnimation.setDuration(1800); // ~33.33 RPM
        recordingButtonAnimation.setInterpolator(new LinearInterpolator());
        recordingButtonAnimation.setRepeatCount(Animation.INFINITE);

        recordingDeviceSpinner = findViewById(R.id.recording_devices_spinner);
        recordingDeviceSpinner.setDirectionType(AudioManager.GET_DEVICES_INPUTS);

        playbackDeviceSpinner = findViewById(R.id.playback_devices_spinner);
        playbackDeviceSpinner.setDirectionType(AudioManager.GET_DEVICES_OUTPUTS);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        // bind to VinylCastService
        bindService();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        // Unbind from VinylCastService
        unbindService();
        super.onStop();
    }

    private void startRecordingButtonClicked() {
        if (!isServiceRecording) {
            if (!hasRecordAudioPermission()) {
                requestRecordAudioPermission();
            } else if (!hasBuiltInDevicesSelected(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startRecording();
                }
            })) {
                startRecording();
            }
        } else {
            stopRecording();
        }
    }

    private boolean hasBuiltInDevicesSelected(DialogInterface.OnClickListener positiveClickListener) {
        final AudioDeviceListEntry selectedPlaybackDevice = (AudioDeviceListEntry)playbackDeviceSpinner.getSelectedItem();
        final AudioDeviceListEntry selectedRecordingDevice = (AudioDeviceListEntry)recordingDeviceSpinner.getSelectedItem();


        if ((RECORDING_DEVICES_BUILTIN.contains(selectedRecordingDevice.getType())) && (PLAYBACK_DEVICES_BUILTIN.contains(selectedPlaybackDevice.getType()))) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.alert_builtin_warning_title)
                    .setMessage(R.string.alert_builtin_warning_message)
                    // Specifying a listener allows you to take an action before dismissing the dialog.
                    // The dialog is automatically dismissed when a dialog button is clicked.
                    .setPositiveButton(android.R.string.yes, positiveClickListener)
                    // A null listener allows the button to dismiss the dialog and take no further action.
                    .setNegativeButton(android.R.string.no, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return true;
        }
        return false;
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
            builder.setMessage(R.string.alert_permissions_message)
                    .setTitle(R.string.alert_permissions_title)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{requiredPermission}, RECORD_REQUEST_CODE);
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            // request the permission.
            ActivityCompat.requestPermissions(this, new String[]{requiredPermission}, RECORD_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // This method is called when the user responds to the permissions dialog
        switch (requestCode) {
            case RECORD_REQUEST_CODE: {
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user");
                    setStatus(getString(R.string.status_record_audio_denied));
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                    startRecordingButtonClicked();
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

    private void bindService() {
        if (!serviceBound) {
            setStatus(getString(R.string.status_preparing));
            Intent bindIntent = new Intent(MainActivity.this, VinylCastService.class);
            bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindService() {
        if (serviceBound) {
            setStatus(getString(R.string.status_stopped));
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void startRecording() {
        if (!isServiceRecording) {
            AudioDeviceListEntry selectedPlaybackDevice = (AudioDeviceListEntry)playbackDeviceSpinner.getSelectedItem();
            NativeAudioEngine.setPlaybackDeviceId(selectedPlaybackDevice.getId());
            AudioDeviceListEntry selectedRecordingDevice = (AudioDeviceListEntry)recordingDeviceSpinner.getSelectedItem();
            NativeAudioEngine.setRecordingDeviceId(selectedRecordingDevice.getId());

            Intent startIntent = new Intent(MainActivity.this, VinylCastService.class);
            startIntent.setAction(VinylCastService.ACTION_START_RECORDING);
            MainActivity.this.startService(startIntent);
        } else {
            Log.d(TAG, "VinylCastService is already running");
        }
    }

    private void stopRecording() {
        if (isServiceRecording) {
            Intent stopIntent = new Intent(MainActivity.this, VinylCastService.class);
            stopIntent.setAction(VinylCastService.ACTION_STOP_RECORDING);
            MainActivity.this.startService(stopIntent);
        } else {
            Log.d(TAG, "VinylCastService is not running");
        }
    }

    private void setSpinnersEnabled(boolean isEnabled){
        recordingDeviceSpinner.setEnabled(isEnabled);
        playbackDeviceSpinner.setEnabled(isEnabled);
    }

    private void animateRecord(boolean animate) {
        if (animate) {
            startRecordingButton.startAnimation(recordingButtonAnimation);
        } else {
            startRecordingButton.clearAnimation();
        }
    }

    /**
     * Public helper to set the recording state
     */
    public void updateRecordingState(boolean isRecording) {
        isServiceRecording = isRecording;
        if (isRecording) {
            startRecordingIndicator.setImageResource(R.drawable.ic_media_stop_dark);
            animateRecord(true);
            setSpinnersEnabled(false);
        } else {
            startRecordingIndicator.setImageResource(R.drawable.ic_media_play_dark);
            animateRecord(false);
            setSpinnersEnabled(true);
        }
    }

    /**
     * Public helper to set the status message
     */
    public void setStatus(String statusMessage) {
        runOnUiThread(new UpdateStatusRunnable(statusMessage));
    }

    class UpdateStatusRunnable implements Runnable {
        String status;

        UpdateStatusRunnable(String status) {
            this.status = status;
        }

        @Override
        public void run() {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(status);
        }
    }

    /**
     *  Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "onServiceConnected");
            // We've bound to VinylCastService, cast the IBinder and get VinylCastService instance
            binder = (VinylCastService.VinylCastBinder) service;
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
}
