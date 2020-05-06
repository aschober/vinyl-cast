package tech.schober.vinylcast.server;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.StringDef;

import tech.schober.vinylcast.VinylCastService;
import tech.schober.vinylcast.utils.Helpers;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;

import static tech.schober.vinylcast.VinylCastService.AUDIO_ENCODING_AAC;
import static tech.schober.vinylcast.VinylCastService.AUDIO_ENCODING_WAV;

/**
 * HTTP Server handling sending InputStream of data to connected clients.
 */

public class HttpStreamServerImpl extends NanoHTTPD implements HttpStreamServer {
    private static final String TAG = "HttpStreamServerImpl";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({CONTENT_TYPE_WAV, CONTENT_TYPE_AAC})
    public @interface ContentType {}
    public static final String CONTENT_TYPE_WAV = "audio/wav";
    public static final String CONTENT_TYPE_AAC = "audio/aac";

    private Context context;
    private String serverUrlPath;
    private int serverPort;
    private InputStream audioStream;
    private int audioBufferSize;

    private List<HttpStreamServerListener> listeners;

    private String streamUrl;
    private String contentType;
    private HttpServerClients httpServerClients;
    private Thread readAudioThread;

    public HttpStreamServerImpl(Context context, String serverUrlPath, int serverPort, @VinylCastService.AudioEncoding int audioEncoding, InputStream audioStream, int audioBufferSize) {
        super(serverPort);
        this.context = context;
        this.serverUrlPath = serverUrlPath;
        this.serverPort = serverPort;
        this.audioBufferSize = audioBufferSize;

        switch(audioEncoding) {
            case AUDIO_ENCODING_WAV:
                this.contentType = CONTENT_TYPE_WAV;
                break;
            case AUDIO_ENCODING_AAC:
                this.contentType = CONTENT_TYPE_AAC;
                break;
        }
        this.audioStream = audioStream;

        this.listeners = Collections.synchronizedList(new ArrayList());
    }

