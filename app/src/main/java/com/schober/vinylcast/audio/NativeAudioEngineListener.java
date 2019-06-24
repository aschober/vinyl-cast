package com.schober.vinylcast.audio;

public interface NativeAudioEngineListener {
    void onAudioData(byte[] audioData);
}
