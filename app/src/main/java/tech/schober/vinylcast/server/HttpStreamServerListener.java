package tech.schober.vinylcast.server;

public interface HttpStreamServerListener {
    void onStarted();
    void onStopped();
    void onClientConnected(HttpClient client);
    void onClientDisconnected(HttpClient client);
}
