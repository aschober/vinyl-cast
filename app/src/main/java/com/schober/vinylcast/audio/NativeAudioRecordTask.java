package com.schober.vinylcast.audio;


import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

public class NativeAudioRecordTask implements Runnable {

    private static final String TAG = "NativeAudioRecordTask";

    private CopyOnWriteArrayList<OutputStream> rawAudioOutputStreams;

    public NativeAudioRecordTask(CopyOnWriteArrayList<OutputStream> rawAudioOutputStreams) {
        this.rawAudioOutputStreams = rawAudioOutputStreams;
    }

    public int getSampleRate() {
        return  NativeAudioEngine.getSampleRate();
    }

    public int getChannelCount() {
        return  NativeAudioEngine.getChannelCount();
    }

    @Override
    public void run() {
        Log.d(TAG, "starting...");
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        // callback from NativeAudioEngine with audioData
        NativeAudioEngine.setAudioDataListener(new NativeAudioEngineListener() {
            @Override
            public void onAudioData(byte[] audioData) {
                for (OutputStream outputStream : rawAudioOutputStreams) {
                    try {
                        outputStream.write(audioData);
                        outputStream.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Exception writing to raw audio output stream. Removing from outputStreams.", e);
                        rawAudioOutputStreams.remove(outputStream);
                    }
                }
            }
        });
        NativeAudioEngine.prepareRecording();
        Log.d(TAG, "Prepared to Record - channelCount: " + NativeAudioEngine.getChannelCount() + ", sampleRate: " + NativeAudioEngine.getSampleRate());
        NativeAudioEngine.startRecording();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted...", e);
                break;
            }
        }

        Log.d(TAG, "stopping...");
        NativeAudioEngine.stopRecording();
    }
}
