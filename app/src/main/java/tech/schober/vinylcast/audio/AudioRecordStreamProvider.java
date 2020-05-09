package tech.schober.vinylcast.audio;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.IntDef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CopyOnWriteArrayList;

import tech.schober.vinylcast.utils.Helpers;

public class AudioRecordStreamProvider implements AudioStreamProvider {

    private static final String TAG = "AudioRecorder";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO_API_OPENSLES, AUDIO_API_AAUDIO})
    public @interface AudioApi {}
    public static final int AUDIO_API_OPENSLES = 1;
    public static final int AUDIO_API_AAUDIO = 2;

    public static final int AUDIO_DEVICE_ID_NONE = -1;
    public static final int AUDIO_DEVICE_ID_AUTO_SELECT = 0;

    protected int bufferSize;
    private CopyOnWriteArrayList<OutputStream> nativeAudioWriteStreams = new CopyOnWriteArrayList<>();

    public AudioRecordStreamProvider(int recordingDeviceId, int playbackDeviceId, boolean lowLatency, int bufferSize) {
        NativeAudioEngine.setRecordingDeviceId(recordingDeviceId);
        NativeAudioEngine.setPlaybackDeviceId(playbackDeviceId);
        NativeAudioEngine.setLowLatency(lowLatency);
        this.bufferSize = bufferSize;
    }

    public boolean start() {
        Log.d(TAG, "start");

        boolean preparedSuccess = NativeAudioEngine.prepareRecording();
        if (!preparedSuccess) {
            Log.w(TAG, "Failed to Prepare to Record.");
            return false;
        }
        Log.d(TAG, "Prepared to Record - sampleRate: " + NativeAudioEngine.getSampleRate() +", channel count: " + NativeAudioEngine.getChannelCount());

        // callback from NativeAudioEngine with audioData will end up on own thread
        NativeAudioEngine.setAudioDataListener(new NativeAudioEngineListener() {
            @Override
            public void onAudioData(byte[] audioData) {
                //Log.v(TAG, "audioData.length: " + audioData.length);
                for (OutputStream writeStream : nativeAudioWriteStreams) {
                    try {
                        writeStream.write(audioData);
                        writeStream.flush();
                    } catch (IOException e) {
                        Log.w(TAG, "Exception writing to raw audio output stream. Removing from list of streams.", e);
                        nativeAudioWriteStreams.remove(writeStream);
                    }
                }

                if (nativeAudioWriteStreams.isEmpty()) {
                    Log.e(TAG, "No open write streams.");
                }
            }
        });

        return NativeAudioEngine.startRecording();
    }

    public boolean stop() {
        Log.d(TAG, "stop");

        try {
            for (OutputStream writeStream : nativeAudioWriteStreams) {
                nativeAudioWriteStreams.remove(writeStream);
                writeStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing streams", e);
        }

        return NativeAudioEngine.stopRecording();
    }

    @Override
    public InputStream getAudioInputStream() {
        Log.d(TAG, "getAudioInputStream");
        Pair<OutputStream, InputStream> audioStreams;
        try {
            audioStreams = Helpers.getPipedAudioStreams(bufferSize);
        } catch (IOException e) {
            Log.e(TAG, "Exception creating audio stream", e);
            return null;
        }
        nativeAudioWriteStreams.add(audioStreams.first);
        return audioStreams.second;
    }

    @Override
    public int getSampleRate() {
        return NativeAudioEngine.getSampleRate();
    }

    @Override
    public int getChannelCount() {
        return NativeAudioEngine.getChannelCount();
    }

    @Override
    public int getAudioEncoding() {
        return AUDIO_ENCODING_WAV;
    }

    public String getAudioApi() {
        switch(NativeAudioEngine.getAudioApi()) {
            case AUDIO_API_OPENSLES:
                return "OpenSL ES";
            case AUDIO_API_AAUDIO:
                return "AAudio";
            default:
                return "[not recording]";
        }
    }

    public static int getAudioRecordStreamSampleRate() {
        return NativeAudioEngine.getSampleRate();
    }

    public static int getAudioRecordStreamChannelCount() {
        return NativeAudioEngine.getChannelCount();
    }

    public static int getAudioRecordStreamBitRate() {
        return NativeAudioEngine.getBitRate();
    }

    public static @AudioEncoding int getAudioRecordStreamAudioEncoding() {
        return AUDIO_ENCODING_WAV;
    }
}
