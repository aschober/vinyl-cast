package tech.schober.vinylcast.audio;

import androidx.annotation.IntDef;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface AudioStreamProvider {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO_ENCODING_WAV, AUDIO_ENCODING_AAC})
    @interface AudioEncoding {}
    int AUDIO_ENCODING_WAV = 0;
    int AUDIO_ENCODING_AAC = 1;

    InputStream getAudioInputStream();
    int getSampleRate();
    int getChannelCount();
    int getAudioEncoding();
}
