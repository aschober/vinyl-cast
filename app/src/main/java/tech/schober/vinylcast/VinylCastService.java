package tech.schober.vinylcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import tech.schober.vinylcast.audio.AudioRecordStreamProvider;
import tech.schober.vinylcast.audio.AudioStreamProvider;
import tech.schober.vinylcast.audio.AudioVisualizer;
import tech.schober.vinylcast.audio.ConvertAudioStreamProvider;
import tech.schober.vinylcast.server.HttpStreamServer;
import tech.schober.vinylcast.server.HttpStreamServerImpl;
import tech.schober.vinylcast.ui.main.MainActivity;
import tech.schober.vinylcast.utils.Helpers;

import static tech.schober.vinylcast.audio.AudioStreamProvider.AUDIO_ENCODING_AAC;

public class VinylCastService extends MediaBrowserServiceCompat {
    private static final String TAG = "VinylCastService";

    private static final String NOTIFICATION_CHANNEL_ID = "tech.schober.vinylcast.CHANNEL_ACTIVELY_RECORDING";
    private static final int NOTIFICATION_ID = 4242;

    public static final String ACTION_START_RECORDING = "tech.schober.vinylcast.ACTION_START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "tech.schober.vinylcast.ACTION_STOP_RECORDING";

    private static final int AUDIO_STREAM_BUFFER_SIZE = 8192;
    private static final int AUDIO_VISUALIZER_FFT_LENGTH = 256;
    private static final int AUDIO_VISUALIZER_FFT_BINS = 16;

    private final IBinder binder = new VinylCastBinder();
    private MainActivity mainActivity;

    private AudioRecordStreamProvider audioRecordStreamProvider;

    private Thread convertAudioThread;
    private ConvertAudioStreamProvider convertAudioStreamProvider;

    private HttpStreamServer httpStreamServer;
    private AudioStreamProvider httpStreamProvider;

