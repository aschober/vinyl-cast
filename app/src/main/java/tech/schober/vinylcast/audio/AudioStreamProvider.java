package tech.schober.vinylcast.audio;

import java.io.InputStream;

public interface AudioStreamProvider {
    InputStream getAudioInputStream();
}
