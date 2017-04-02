package com.schober.vinylcast;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.io.IOException;
import java.io.InputStream;

public class MediaRecorderService extends Service {
    private static final String TAG = "MediaRecorderService";

    private final static int HTTP_SERVER_PORT = 5000;
    private static final int NOTIFICATION_ID = 321;

    static final String REQUEST_TYPE = "REQUEST_TYPE";
    static final String REQUEST_TYPE_START = "REQUEST_TYPE_START";
    static final String REQUEST_TYPE_STOP = "REQUEST_TYPE_STOP";

    private final IBinder binder = new MediaRecorderBinder();


    private Thread audioRecordThread = null;
    private Thread convertAudioThread = null;

    private StreamHttpServer server;
    private InputStream convertedAudioInputStream = null;

    private SessionManager castSessionManager;
    private CastSession castSession;
    private SessionManagerListener sessionManagerListener;

    public MediaRecorderService() {
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class MediaRecorderBinder extends Binder {
        //MediaRecorderService getService() {
            // Return this instance of MediaRecorderService so clients can call public methods
        //    return MediaRecorderService.this;
        //}
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        castSessionManager = CastContext.getSharedInstance(this).getSessionManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        castSession = castSessionManager.getCurrentCastSession();

        if (sessionManagerListener == null) {
            sessionManagerListener = new SessionManagerListenerImpl();
            castSessionManager.addSessionManagerListener(sessionManagerListener);
        }

        // Based on https://github.com/columbia/helios_android
        String requestType = intent.getStringExtra(MediaRecorderService.REQUEST_TYPE);
        Log.d(TAG, "onStartCommand received request: " + requestType);

        if (requestType.equals(MediaRecorderService.REQUEST_TYPE_START)) {
            Log.i(TAG, "Started service");
            Helpers.createStopNotification("Vinyl Cast", "Stop", this, MediaRecorderService.class, NOTIFICATION_ID);

            startRecording();
            startHttpServer();

            if (castSession != null) {
                MediaMetadata audioMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

                audioMetadata.putString(MediaMetadata.KEY_TITLE, "Vinyl Track");
                audioMetadata.putString(MediaMetadata.KEY_ARTIST, "Vinyl Artist");

                String url = "http://" + Helpers.getIpAddress(this) + ":" + HTTP_SERVER_PORT + "/vinyl";
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

        if (requestType.equals(MediaRecorderService.REQUEST_TYPE_STOP)) {
            stop();
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
        super.onDestroy();
    }

    private void startRecording() {
        AudioRecordTask audioRecordTask = new AudioRecordTask();
        ConvertAudioRunnable convertAudioRunnable = new ConvertAudioRunnable();

        convertedAudioInputStream = convertAudioRunnable.getConvertedInputStream(audioRecordTask.getRawInputStream(), audioRecordTask.getSampleRate());

        audioRecordThread = new Thread(audioRecordTask);
        audioRecordThread.start();
        convertAudioThread = new Thread(convertAudioRunnable);
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
            server = new StreamHttpServer(HTTP_SERVER_PORT, convertedAudioInputStream);
            Log.d(TAG, "Start webserver on port: " + HTTP_SERVER_PORT);
            server.start();
        } catch (IOException e) {
            Log.e(TAG, "Exception creating webserver", e);
        }
    }

    public void stopHttpServer() {
        if (server != null) {
            server.stop();
        }
    }

    private void stop() {
        Log.i(TAG, "Stopping service");
        stopRecording();
        stopHttpServer();
        stopForeground(true);
        stopSelf();
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
            Log.d(TAG, "Cast onSessionEnded");
        }

        @Override
        public void onSessionResuming(Session session, String s) {

        }
    }
}
