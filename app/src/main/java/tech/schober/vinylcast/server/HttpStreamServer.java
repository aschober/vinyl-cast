package tech.schober.vinylcast.server;

import java.io.IOException;

public interface HttpStreamServer {
    String HTTP_SERVER_URL_PATH = "/vinylcast";
    String HTTP_SERVER_IMAGE_PATH = "/image.webp";
    int HTTP_SERVER_PORT = 8080;

    void start() throws IOException;
    void stop();
    String getStreamUrl();
    String getImageUrl();
    String getContentType();
    int getClientCount();
    void addServerListener(HttpStreamServerListener listener);
    void removeServerListener(HttpStreamServerListener listener);
}

