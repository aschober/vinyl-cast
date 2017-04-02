package com.schober.vinylcast;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP Server handling sending InputStream of data to a client.
 * Currently only supports connecting to single HTTP client as only a single input stream.
 */

public class StreamHttpServer extends NanoHTTPD {
    private static final String TAG = "StreamHttpServer";

    private InputStream inputStream;
    private volatile boolean connectedClient;

    public StreamHttpServer(int port, InputStream inputStream) throws IOException {
        super(port);
        this.inputStream = inputStream;
        connectedClient = false;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String path = session.getUri();
        if (path.equals("/vinyl")) {
            Log.d(TAG, "Received HTTP Request: " + session.getRemoteIpAddress());

            if (connectedClient) {
                Log.e(TAG, "Client already connected");
                return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
            } else {
                connectedClient = true;
                InputStream inputStream = this.inputStream;
                if (inputStream == null) {
                    Log.e(TAG, "Input Stream not available");
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "audio/aac", "Input Stream not available");
                }

                return newChunkedResponse(Response.Status.OK, "audio/aac", inputStream);
            }
        } else {
            return super.serve(session);
        }
    }

}