    private AudioVisualizer audioVisualizer;

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private MediaSessionCompat mediaSession;
    private SessionManagerListener castSessionManagerListener;
    private BecomingNoisyReceiver becomingNoisyReceiver;
    private IntentFilter becomingNoisyIntentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class VinylCastBinder extends Binder {
        public boolean isRecording() {
            return VinylCastService.this.isRecording();
        }

        public MainActivity getMainActivity() {
            return VinylCastService.this.mainActivity;
        }

        public void setMainActivity(MainActivity activity) {
            VinylCastService.this.mainActivity = activity;
            updateMainActivity();
        }

        public void start() {
            VinylCastService.this.engage();
        }

        public void stop() {
            VinylCastService.this.disengage();
        }

        public int getRecorderSampleRate() {
            return audioRecordStreamProvider.getSampleRate();
        }

        public int getRecorderChannelCount() {
            return audioRecordStreamProvider.getChannelCount();
        }

        public String getRecorderAudioApi() {
            return audioRecordStreamProvider.getAudioApi();
        }

        public HttpStreamServer getHttpStreamServer() {
            return httpStreamServer;
        }

        public int getAudioStreamBufferSize() {
            return AUDIO_STREAM_BUFFER_SIZE;
        }

        public InputStream getRawAudioInputStream() {
            if (isRecording()) {
                return audioRecordStreamProvider.getAudioInputStream();
            } else {
                return null;
            }
        }

        public void updateMediaSessionMetadata(String trackTitle, String artist, String album, BitmapDrawable albumImage) {
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackTitle)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1);
            if (albumImage != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumImage.getBitmap());
            }
            mediaSession.setMetadata(metadataBuilder.build());
            mediaSession.setActive(true);

            // Put the service in the foreground, post notification
            Helpers.createStopNotification(mediaSession,
                    VinylCastService.this,
                    VinylCastService.class,
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_ID);

            // currently, only way to update Cast metadata is to re-send URL which causes reload of stream
            //castMedia(trackTitle, artist, album, imageUrl);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();


        // Create a MediaSessionCompat
        mediaSession = new MediaSessionCompat(this, TAG);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP);
        mediaSession.setPlaybackState(stateBuilder.build());

        // MediaSessionCallback() has methods that handle callbacks from a media controller
        mediaSession.setCallback(mediaSessionCallback);

        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (castSessionManagerListener == null) {
            castSessionManagerListener = new CastSessionManagerListener();
            ((VinylCastApplication)getApplication()).getCastSessionManager().addSessionManagerListener(castSessionManagerListener);
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand received action: " + action);

        switch (action) {
            case VinylCastService.ACTION_START_RECORDING:
                engage();
                break;
            case VinylCastService.ACTION_STOP_RECORDING:
                disengage();
                break;
            default:
                MediaButtonReceiver.handleIntent(mediaSession, intent);
                break;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (castSessionManagerListener != null) {
            ((VinylCastApplication)getApplication()).getCastSessionManager().removeSessionManagerListener(castSessionManagerListener);
            castSessionManagerListener = null;
        }
        mediaSession.release();
        mediaSession = null;
        super.onDestroy();
    }

    private void engage() {
        Log.i(TAG, "engage");

        // fetch audio record settings values out of SharedPreferences
        int recordingDeviceId = Helpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_recording_device_id, R.string.prefs_default_recording_device_id);
        int playbackDeviceId = Helpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_local_playback_device_id, R.string.prefs_default_local_playback_device_id);
        @AudioStreamProvider.AudioEncoding int audioEncoding = Helpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_audio_encoding, R.string.prefs_default_audio_encoding);
        boolean lowLatency = PreferenceManager.getDefaultSharedPreferences (this).getBoolean(getString(R.string.prefs_key_low_latency), Boolean.valueOf(getString(R.string.prefs_default_low_latency)));

        if (isPlaybackDeviceSelected() && !requestAudioFocus()) {
            Log.e(TAG, "Failed to get Audio Focus for playback. Stopping VinylCastService...");
            // TODO Error!
            disengage();
            return;
        }

        if (!startAudioRecord(recordingDeviceId, playbackDeviceId, lowLatency)) {
            Log.e(TAG, "Failed to start Audio Record. Stopping VinylCastService...");
            // TODO Error!
            disengage();
            return;
        }

        switch (audioEncoding) {
            case AUDIO_ENCODING_AAC:
                if (!startAudioConverter(audioRecordStreamProvider, AUDIO_STREAM_BUFFER_SIZE)) {
                    Log.e(TAG, "Failed to start Audio Converter. Stopping VinylCastService...");
                    // TODO Error!
                    disengage();
                    return;
                }
                httpStreamProvider = convertAudioStreamProvider;
                break;
            default:
                httpStreamProvider = audioRecordStreamProvider;
                break;
        }

        if (!startHttpServer(httpStreamProvider)) {
            Log.e(TAG, "Failed to start HTTP Server. Stopping VinylCastService...");
            // TODO Error!
            disengage();
            return;
        }

        //startAudioRecognition();

        startAudioVisualizer(
                audioRecordStreamProvider.getAudioInputStream(),
                audioRecordStreamProvider.getSampleRate(),
                AUDIO_VISUALIZER_FFT_LENGTH,
                AUDIO_VISUALIZER_FFT_BINS);

        // put service in the foreground, post notification
        Helpers.createStopNotification(mediaSession,
                VinylCastService.this,
                VinylCastService.class,
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_ID);

        // register to hear if playback device gets disconnected
        registerForBecomingNoisy();

        updateMediaSession();
        updateMainActivity();
        updateCastSession();
    }

    private void disengage() {
        Log.i(TAG, "disengage");
        unregisterForBecomingNoisy();
        abandonAudioFocus();
        stopAudioRecognition();
        stopHttpServer();
        stopAudioVisualizer();
        stopAudioConverter();
        stopAudioRecord();

        // remove service from foreground
        stopForeground(true);

        updateMediaSession();
        updateMainActivity();
        updateCastSession();
    }

    private boolean isRecording() {
        return audioRecordStreamProvider != null;
    }

    private boolean isPlaybackDeviceSelected() {
        int playbackDeviceId = Helpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_local_playback_device_id, R.string.prefs_default_local_playback_device_id);
        return playbackDeviceId != AudioRecordStreamProvider.AUDIO_DEVICE_ID_NONE;
    }

    private boolean requestAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "Lost Audio Focus. stopping...");
                    disengage();
                    break;

            }
        };
        // Request audio focus for playback, this registers the afChangeListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAudioAttributes(attrs)
                    .build();
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            return (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } else {
            int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            return (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
            audioFocusChangeListener = null;
        } else if (audioFocusChangeListener != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private boolean startAudioRecord(int recordingDeviceId, int playbackDeviceId, boolean lowLatency) {
        audioRecordStreamProvider = new AudioRecordStreamProvider(recordingDeviceId, playbackDeviceId, lowLatency, AUDIO_STREAM_BUFFER_SIZE);
        return audioRecordStreamProvider.start();
    }

    private boolean stopAudioRecord() {
        if (audioRecordStreamProvider == null) {
            return true;
        }
        boolean success = audioRecordStreamProvider.stop();
        audioRecordStreamProvider = null;
        return success;
    }

    private boolean startAudioConverter(AudioStreamProvider rawAudioStream, int audioBufferSize) {
        try {
            convertAudioStreamProvider = new ConvertAudioStreamProvider(rawAudioStream, audioBufferSize);
            convertAudioThread = new Thread(convertAudioStreamProvider, "ConvertAudio");
            convertAudioThread.start();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Exception starting audio record task.", e);
            return false;
        }
    }

    private boolean stopAudioConverter() {
        if (convertAudioThread != null) {
            convertAudioThread.interrupt();
            convertAudioThread = null;
            convertAudioStreamProvider = null;
        }
        return true;
    }

    private boolean startHttpServer(AudioStreamProvider audioStreamProvider) {
        try {
            httpStreamServer = new HttpStreamServerImpl(
                    this,
                    HttpStreamServer.HTTP_SERVER_URL_PATH,
                    HttpStreamServer.HTTP_SERVER_PORT,
                    audioStreamProvider.getAudioEncoding(),
                    audioStreamProvider.getAudioInputStream(),
                    AUDIO_STREAM_BUFFER_SIZE);
            httpStreamServer.start();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Exception creating webserver", e);
            return false;
        }
    }

    private boolean stopHttpServer() {
        if (httpStreamServer != null) {
            httpStreamServer.stop();
            httpStreamServer = null;
            httpStreamProvider = null;
        }
        return true;
    }

    private boolean startAudioVisualizer(
            final InputStream rawAudioInputStream,
            final int sampleRate,
            int fftLength,
            int fftBins) {
        audioVisualizer = new AudioVisualizer(
                rawAudioInputStream,
                AUDIO_STREAM_BUFFER_SIZE,
                sampleRate,
                fftLength,
                fftBins);
        audioVisualizer.setAudioVisualizerListener(mainActivity);
        audioVisualizer.start();
        return true;
    }

    private boolean stopAudioVisualizer() {
        if (audioVisualizer != null) {
            audioVisualizer.stop();
            audioVisualizer = null;
        }
        return false;
    }

    private boolean startAudioRecognition() {
        Intent startRecognitionIntent = new Intent();
        startRecognitionIntent.setClassName(this, "tech.schober.audioacr.AudioRecognitionService");

        startService(startRecognitionIntent);
        return true;
    }

    private boolean stopAudioRecognition() {
        Intent stopRecognitionIntent = new Intent();
        stopRecognitionIntent.setClassName(this, "tech.schober.audioacr.AudioRecognitionService");

        stopService(stopRecognitionIntent);
        return false;
    }

    private void registerForBecomingNoisy() {
        if (isPlaybackDeviceSelected()) {
            becomingNoisyReceiver = new BecomingNoisyReceiver();
            registerReceiver(becomingNoisyReceiver, becomingNoisyIntentFilter);
        }
    }

    private void unregisterForBecomingNoisy() {
        if (becomingNoisyReceiver != null) {
            unregisterReceiver(becomingNoisyReceiver);
            becomingNoisyReceiver = null;
        }
    }

    private void updateMediaSession() {
        int state = isRecording() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_STOPPED;
        long action = isRecording() ? PlaybackStateCompat.ACTION_STOP : PlaybackStateCompat.ACTION_PLAY;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(action)
                .build());
        mediaSession.setActive(isRecording());
    }

    private void updateCastSession() {
        if (((VinylCastApplication)getApplication()).getCastSessionManager().getCurrentCastSession() == null) {
            return;
        }
        if (((VinylCastApplication)getApplication()).getCastSessionManager().getCurrentCastSession().getRemoteMediaClient() == null) {
            return;
        }

        RemoteMediaClient remoteMediaClient = ((VinylCastApplication)getApplication()).getCastSessionManager().getCurrentCastSession().getRemoteMediaClient();
        if (isRecording()) {
            MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
            String url = httpStreamServer.getStreamUrl();
            MediaInfo mediaInfo = new MediaInfo.Builder(url)
                    .setContentType(httpStreamServer.getContentType())
                    .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                    .setStreamDuration(MediaInfo.UNKNOWN_DURATION)
                    .setMetadata(audioMetadata)
                    .build();
            Log.d(TAG, "Cast MediaInfo: " + mediaInfo);
            MediaLoadRequestData mediaLoadRequestData = new MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build();
            remoteMediaClient.load(mediaLoadRequestData);
        } else {
            remoteMediaClient.stop();
        }
    }

    private void updateMainActivity() {
        if (mainActivity != null) {
            mainActivity.updateRecordingState(isRecording());
            if (isRecording()) {
                mainActivity.setStatus(getString(R.string.status_recording) + "\n" + httpStreamServer.getStreamUrl(), true);
            } else {
                mainActivity.setStatus(getString(R.string.status_ready), true);
            }
        }
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        // Clients can connect, but since the BrowserRoot is an empty string
        // onLoadChildren will return nothing. This disables the ability to browse for content.
        return new BrowserRoot("", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
        return;
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSessionCompat onPlay");
            engage();
        }

        @Override
        public void onStop() {
            Log.d(TAG, "MediaSessionCompat onStop");
            disengage();
        }
    };

    private class CastSessionManagerListener implements SessionManagerListener {
        @Override
        public void onSessionStarting(Session session) { }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            Log.d(TAG, "Cast onSessionStarted");
            // cast session started after service already started so trigger updateCastSession
            updateCastSession();
        }

        @Override
        public void onSessionStartFailed(Session session, int i) { }

        @Override
        public void onSessionEnding(Session session) { }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) { }

        @Override
        public void onSessionResumeFailed(Session session, int i) { }

        @Override
        public void onSessionSuspended(Session session, int i) { }

        @Override
        public void onSessionEnded(Session session, int error) { }

        @Override
        public void onSessionResuming(Session session, String s) { }
    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (isPlaybackDeviceSelected() && isRecording()) {
                    disengage();
                }
            }
        }
    }
}
