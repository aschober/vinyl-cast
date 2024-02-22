package tech.schober.vinylcast;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.IntDef;
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
import com.google.android.gms.common.images.WebImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import tech.schober.vinylcast.audio.AudioRecordStreamProvider;
import tech.schober.vinylcast.audio.AudioStreamProvider;
import tech.schober.vinylcast.audio.AudioVisualizer;
import tech.schober.vinylcast.audio.ConvertAudioStreamProvider;
import tech.schober.vinylcast.audio.NativeAudioEngine;
import tech.schober.vinylcast.server.HttpStreamServer;
import tech.schober.vinylcast.server.HttpStreamServerImpl;
import tech.schober.vinylcast.utils.VinylCastHelpers;
import timber.log.Timber;

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

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATUS_PREPARING, STATUS_READY, STATUS_RECORDING, STATUS_STOPPED, STATUS_ERROR_UNKNOWN, STATUS_ERROR_PERMISSION_DENIED, STATUS_ERROR_AUDIO_FOCUS_FAILED, STATUS_ERROR_AUDIO_RECORD_FAILED, STATUS_ERROR_AUDIO_CONVERT_FAILED, STATUS_ERROR_HTTP_SERVER_FAILED})
    public @interface StatusCode {}
    public static final int STATUS_PREPARING = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_RECORDING = 2;
    public static final int STATUS_STOPPED = 3;
    public static final int STATUS_ERROR_UNKNOWN = 100;
    public static final int STATUS_ERROR_PERMISSION_DENIED = 101;
    public static final int STATUS_ERROR_AUDIO_FOCUS_FAILED = 102;
    public static final int STATUS_ERROR_AUDIO_RECORD_FAILED = 103;
    public static final int STATUS_ERROR_AUDIO_CONVERT_FAILED = 104;
    public static final int STATUS_ERROR_HTTP_SERVER_FAILED = 105;

    private final IBinder binder = new VinylCastBinder();
    private CopyOnWriteArrayList<VinylCastServiceListener> vinylCastServiceListeners = new CopyOnWriteArrayList<>();
    private Integer lastStatusCode = null;

    private AudioRecordStreamProvider audioRecordStreamProvider;

    private Thread convertAudioThread;
    private ConvertAudioStreamProvider convertAudioStreamProvider;

    private HttpStreamServer httpStreamServer;
    private AudioStreamProvider httpStreamProvider;

    private AudioVisualizer audioVisualizer;
    private CopyOnWriteArrayList<AudioVisualizer.AudioVisualizerListener> audioVisualizerListeners = new CopyOnWriteArrayList<>();

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private MediaSessionCompat mediaSession;
    private SessionManagerListener castSessionManagerListener;
    private BecomingNoisyReceiver becomingNoisyReceiver;
    private IntentFilter becomingNoisyIntentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    public interface VinylCastServiceListener {
        void onStatusUpdate(@StatusCode int statusCode);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class VinylCastBinder extends Binder {
        public boolean isRecording() {
            return VinylCastService.this.isRecording();
        }

        public void addVinylCastServiceListener(VinylCastServiceListener listener) {
            vinylCastServiceListeners.add(listener);
            // make sure to send updated state to newly added listener
            resendStatus();
        }

        public void removeVinylCastServiceListener(VinylCastServiceListener listener) {
            vinylCastServiceListeners.remove(listener);
        }

        public void addAudioVisualizerListener(AudioVisualizer.AudioVisualizerListener listener) {
            audioVisualizerListeners.add(listener);
        }

        public void removeAudioVisualizerListener(AudioVisualizer.AudioVisualizerListener listener) {
            audioVisualizerListeners.remove(listener);
        }

        public void start() {
            VinylCastService.this.engage();
        }

        public void stop() {
            VinylCastService.this.disengage(false);
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        Timber.d("onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Timber.d("onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        Timber.d("onCreate");
        super.onCreate();
        registerMediaSession();
        updateStatus(STATUS_READY);
    }



    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        if (castSessionManagerListener != null) {
            ((VinylCastApplication)getApplication()).getCastSessionManager().removeSessionManagerListener(castSessionManagerListener);
            castSessionManagerListener = null;
        }
        unregisterMediaSession();
        super.onDestroy();
    }

    private void registerMediaSession() {
        // Create a MediaSessionCompat
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession = new MediaSessionCompat(this, TAG, null, pendingIntent);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP);
        mediaSession.setPlaybackState(stateBuilder.build());

        // MediaSessionCallback() has methods that handle callbacks from a media controller
        mediaSession.setCallback(mediaSessionCallback);

        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mediaSession.getSessionToken());
    }

    private void unregisterMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.d("onStartCommand");

        if (castSessionManagerListener == null) {
            castSessionManagerListener = new CastSessionManagerListener();
            ((VinylCastApplication)getApplication()).getCastSessionManager().addSessionManagerListener(castSessionManagerListener);
        }

        String action = intent.getAction();
        Timber.d("onStartCommand received action: " + action);

        switch (action) {
            case VinylCastService.ACTION_START_RECORDING:
                engage();
                break;
            case VinylCastService.ACTION_STOP_RECORDING:
                disengage(false);
                break;
            default:
                MediaButtonReceiver.handleIntent(mediaSession, intent);
                break;
        }

        return START_NOT_STICKY;
    }

    private void engage() {
        Timber.i("engage");

        // fetch audio record settings values out of SharedPreferences
        int recordingDeviceId = VinylCastHelpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_recording_device_id, R.string.prefs_default_recording_device_id);
        int playbackDeviceId = VinylCastHelpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_local_playback_device_id, R.string.prefs_default_local_playback_device_id);
        @AudioStreamProvider.AudioEncoding int audioEncoding = VinylCastHelpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_audio_encoding, R.string.prefs_default_audio_encoding);
        boolean lowLatency = PreferenceManager.getDefaultSharedPreferences (this).getBoolean(getString(R.string.prefs_key_low_latency), Boolean.valueOf(getString(R.string.prefs_default_low_latency)));
        double gainDecibels = VinylCastHelpers.getGainPreference(this);

        NativeAudioEngine.setGainDecibels(gainDecibels);

        if (isPlaybackDeviceSelected() && !requestAudioFocus()) {
            Timber.e("Failed to get Audio Focus for playback. Stopping VinylCastService...");
            updateStatus(STATUS_ERROR_AUDIO_FOCUS_FAILED);
            disengage(true);
            return;
        }

        if (!startAudioRecord(recordingDeviceId, playbackDeviceId, lowLatency)) {
            Timber.e("Failed to start Audio Record. Stopping VinylCastService...");
            updateStatus(STATUS_ERROR_AUDIO_RECORD_FAILED);
            disengage(true);
            return;
        }

        switch (audioEncoding) {
            case AUDIO_ENCODING_AAC:
                if (!startAudioConverter(audioRecordStreamProvider, AUDIO_STREAM_BUFFER_SIZE)) {
                    Timber.e("Failed to start Audio Converter. Stopping VinylCastService...");
                    updateStatus(STATUS_ERROR_AUDIO_CONVERT_FAILED);
                    disengage(true);
                    return;
                }
                httpStreamProvider = convertAudioStreamProvider;
                break;
            default:
                httpStreamProvider = audioRecordStreamProvider;
                break;
        }

        if (!startHttpServer(httpStreamProvider)) {
            Timber.e("Failed to start HTTP Server. Stopping VinylCastService...");
            updateStatus(STATUS_ERROR_HTTP_SERVER_FAILED);
            disengage(true);
            return;
        }

        //startAudioRecognition();

        startAudioVisualizer(
                audioRecordStreamProvider.getAudioInputStream(),
                audioRecordStreamProvider.getSampleRate(),
                AUDIO_VISUALIZER_FFT_LENGTH,
                AUDIO_VISUALIZER_FFT_BINS);

        // put service in the foreground, post notification
        VinylCastHelpers.createStopNotification(mediaSession,
                VinylCastService.this,
                VinylCastService.class,
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_ID,
                httpStreamServer.getStreamUrl());

        // register to hear if playback device gets disconnected
        registerForBecomingNoisy();

        updateMediaSession();
        updateStatus(STATUS_RECORDING);
        updateCastSession();
    }

    private void disengage(boolean fromError) {
        Timber.i("disengage");
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
        if (!fromError) {
            updateStatus(STATUS_STOPPED);
        }
        updateCastSession();
    }

    private boolean isRecording() {
        return audioRecordStreamProvider != null;
    }

    private boolean isPlaybackDeviceSelected() {
        int playbackDeviceId = VinylCastHelpers.getSharedPreferenceStringAsInteger(this, R.string.prefs_key_local_playback_device_id, R.string.prefs_default_local_playback_device_id);
        return playbackDeviceId != AudioRecordStreamProvider.AUDIO_DEVICE_ID_NONE;
    }

    private boolean requestAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Timber.d("Lost Audio Focus. stopping...");
                    disengage(false);
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
            Timber.e(e,"Exception starting audio record task.");
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

    private byte[] getAlbumArtImage() {
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.vinyl_orange_512);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.WEBP, 100, stream);
        return stream.toByteArray();
    }
    private boolean startHttpServer(AudioStreamProvider audioStreamProvider) {
        try {
            httpStreamServer = new HttpStreamServerImpl(
                    this,
                    HttpStreamServer.HTTP_SERVER_URL_PATH,
                    HttpStreamServer.HTTP_SERVER_IMAGE_PATH,
                    getAlbumArtImage(),
                    HttpStreamServer.HTTP_SERVER_PORT,
                    audioStreamProvider.getAudioEncoding(),
                    audioStreamProvider.getAudioInputStream(),
                    AUDIO_STREAM_BUFFER_SIZE);
            httpStreamServer.start();
            return true;
        } catch (IOException e) {
            Timber.e(e, "Exception creating webserver");
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
                fftBins,
                audioVisualizerListeners);
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
        if (isRecording() && httpStreamServer != null) {
            WebImage img = new WebImage(Uri.parse(httpStreamServer.getImageUrl()));
            MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
            audioMetadata.putString(MediaMetadata.KEY_TITLE, "Vinyl Cast");
            audioMetadata.addImage(img);
            String url = httpStreamServer.getStreamUrl();
            MediaInfo mediaInfo = new MediaInfo.Builder(url)
                    .setContentType(httpStreamServer.getContentType())
                    .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                    .setStreamDuration(MediaInfo.UNKNOWN_DURATION)
                    .setMetadata(audioMetadata)
                    .build();
            Timber.d("Cast MediaInfo: " + mediaInfo);
            MediaLoadRequestData mediaLoadRequestData = new MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build();
            remoteMediaClient.load(mediaLoadRequestData);
        } else {
            remoteMediaClient.stop();
        }
    }

    private void updateStatus(@StatusCode int statusCode) {
        for (VinylCastServiceListener listener : vinylCastServiceListeners) {
            listener.onStatusUpdate(statusCode);
        }
        lastStatusCode = statusCode;
    }

    private void resendStatus() {
        if (lastStatusCode != null) {
            updateStatus(lastStatusCode);
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
            Timber.d("MediaSessionCompat onPlay");
            engage();
        }

        @Override
        public void onStop() {
            Timber.d("MediaSessionCompat onStop");
            disengage(false);
        }
    };

    private class CastSessionManagerListener implements SessionManagerListener {
        @Override
        public void onSessionStarting(Session session) { }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            Timber.d("Cast onSessionStarted");
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
                    disengage(false);
                }
            }
        }
    }
}
