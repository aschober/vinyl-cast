package tech.schober.vinylcast.ui.main;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.app.MediaRouteDialogFactory;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

import tech.schober.vinylcast.R;
import tech.schober.vinylcast.VinylCastService;
import tech.schober.vinylcast.audio.AudioVisualizer;
import tech.schober.vinylcast.ui.VinylCastActivity;
import tech.schober.vinylcast.ui.settings.SettingsActivity;

import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_AUDIO_CONVERT_FAILED;
import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_AUDIO_FOCUS_FAILED;
import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_AUDIO_RECORD_FAILED;
import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_HTTP_SERVER_FAILED;
import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_PERMISSION_DENIED;
import static tech.schober.vinylcast.VinylCastService.STATUS_ERROR_UNKNOWN;
import static tech.schober.vinylcast.VinylCastService.STATUS_PREPARING;
import static tech.schober.vinylcast.VinylCastService.STATUS_READY;
import static tech.schober.vinylcast.VinylCastService.STATUS_RECORDING;
import static tech.schober.vinylcast.VinylCastService.STATUS_STOPPED;

public class MainActivity extends VinylCastActivity implements VinylCastService.VinylCastServiceListener, AudioVisualizer.AudioVisualizerListener {
    private static final String TAG = "MainActivity";

    private static final int RECORD_REQUEST_CODE = 1;

    private TextView statusText;

    private PlayStopView playStopButton;
    private ImageButton startRecordingButton;
    private ObjectAnimator recordingButtonAnimator;

    private BarGraphView barGraphView;

    private boolean isServiceRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        CastContext.getSharedInstance(this).getSessionManager();

        statusText = findViewById(R.id.statusText);

        // button to begin audio record
        startRecordingButton = findViewById(R.id.startRecordingButton);
        startRecordingButton.setOnClickListener(v -> startRecordingButtonClicked());

        playStopButton = findViewById(R.id.play_stop_view);
        playStopButton.setOnClickListener(v -> {
            playStopButton.toggle();
            startRecordingButtonClicked();
        });

        barGraphView = findViewById(R.id.audio_visualizer);
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
            onStatusUpdate(STATUS_PREPARING);
        }
        super.bindVinylCastService();
    }

    protected void unbindVinylCastService() {
        if (isServiceBound()) {
            onStatusUpdate(STATUS_STOPPED);
        }
        super.unbindVinylCastService();
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        Log.d(TAG, "onServiceConnected");
        super.onServiceConnected(className, service);
        binder.addVinylCastServiceListener(this);
        binder.addAudioVisualizerListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        Log.d(TAG, "onServiceDisconnected");
        binder.removeVinylCastServiceListener(this);
        binder.removeAudioVisualizerListener(this);
        super.onServiceDisconnected(className);
    }

    private void startRecordingButtonClicked() {
        if (!isServiceRecording) {
            if (!hasRecordAudioPermission()) {
                requestRecordAudioPermission();
            } else {
                startVinylCast();
            }
        } else {
            stopVinylCast();
        }
    }

    private boolean hasRecordAudioPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        return hasPermission;
    }

    private void requestRecordAudioPermission() {
        final String requiredPermission = Manifest.permission.RECORD_AUDIO;

        // If the user previously denied this permission then show a message explaining why this permission is needed
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, requiredPermission)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.alert_permissions_message)
                    .setTitle(R.string.alert_permissions_title)
                    .setPositiveButton(R.string.button_ok, (dialog, id) -> {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{requiredPermission}, RECORD_REQUEST_CODE);
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
                    onStatusUpdate(STATUS_ERROR_PERMISSION_DENIED);
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
            // Use intent to start VinylCastService so the service lifecycle is independent from
            // number of bound clients
            // https://developer.android.com/guide/components/bound-services#Lifecycle
            Intent startIntent = new Intent(MainActivity.this, VinylCastService.class);
            startIntent.setAction(VinylCastService.ACTION_START_RECORDING);
            MainActivity.this.startService(startIntent);
        } else {
            Log.d(TAG, "VinylCastService is already running");
        }
    }

    private void stopVinylCast() {
        if (isServiceRecording) {
            Intent stopIntent = new Intent(MainActivity.this, VinylCastService.class);
            stopIntent.setAction(VinylCastService.ACTION_STOP_RECORDING);
            MainActivity.this.startService(stopIntent);
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
            recordingButtonAnimator.setCurrentPlayTime(recordingButtonAnimator.getCurrentPlayTime()-300);
            recordingButtonAnimator.resume();
        } else {
            if (recordingButtonAnimator != null) {
                recordingButtonAnimator.pause();
            }
        }
    }

    /**
     * Callback from AudioVisualizer with spectrum amplitude data
     * @param spectrumAmpDB
     */
    @Override
    public void onAudioVisualizerData(double[] spectrumAmpDB) {
        barGraphView.setData(spectrumAmpDB);
    }

    @Override
    public void onStatusUpdate(@VinylCastService.StatusCode int statusCode) {
        boolean isRecording = false;
        String statusMessage;
        switch (statusCode) {
            case STATUS_PREPARING:
                statusMessage = getString(R.string.status_preparing);
                break;
            case STATUS_READY:
                statusMessage = getString(R.string.status_ready);
                break;
            case STATUS_RECORDING:
                isRecording = true;
                statusMessage = getString(R.string.status_recording) + "\n" + binder.getHttpStreamServer().getStreamUrl();
                break;
            case STATUS_STOPPED:
                statusMessage = getString(R.string.status_stopped);
                break;
            case STATUS_ERROR_PERMISSION_DENIED:
                statusMessage = getString(R.string.status_error_record_audio_denied);
                break;
            case STATUS_ERROR_AUDIO_FOCUS_FAILED:
                statusMessage = getString(R.string.status_error_audio_focus_failed);
                break;
            case STATUS_ERROR_AUDIO_RECORD_FAILED:
                statusMessage = getString(R.string.status_error_audio_record_failed);
                break;
            case STATUS_ERROR_AUDIO_CONVERT_FAILED:
                statusMessage = getString(R.string.status_error_audio_convert_failed);
                break;
            case STATUS_ERROR_HTTP_SERVER_FAILED:
                statusMessage = getString(R.string.status_error_http_server_failed);
                break;
            case STATUS_ERROR_UNKNOWN:
            default:
                statusMessage = getString(R.string.status_error_unknown);
                break;
        }

        updateRecordingState(isRecording);
        runOnUiThread(new UpdateStatusRunnable(statusMessage));
    }

    /**
     * helper to set the recording state
     */
    private void updateRecordingState(boolean isRecording) {
        isServiceRecording = isRecording;
        if (isRecording) {
            playStopButton.change(false);
            animateRecord(true);
        } else {
            playStopButton.change(true);
            barGraphView.clearData();
            animateRecord(false);
        }
    }

    /**
     *  helper to set the status message on main thread
     */
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
}
