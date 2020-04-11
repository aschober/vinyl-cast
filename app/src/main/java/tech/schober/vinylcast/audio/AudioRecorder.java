package tech.schober.vinylcast.audio;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

import tech.schober.vinylcast.utils.Helpers;

public class AudioRecorder implements AudioStreamProvider {

    private static final String TAG = "AudioRecorder";

    protected int bufferSize;
    private CopyOnWriteArrayList<OutputStream> nativeAudioWriteStreams = new CopyOnWriteArrayList<>();

    public AudioRecorder(int bufferSize) {
        this.bufferSize = bufferSize;

        NativeAudioEngine.prepareRecording();
        Log.d(TAG, "Prepared to Record - sampleRate: " + NativeAudioEngine.getSampleRate() +", channel count: " + NativeAudioEngine.getChannelCount());
    }

    public void start() {
        Log.d(TAG, "start");

        // callback from NativeAudioEngine with audioData will end up on own thread
        NativeAudioEngine.setAudioDataListener(new NativeAudioEngineListener() {
            @Override
            public void onAudioData(byte[] audioData) {
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

        NativeAudioEngine.startRecording();
    }

    public void stop() {
        Log.d(TAG, "stop");

        try {
            for (OutputStream writeStream : nativeAudioWriteStreams) {
                nativeAudioWriteStreams.remove(writeStream);
                writeStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing streams", e);
        }

        NativeAudioEngine.stopRecording();
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

    public int getSampleRate() {
        return NativeAudioEngine.getSampleRate();
    }

    public int getChannelCount() {
        return NativeAudioEngine.getChannelCount();
    }
}
