package com.schober.vinylcast.audio;

import android.os.Process;
import android.util.Log;
import android.util.Pair;

import com.schober.vinylcast.utils.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioRecordTask implements Runnable, AudioStreamProvider {

    private static final String TAG = "AudioRecordTask";

    protected Pair<OutputStream, InputStream> nativeAudioStreams;
    protected volatile boolean isStopped = false;

    public AudioRecordTask(int bufferSize) throws IOException {
         this.nativeAudioStreams = Helpers.getPipedAudioStreams(bufferSize);

        NativeAudioEngine.prepareRecording();
        Log.d(TAG, "Prepared to Record - channelCount: " + NativeAudioEngine.getChannelCount() + ", sampleRate: " + NativeAudioEngine.getSampleRate());
    }

    @Override
    public void run() {
        Log.d(TAG, "starting...");
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        // callback from NativeAudioEngine with audioData
        NativeAudioEngine.setAudioDataListener(new NativeAudioEngineListener() {
            @Override
            public void onAudioData(byte[] audioData) {
                try {
                    nativeAudioStreams.first.write(audioData);
                    nativeAudioStreams.first.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Exception writing to raw audio output stream. Stopping runnable.", e);
                    isStopped = true;
                }
            }
        });

        NativeAudioEngine.startRecording();

        while (!Thread.currentThread().isInterrupted() && !isStopped) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted...");
                break;
            }
        }

        Log.d(TAG, "stopping...");
        NativeAudioEngine.stopRecording();
        try {
            nativeAudioStreams.first.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception closing streams", e);
        }
    }

    @Override
    public InputStream getAudioInputStream() {
        return nativeAudioStreams.second;
    }

    public int getSampleRate() {
        return NativeAudioEngine.getSampleRate();
    }

    public int getChannelCount() {
        return NativeAudioEngine.getChannelCount();
    }
}
