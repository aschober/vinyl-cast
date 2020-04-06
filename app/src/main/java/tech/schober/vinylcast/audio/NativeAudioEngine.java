package tech.schober.vinylcast.audio;

public enum NativeAudioEngine {

    INSTANCE;

    // Load native library
    static {
        System.loadLibrary("vinylCast");
    }

    // Native methods
    public static native boolean create();
    public static native boolean isAAudioSupported();
    public static native boolean setAPI(int apiType);
    public static native void setAudioDataListener(NativeAudioEngineListener listener);
    public static native int getSampleRate();
    public static native int getChannelCount();
    public static native void prepareRecording();
    public static native void startRecording();
    public static native void stopRecording();
    public static native void setRecordingDeviceId(int deviceId);
    public static native void setPlaybackDeviceId(int deviceId);
    public static native void delete();
}

