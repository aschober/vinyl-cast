package tech.schober.vinylcast.ui.main;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.app.MediaRouteDialogFactory;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import tech.schober.vinylcast.R;
import tech.schober.vinylcast.audio.AudioVisualizer;
import tech.schober.vinylcast.ui.VinylCastActivity;
import tech.schober.vinylcast.ui.settings.SettingsActivity;

public class MainActivity extends VinylCastActivity implements AudioVisualizer.AudioVisualizerListener {
    private static final String TAG = "MainActivity";

    private static final int RECORD_REQUEST_CODE = 1;

    private static final Set<Integer> RECORDING_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_MIC));
    private static final Set<Integer> PLAYBACK_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));

    private TextView statusText;
    private TextView albumTextView;
    private TextView trackTextView;
    private TextView artistTextView;
    private ImageView coverArtImage;

    private PlayStopView playStopButton;
    private ImageButton startRecordingButton;
    private ObjectAnimator recordingButtonAnimator;
    private long recordingButtonAnimationTime = 0;

    private BarGraphView audioVisualizer;

    private boolean isServiceRecording = false;

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
        startRecordingButton.setOnClickListener(v -> startRecordingButtonClicked());

        playStopButton = findViewById(R.id.play_pause_view);
        playStopButton.setOnClickListener(v -> {
            playStopButton.toggle();
            startRecordingButtonClicked();
        });

        audioVisualizer = findViewById(R.id.audio_visualizer);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
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
        super.onStop();
    }

    protected void bindVinylCastService() {
        if (!isServiceBound()) {
            setStatus(getString(R.string.status_preparing), true);
        }
        super.bindVinylCastService();
    }

    protected void unbindVinylCastService() {
        if (isServiceBound()) {
            setStatus(getString(R.string.status_stopped), true);
        }
        super.unbindVinylCastService();
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        Log.d(TAG, "onServiceConnected");
        super.onServiceConnected(className, service);
        binder.setMainActivity(MainActivity.this);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        Log.d(TAG, "onServiceDisconnected");
        super.onServiceDisconnected(className);
    }

    private void startRecordingButtonClicked() {
        if (!isServiceRecording) {
            if (!hasRecordAudioPermission()) {
                requestRecordAudioPermission();
            } else if (!hasBuiltInDevicesSelected((dialog, which) -> startVinylCast())) {
                startVinylCast();
            }
        } else {
            stopVinylCast();
        }
    }

    // TODO: update check for built-in devices based on preferences
    private boolean hasBuiltInDevicesSelected(DialogInterface.OnClickListener positiveClickListener) {
//        final AudioDeviceListEntry selectedPlaybackDevice = (AudioDeviceListEntry)playbackDeviceSpinner.getSelectedItem();
//        final AudioDeviceListEntry selectedRecordingDevice = (AudioDeviceListEntry)recordingDeviceSpinner.getSelectedItem();
//
//
//        if ((RECORDING_DEVICES_BUILTIN.contains(selectedRecordingDevice.getType())) && (PLAYBACK_DEVICES_BUILTIN.contains(selectedPlaybackDevice.getType()))) {
//            new AlertDialog.Builder(this)
//                    .setTitle(R.string.alert_builtin_warning_title)
//                    .setMessage(R.string.alert_builtin_warning_message)
//                    // Specifying a listener allows you to take an action before dismissing the dialog.
//                    // The dialog is automatically dismissed when a dialog button is clicked.
//                    .setPositiveButton(android.R.string.yes, positiveClickListener)
//                    // A null listener allows the button to dismiss the dialog and take no further action.
//                    .setNegativeButton(android.R.string.no, null)
//                    .setIcon(android.R.drawable.ic_dialog_alert)
//                    .show();
//            return true;
//        }
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
                    setStatus(getString(R.string.status_record_audio_denied), true);
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
        f.show(fm, "androidx.mediarouter:MediaRouteChooserDialogFragment");
    }

    private void startVinylCast() {
        if (!isServiceRecording) {
            binder.start();
        } else {
            Log.d(TAG, "VinylCastService is already running");
        }
    }

    private void stopVinylCast() {
        if (isServiceRecording) {
            binder.stop();
        } else {
            Log.d(TAG, "VinylCastService is not running");
        }
    }

    private void animateRecord(boolean animate) {
        if (animate) {
            if (recordingButtonAnimator == null) {
                recordingButtonAnimator = ObjectAnimator.ofFloat(startRecordingButton, "rotation", 0, 360);
                recordingButtonAnimator.setDuration(1800); // ~33.33 RPM
                recordingButtonAnimator.setInterpolator(new LinearInterpolator());
                recordingButtonAnimator.setRepeatCount(Animation.INFINITE);
                recordingButtonAnimator.start();
            }
            // looks better if we go back in time a bit
            recordingButtonAnimator.setCurrentPlayTime(recordingButtonAnimator.getCurrentPlayTime()-500);
            recordingButtonAnimator.resume();
        } else {
            if (recordingButtonAnimator != null) {
                recordingButtonAnimator.pause();
            }
        }
    }

    /**
     * Public helper to set the recording state
     */
    public void updateRecordingState(boolean isRecording) {
        isServiceRecording = isRecording;
        if (isRecording) {
            playStopButton.change(false);
            animateRecord(true);
        } else {
            playStopButton.change(true);
            audioVisualizer.clearData();
            animateRecord(false);
        }
    }

    /**
     * Public helper to set the status message
     */
    public void setStatus(String statusMessage, boolean clearStatus) {
        runOnUiThread(new UpdateStatusRunnable(statusMessage, clearStatus));
    }

    /**
     * Callback from AudioVisualizer with spectrum amplitude data
     * @param spectrumAmpDB
     */
    @Override
    public void onAudioVisualizerData(double[] spectrumAmpDB) {
        audioVisualizer.setData(spectrumAmpDB);
    }

    class UpdateStatusRunnable implements Runnable {
        String status;
        boolean clearStatus;

        UpdateStatusRunnable(String status, boolean clearStatus) {
            this.status = status;
            this.clearStatus = clearStatus;
        }

        @Override
        public void run() {
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

//    public void updateMetadataFields(String trackTitle, String albumTitle, String artist, String coverArtUrl) {
//
//        currentTrack = trackTitle;
//        currentAlbum = albumTitle;
//        currentArtist = artist;
//
//        if (trackTitle == null) {
//            //coverArtImage.setVisibility(View.GONE);
//            //albumTextView.setVisibility(View.GONE);
//            //trackTextView.setVisibility(View.GONE);
//            // Use the artist text field to display the error message
//            //artistText.setText("Music Not Identified");
//        } else {
//            // populate the display tow with metadata and cover art
//            albumTextView.setText(albumTitle);
//            artistTextView.setText(artist);
//            trackTextView.setText(trackTitle);
//
//            binder.updateMediaSessionMetadata(trackTitle, albumTitle, artist, null);
//            binder.loadAndDisplayCoverArt(coverArtUrl, coverArtImage);
//        }
//    }

    public void setCoverArt(Drawable coverArt, ImageView coverArtImage){
        if (coverArt instanceof BitmapDrawable) {
            binder.updateMediaSessionMetadata(currentTrack, currentAlbum, currentArtist, (BitmapDrawable) coverArt);
        }
        runOnUiThread(new SetCoverArtRunnable(coverArt, coverArtImage));
    }

    class SetCoverArtRunnable implements Runnable {

        Drawable coverArt;
        ImageView coverArtImage;

        SetCoverArtRunnable(Drawable locCoverArt, ImageView locCoverArtImage) {
            coverArt = locCoverArt;
            coverArtImage = locCoverArtImage;
        }

        @Override
        public void run() {
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



}