    public void start() throws IOException {
        // Start NanoHTTPD
        super.start();

        // Create fresh list of clients
        httpServerClients = new HttpServerClients();

        // Create / start HttpReadAudioStream thread
        readAudioThread = new Thread(new HttpReadAudioStreamRunnable(), "HttpReadAudioStream");
        readAudioThread.start();

        // Set stream url and contentType
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

    public String getContentType() {
        return this.contentType;
    }

    @Override
    public int getClientCount() {
        return httpServerClients.getHttpClients().size();
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
                return newFixedLengthResponse(Response.Status.NO_CONTENT, contentType, "Stream not available.");
            }

            Response response = newChunkedResponse(Response.Status.OK, contentType, httpClient.inputStream);
            Log.d(TAG, "Sending HTTP Response: " + response);
            return response;
        } else {
            return super.serve(session);
        }
    }

    class HttpReadAudioStreamRunnable implements Runnable {
        private static final String TAG = "HttpReadAudioStream";

        @Override
        public void run() {
            Log.d(TAG, "starting...");
            //Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            byte[] buffer = new byte[audioBufferSize];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bufferReadResult = audioStream.read(buffer, 0, buffer.length);
                    for (HttpClientImpl httpClient : httpServerClients.getHttpClients()) {
                        try {
                            httpClient.outputStream.write(buffer, 0, bufferReadResult);
                            httpClient.outputStream.flush();
                        } catch (Exception e) {
                            Log.w(TAG, "Exception writing to HttpClient. Removing client from list.", e);
                            httpServerClients.removeClient(httpClient);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Exception reading audio stream input. Exiting.", e);
                    break;
                }
            }

            Log.d(TAG, "interrupted, calling HttpStreamServerImpl.stop()");
            stop();
        }
    }

    class HttpServerClients {

        // Thread-safe list tracking connected clients and their associated streams
        private CopyOnWriteArrayList<HttpClientImpl> httpClients = new CopyOnWriteArrayList<>();

        HttpClientImpl createHttpClient(String ipAddress, String hostname) {
            HttpClientImpl newClient;
            try {
                Pair<OutputStream, InputStream> httpClientStreams = Helpers.getPipedAudioStreams(audioBufferSize);
                newClient = new HttpClientImpl(ipAddress, hostname, httpClientStreams.first, httpClientStreams.second);
                if (contentType.equals(CONTENT_TYPE_WAV)) {
                    writeWAVHeaders(newClient.outputStream);
                }
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
                for (HttpStreamServerListener listener : listeners) {
                    listener.onClientDisconnected(httpClient);
                }
            }
        }

        // HttpClients list should only be modified by HttpServerClients object so provide unmodifiable list
        // Any removal from this list should use removeClient helper method above.
        List<HttpClientImpl> getHttpClients() {
            return Collections.unmodifiableList(this.httpClients);
        }



        void writeWAVHeaders(OutputStream outputStream) throws IOException {
            DataOutputStream dataOutputStream;
            if (outputStream instanceof DataOutputStream) {
                dataOutputStream = (DataOutputStream) outputStream;
            } else {
                dataOutputStream = new DataOutputStream(outputStream);
            }

            dataOutputStream.write(HEADER_RIFF);
            writeInt(dataOutputStream, -1);
            dataOutputStream.write(HEADER_WAVE);
            dataOutputStream.write(HEADER_FMT);
            writeInt(dataOutputStream, HEADER_PCM_SUBCHUNK_1_SIZE);
            writeShort(dataOutputStream, HEADER_PCM_FORMAT);
            writeShort(dataOutputStream, (short) DEFAULT_CHANNEL_COUNT);
            writeInt(dataOutputStream, DEFAULT_SAMPLE_RATE);
            writeInt(dataOutputStream, getByteRate());
            writeShort(dataOutputStream, getBlockAlign());
            writeShort(dataOutputStream, (short) DEFAULT_BITS_PER_SAMPLE);
            dataOutputStream.write(HEADER_DATA);
            writeInt(dataOutputStream, -1);
        }

        /**
         * Little Endian writing of an integer to the stream.
         */
        private void writeInt(DataOutputStream output, int value) throws IOException {
            output.write(value);
            output.write(value >> 8);
            output.write(value >> 16);
            output.write(value >> 24);
        }

        /**
         * Little Endian writing of a short to the stream.
         */
        private void writeShort(final DataOutputStream output, final short value) throws IOException {
            output.write(value);
            output.write(value >> 8);
        }

        private int getByteRate() {
            return DEFAULT_CHANNEL_COUNT * DEFAULT_SAMPLE_RATE * (DEFAULT_BITS_PER_SAMPLE / 8);
        }

        private short getBlockAlign() {
            return (short) (DEFAULT_CHANNEL_COUNT * (DEFAULT_BITS_PER_SAMPLE / 8));
        }
    }

    // "RIFF" in ascii
    private static final byte[] HEADER_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    // 36 bytes in the header (add to total size of input)
    private static final int HEADER_CHUNK_PREFIX_SIZE = 36;
    // "WAVE" in ascii
    private static final byte[] HEADER_WAVE = new byte[]{0x57, 0x41, 0x56, 0x45};
    // "fmt " in ascii
    private static final byte[] HEADER_FMT = new byte[]{0x66, 0x6d, 0x74, 0x20};
    // 1 = PCM
    private static final short HEADER_PCM_FORMAT = 1;
    // "data" in ascii
    private static final byte[] HEADER_DATA = new byte[]{0x64, 0x61, 0x74, 0x61};
    // PCM = 16
    private static final int HEADER_PCM_SUBCHUNK_1_SIZE = 16;

    private static final int DEFAULT_CHANNEL_COUNT = 2;
    private static final int DEFAULT_BITS_PER_SAMPLE = 16;
    private static final int DEFAULT_SAMPLE_RATE = 48000;

    class HttpClientImpl implements HttpClient {
        private String ipAddress;
        private String hostname;
        protected OutputStream outputStream;
        protected InputStream inputStream;

        public HttpClientImpl(String ipAddress, String hostname, OutputStream outputStream, InputStream inputStream) {
            this.ipAddress = ipAddress;
            this.hostname = hostname;
            this.outputStream = outputStream;
            this.inputStream = inputStream;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public String getHostname() {
            return hostname;
        }
    }
}