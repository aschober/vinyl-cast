package tech.schober.vinylcast.server;

import java.io.IOException;

public interface HttpStreamServer {
    String HTTP_SERVER_URL_PATH = "/vinylcast";
    int HTTP_SERVER_PORT = 8080;

    void start() throws IOException;
    void stop();
    String getStreamUrl();
    String getContentType();
    int getClientCount();
    void addServerListener(HttpStreamServerListener listener);
    void removeServerListener(HttpStreamServerListener listener);
}

