package com.schober.vinylcast;

import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP Server handling sending InputStream of data to a client.
 * Currently only supports connecting to single HTTP client as only a single input stream.
 */

public class StreamHttpServer extends NanoHTTPD {
    private static final String TAG = "StreamHttpServer";

    public static final String HTTP_SERVER_URL_PATH = "/vinylcast";
    public final static int HTTP_SERVER_PORT = 8081;

    private static final int BUFFER_SIZE = 2048;

    private InputStream audioStream;
    private Thread readAudioThread;
    private CopyOnWriteArrayList<OutputStream> outputStreams;

    public StreamHttpServer(InputStream audioStream) throws IOException {
        super(HTTP_SERVER_PORT);
        this.audioStream = audioStream;
        outputStreams = new CopyOnWriteArrayList<>();

        this.readAudioThread = new Thread(new HttpReadStreamRunnable(), "HttpReadStream");
        readAudioThread.start();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String path = session.getUri();
        if (path.equals(HTTP_SERVER_URL_PATH)) {
            Log.d(TAG, "Received HTTP Request: " + session.getRemoteIpAddress());
            InputStream inputStream = getHttpStream();
            if (inputStream == null) {
                Log.e(TAG, "Input Stream not available");
                return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
            }

            return newChunkedResponse(Response.Status.OK, "audio/aac", inputStream);
        } else {
            return super.serve(session);
        }
    }

    private InputStream getHttpStream() {
        try {
            PipedInputStream httpStream = new PipedInputStream(BUFFER_SIZE);
            PipedOutputStream outputStream = new PipedOutputStream(httpStream);
            outputStreams.add(outputStream);
            return httpStream;
        } catch (IOException e) {
            Log.e(TAG, "Exception getting new stream.", e);
            return null;
        }
    }

    class HttpReadStreamRunnable implements Runnable {
        private static final String TAG = "HttpReadStreamRunnable";

        @Override
        public void run() {
            Log.d(TAG, "starting...");
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            byte[] buffer = new byte[BUFFER_SIZE];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bufferReadResult = audioStream.read(buffer, 0, buffer.length);
                    for (OutputStream outputStream : outputStreams) {
                        try {
                            outputStream.write(buffer, 0, bufferReadResult);
                            outputStream.flush();
                        } catch (Exception e) {
                            Log.e(TAG, "Exception writing audio stream output", e);
                            outputStreams.remove(outputStream);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Exception reading audio stream input", e);
                    break;
                }
            }

            Log.d(TAG, "stopping...");
            try {
                for (OutputStream outputStream : outputStreams) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception closing streams", e);
            }
        }
    }
}