package com.schober.vinylcast.service;

import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.ImageView;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.schober.vinylcast.MusicRecognizer;
import com.schober.vinylcast.utils.Helpers;
import com.schober.vinylcast.MainActivity;
import com.schober.vinylcast.StreamHttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MediaRecorderService extends MediaBrowserServiceCompat {
    private static final String TAG = "MediaRecorderService";

    private static final int NOTIFICATION_ID = 321;

    private static final int MUSIC_RECOGNIZE_INTERVAL = 10000;

    public static final String REQUEST_TYPE = "REQUEST_TYPE";
    public static final String REQUEST_TYPE_START = "REQUEST_TYPE_START";
    public static final String REQUEST_TYPE_STOP = "REQUEST_TYPE_STOP";

    private final IBinder binder = new MediaRecorderBinder();

    private AudioRecordTask audioRecordTask = null;
    private Thread audioRecordThread = null;
    private Thread convertAudioThread = null;

    private StreamHttpServer server;
    private InputStream convertedAudioInputStream = null;

    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;

    private SessionManager castSessionManager;
    private CastSession castSession;
    private SessionManagerListener sessionManagerListener;

    private MusicRecognizer musicRecognizer;
    private Timer musicRecognizerTimer = new Timer();
    private MainActivity activity;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class MediaRecorderBinder extends Binder {

        public void setActivity(MainActivity activity) {
            MediaRecorderService.this.activity = activity;
            musicRecognizer = new MusicRecognizer(MediaRecorderService.this, activity);
            activity.setStatus("" , true);
            start();
        }

        public void updateMetadata(String trackTitle, String artist, String album, BitmapDrawable albumImage) {
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
            Helpers.createStopNotification(mediaSession, MediaRecorderService.this, MediaRecorderService.class, NOTIFICATION_ID);

            // currently, only way to update Cast metadata is to re-send URL which causes reload of stream
            //castMedia(trackTitle, artist, album, imageUrl);
        }

        public void loadAndDisplayCoverArt(String coverArtUrl, ImageView imageView) {
            musicRecognizer.loadAndDisplayCoverArt(coverArtUrl, imageView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
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

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        castSessionManager = CastContext.getSharedInstance(this).getSessionManager();

        // Create a MediaSessionCompat
        mediaSession = new MediaSessionCompat(this, TAG);

        // Enable callbacks from MediaButtons and TransportControls
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(stateBuilder.build());

        // MySessionCallback() has methods that handle callbacks from a media controller
        mediaSession.setCallback(mediaSessionCallback);

        // Set the session's token so that client activities can communicate with it.
        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        castSession = castSessionManager.getCurrentCastSession();

        if (sessionManagerListener == null) {
            sessionManagerListener = new SessionManagerListenerImpl();
            castSessionManager.addSessionManagerListener(sessionManagerListener);
        }

        String requestType = intent.getStringExtra(MediaRecorderService.REQUEST_TYPE);
        Log.d(TAG, "onStartCommand received request: " + requestType);

        if (requestType != null && requestType.equals(MediaRecorderService.REQUEST_TYPE_START)) {
            Log.i(TAG, "Started service");
            // After binding with activity, will hear setActivity() to start recording
        }
        else if (requestType != null && requestType.equals(MediaRecorderService.REQUEST_TYPE_STOP)) {
            stop();
        }
        else {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (sessionManagerListener != null) {
            castSessionManager.removeSessionManagerListener(sessionManagerListener);
            sessionManagerListener = null;
        }
        mediaSession.release();
        super.onDestroy();
    }

    private void start() {
        Log.i(TAG, "Start");
        startRecording();
        startHttpServer();
        startMusicRecognition();
        castMedia();
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0.0f)
                .setActions(PlaybackStateCompat.ACTION_STOP).build());
    }



    private void castMedia() {
        if (castSession != null) {
            MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
            String url = "http://" + Helpers.getIpAddress(MediaRecorderService.this) + ":" + StreamHttpServer.HTTP_SERVER_PORT + StreamHttpServer.HTTP_SERVER_URL_PATH;
            MediaInfo mediaInfo = new MediaInfo.Builder(url)
                    .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType("audio/aac")
                    .setMetadata(audioMetadata)
                    .build();
            Log.d(TAG, "MediaInfo: " + mediaInfo);
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.load(mediaInfo);
        }
    }

    private void stop() {
        Log.i(TAG, "Stop");
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 0.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY).build());
        mediaSession.setActive(false);
        stopMusicRecognition();
        stopRecording();
        stopHttpServer();
        stopForeground(true);
        stopSelf();
    }

    private void startRecording() {
        audioRecordTask = new AudioRecordTask(musicRecognizer.getGnMusicIdStream());
        ConvertAudioTask convertAudioTask = new ConvertAudioTask();

        convertedAudioInputStream = convertAudioTask.getConvertedInputStream(
                audioRecordTask.getRawAudioInputStream(),
                audioRecordTask.getSampleRate(),
                audioRecordTask.getChannelCount()
        );

        audioRecordThread = new Thread(audioRecordTask, "AudioRecord");
        audioRecordThread.start();
        convertAudioThread = new Thread(convertAudioTask, "ConvertAudio");
        convertAudioThread.start();
    }

    private void stopRecording() {
        if (audioRecordThread != null) {
            audioRecordThread.interrupt();
            audioRecordThread = null;
        }
        if (convertAudioThread != null) {
            convertAudioThread.interrupt();
            convertAudioThread = null;
        }
    }

    private void startHttpServer() {
        try {
            server = new StreamHttpServer(convertedAudioInputStream);
            server.start();
        } catch (IOException e) {
            Log.e(TAG, "Exception creating webserver", e);
        }
    }

    private void stopHttpServer() {
        if (server != null) {
            server.stop();
        }
    }

    private void startMusicRecognition() {
        musicRecognizerTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                musicRecognizer.start();
            }
        }, 0, MUSIC_RECOGNIZE_INTERVAL);
    }

    private void stopMusicRecognition() {
        musicRecognizer.stop();
        musicRecognizerTimer.cancel();
    }

    private class SessionManagerListenerImpl implements SessionManagerListener {
        @Override
        public void onSessionStarting(Session session) {
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(TAG, "Cast onSessionEnding");
            stop();
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
        }

        @Override
        public void onSessionEnded(Session session, int error) {
        }

        @Override
        public void onSessionResuming(Session session, String s) {
        }
    }

    MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            Log.d(TAG, "MediaSessionCompat onPlay");
        }

        @Override
        public void onStop() {
            Log.d(TAG, "MediaSessionCompat onStop");
            // Stop the service
            stop();
        }

        @Override
        public void onPause() {
            Log.d(TAG, "MediaSessionCompat onPause");
            // Update metadata and state
            // pause the player (custom call)
            //player.pause();
            // Take the service out of the foreground, retain the notification
            stopForeground(false);
        }
    };
}
