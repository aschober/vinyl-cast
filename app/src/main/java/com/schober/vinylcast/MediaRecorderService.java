package com.schober.vinylcast;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.apache.commons.io.input.TeeInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import fi.iki.elonen.NanoHTTPD;

import static java.lang.Thread.sleep;

public class MediaRecorderService extends Service {
    private static final String TAG = "MediaRecorderService";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.DEFAULT;
    private static final int SAMPLE_RATE = 48000; // Hz
    private static final int BIT_RATE = 192000;

    private final static int HTTP_SERVER_PORT = 5000;

    static final String REQUEST_TYPE = "REQUEST_TYPE";
    static final String REQUEST_TYPE_START = "REQUEST_TYPE_START";
    static final String REQUEST_TYPE_STOP = "REQUEST_TYPE_STOP";

    private final IBinder binder = new MediaRecorderBinder();

    private MediaRecorder mediaRecorder = null;
    private ParcelFileDescriptor[] dataPipe = null;
    private InputStream mediaRecorderInputStream = null;

    private Thread mediaRecorderReadThread = null;

    private HttpServer server;

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

    class MediaRecorderReadRunnable implements Runnable {

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    int bytesAvailable = mediaRecorderInputStream.available();
                    if (bytesAvailable > buffer.length) bytesAvailable = buffer.length;
                    if (bytesAvailable > 0) {
                        mediaRecorderInputStream.read(buffer, 0, bytesAvailable);
                    }
                    sleep(10);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Exception in MediaRecorderReadRunnable", e);
                stopRecording();
                stopHttpServer();
                stopSelf();
            }
        }
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

        try {
            dataPipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
            //Helpers.createStopNotification("Vinyl Cast", "Stop", this, MediaRecorderService.class, NOTIFICATION_ID);

            startHttpServer();
            startRecording();

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
            Log.i(TAG, "Stopping service");
            stopRecording();
            stopHttpServer();
            stopSelf();
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
        try {
            ParcelFileDescriptor parcelWrite  = new ParcelFileDescriptor(dataPipe[1]);
            ParcelFileDescriptor parcelRead  = new ParcelFileDescriptor(dataPipe[0]);

            ParcelFileDescriptor.AutoCloseInputStream autoCloseInputStream = new ParcelFileDescriptor.AutoCloseInputStream(parcelRead);
            mediaRecorderInputStream = autoCloseInputStream;

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(AUDIO_SOURCE);

            mediaRecorder.setAudioChannels(2);
            mediaRecorder.setAudioEncodingBitRate(BIT_RATE);
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            mediaRecorder.setOutputFile(parcelWrite.getFileDescriptor());
            mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    Log.d(TAG, "MediaRecorder onInfo: " + what + ", " + extra);
                }
            });
            mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    Log.d(TAG, "MediaRecorder onError: " + what + ", " + extra);
                }
            });

            mediaRecorder.prepare();
            mediaRecorderReadThread = new Thread(new MediaRecorderReadRunnable());

            Log.d(TAG, "mediaRecorder.start()");
            mediaRecorder.start();
            mediaRecorderReadThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {

        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (mediaRecorderReadThread != null) {
            mediaRecorderReadThread.interrupt();
            mediaRecorderReadThread = null;
        }

        if (mediaRecorderInputStream != null) {
            try {
                mediaRecorderInputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception closing input stream", e);
            }
            mediaRecorderInputStream = null;
        }

        stopForeground(true);
    }

    private InputStream getInputStream() {
        Log.d(TAG, "getInputStream()");

        try {
            PipedInputStream inputStream = new PipedInputStream();
            PipedOutputStream outputStream = new PipedOutputStream(inputStream);
            mediaRecorderInputStream = new TeeInputStream(mediaRecorderInputStream, outputStream);
            return inputStream;
        } catch (IOException e) {
            Log.e(TAG, "Exception splitting InputStream", e);
            return null;
        }
    }

    private void startHttpServer() {
        try {
            server = new HttpServer();
        } catch (IOException e) {
            Log.e(TAG, "Exception creating webserver", e);
        }
    }

    public void stopHttpServer() {
        if (server != null) {
            server.stop();
        }
    }

    public class HttpServer extends NanoHTTPD {

        public HttpServer() throws IOException {
            super(HTTP_SERVER_PORT);
            start();
            Log.d(TAG, "Start webserver on port: " + HTTP_SERVER_PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String path = session.getUri();
            if (path.equals("/vinyl")) {
                Log.d(TAG, "Received HTTP Request: " + session.getRemoteIpAddress());
                if (binder == null) {
                    Log.e(TAG, "MediaRecorderService Binder not available");
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
                }

                InputStream inputStream = getInputStream();
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
            stopRecording();
            stopHttpServer();
            stopSelf();
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
