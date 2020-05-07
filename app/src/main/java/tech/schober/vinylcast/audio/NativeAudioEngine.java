package tech.schober.vinylcast.audio;

public enum NativeAudioEngine {

    INSTANCE;

    // Load native library
    static {
        System.loadLibrary("vinylCast");
    }

    // Native methods
    public static native boolean create();
    public static native String getOboeVersion();
    public static native boolean isAAudioSupportedAndRecommended();
    public static native void setRecordingDeviceId(int deviceId);
    public static native void setPlaybackDeviceId(int deviceId);
    public static native boolean setAudioApi(int apiType);
    public static native boolean setLowLatency(boolean lowLatency);
    public static native void setAudioDataListener(NativeAudioEngineListener listener);
    public static native int getSampleRate();
    public static native int getChannelCount();
    public static native int getAudioApi();
    public static native boolean prepareRecording();
    public static native boolean startRecording();
    public static native boolean stopRecording();
    public static native void delete();
}

