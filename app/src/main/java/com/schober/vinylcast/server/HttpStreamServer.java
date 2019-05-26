package com.schober.vinylcast.server;

import java.io.IOException;

public interface HttpStreamServer {
    void start() throws IOException;
    void stop();
    String getStreamUrl();
    void addServerListener(HttpStreamServerListener listener);
    void removeServerListener(HttpStreamServerListener listener);
}

interface HttpStreamServerListener {
    void onStarted();
    void onStopped();
    void onClientConnected(HttpClient client);
    void onClientDisconnected(HttpClient client);
}

interface HttpClient {
    String getIpAddress();
    String getHostname();
}