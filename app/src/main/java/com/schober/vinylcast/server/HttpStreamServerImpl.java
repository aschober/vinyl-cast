package com.schober.vinylcast.server;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.schober.vinylcast.utils.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP Server handling sending InputStream of data to a client.
 * Currently only supports connecting to single HTTP client as only a single input stream.
 */

public class HttpStreamServerImpl extends NanoHTTPD implements HttpStreamServer {
    private static final String TAG = "HttpStreamServerImpl";

    private static final int AUDIO_READ_BUFFER_SIZE = 1024;

    private String serverUrlPath;
    private int serverPort;
    private InputStream audioStream;
    private List<HttpStreamServerListener> listeners;
    private Context context;

    private String streamUrl;
    private HttpServerClients httpServerClients;
    private Thread readAudioThread;

    public HttpStreamServerImpl(String serverUrlPath, int serverPort, InputStream audioStream, Context context) {
        super(serverPort);
        this.serverUrlPath = serverUrlPath;
        this.serverPort = serverPort;
        this.audioStream = audioStream;
        this.context = context;

        this.listeners = Collections.synchronizedList(new ArrayList());
    }

    public void start() throws IOException {
        // Start NanoHTTPD
        super.start();

        // Create fresh list of clients
        httpServerClients = new HttpServerClients();

        // Create / start ReadAudioStream thread
        readAudioThread = new Thread(new HttpReadAudioStreamRunnable(), "HttpReadAudioStream");
        readAudioThread.start();

        // Set stream url
        streamUrl = "http://" + Helpers.getIpAddress(context) + ":" + serverPort + serverUrlPath;
        Log.d(TAG, "HTTP Server streaming at: " + streamUrl);

        // Notify listeners
        for (HttpStreamServerListener listener : listeners) {
            listener.onStarted();
        }
    }

    public void stop() {
        // Stop ReadAudio thread
        if (!readAudioThread.isInterrupted()) {
            readAudioThread.interrupt();
        }

        // Remove all Http clients
        httpServerClients.removeAllClients();

        // stop NanoHTTPD server
        super.stop();

        // clear stream url
        Log.d(TAG, "HTTP Server stopped streaming at: " + streamUrl);
        streamUrl = null;

        // Notify listeners
        for (HttpStreamServerListener listener : listeners) {
            listener.onStopped();
        }
    }

    public String getStreamUrl() {
        return this.streamUrl;
    }

    public void addServerListener(HttpStreamServerListener listener) {
        listeners.add(listener);
    }

    public void removeServerListener(HttpStreamServerListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String path = session.getUri();
        if (path.equals(serverUrlPath)) {
            Log.d(TAG, "Received HTTP Request: " + session.getRemoteIpAddress());
            HttpClientImpl httpClient = httpServerClients.createHttpClient(session.getRemoteIpAddress(), session.getRemoteHostName());
            if (httpClient == null) {
                Log.e(TAG, "Failed to create HttpClient.");
                return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Stream not available.");
            }

            return newChunkedResponse(Response.Status.OK, "audio/aac", httpClient.inputStream);
        } else {
            return super.serve(session);
        }
    }

    class HttpReadAudioStreamRunnable implements Runnable {
        private static final String TAG = "HttpReadAudioStream";

        @Override
        public void run() {
            Log.d(TAG, "starting...");
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            byte[] buffer = new byte[AUDIO_READ_BUFFER_SIZE];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bufferReadResult = audioStream.read(buffer, 0, buffer.length);
                    for (HttpClientImpl httpClient : httpServerClients.getHttpClients()) {
                        try {
                            httpClient.outputStream.write(buffer, 0, bufferReadResult);
                            httpClient.outputStream.flush();
                        } catch (Exception e) {
                            Log.e(TAG, "Exception writing to HttpClient. Removing client from list.", e);
                            httpServerClients.removeClient(httpClient);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Exception reading audio stream input. Exiting.", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Log.d(TAG, "interrupted...");
            stop();
        }
    }

    class HttpServerClients {

        // Thread-safe list tracking connected clients and their associated streams
        private CopyOnWriteArrayList<HttpClientImpl> httpClients = new CopyOnWriteArrayList<>();

        HttpClientImpl createHttpClient(String ipAddress, String hostname) {
            HttpClientImpl newClient;
            try {
                PipedInputStream httpClientInputStream = new PipedInputStream(AUDIO_READ_BUFFER_SIZE);
                PipedOutputStream httpClientOutputStream = new PipedOutputStream(httpClientInputStream);
                newClient = new HttpClientImpl(ipAddress, hostname, httpClientInputStream, httpClientOutputStream);
            } catch (IOException e) {
                Log.e(TAG, "Exception getting new stream.", e);
                return null;
            }

            httpClients.add(newClient);
            for (HttpStreamServerListener listener : listeners) {
                listener.onClientConnected(newClient);
            }
            return newClient;
        }

        void removeClient(HttpClientImpl httpClient) {
            try {
                httpClient.inputStream.close();
                httpClient.outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception closing HttpClient streams.", e);
            }
            httpClients.remove(httpClient);
            for (HttpStreamServerListener listener : listeners) {
                listener.onClientDisconnected(httpClient);
            }
        }

        void removeAllClients() {
            for (HttpClientImpl httpClient : httpClients) {
                httpServerClients.removeClient(httpClient);
            }
        }

        // HttpClients list should only be modified by HttpServerClients object so provide unmodifiable list
        List<HttpClientImpl> getHttpClients() {
            return Collections.unmodifiableList(this.httpClients);
        }
    }

    class HttpClientImpl implements HttpClient {
        private String ipAddress;
        private String hostname;
        protected OutputStream outputStream;
        protected InputStream inputStream;

        public HttpClientImpl(String ipAddress, String hostname, InputStream inputStream, OutputStream outputStream) {
            this.ipAddress = ipAddress;
            this.hostname = hostname;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getHostname() {
            return hostname;
        }
    }
}