package com.schober.vinylcast.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runnable that handles saving raw PCM audio data to an input stream.
 */

public class AudioRecordTask implements Runnable {
    private static final String TAG = "AudioRecorderRunnable";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.DEFAULT;
    private static final int AUDIO_SAMPLE_RATE = 48000;
    private static final int AUDIO_CHANNEL_COUNT = 2;
    private static final int AUDIO_BIT_DEPTH = 16;

    private static final int AUDIO_RECORD_BUFFER_SIZE_BYTES = 2 * AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
    );

    private static final int AUDIO_BUFFER_SIZE = AUDIO_RECORD_BUFFER_SIZE_BYTES / 2;

    private AudioRecord audioRecord;
    private CopyOnWriteArrayList<OutputStream> rawAudioOutputStreams;

    public AudioRecordTask(CopyOnWriteArrayList<OutputStream> rawAudioOutputStreams) {
        Log.d(TAG, "AudioRecordTask - AudioRecordBufferSizeBytes: " + AUDIO_RECORD_BUFFER_SIZE_BYTES);
        this.audioRecord = new AudioRecord(
                AUDIO_SOURCE,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                AUDIO_RECORD_BUFFER_SIZE_BYTES);
        this.rawAudioOutputStreams = rawAudioOutputStreams;
    }

    public int getSampleRate() {
        return AUDIO_SAMPLE_RATE;
    }

    public int getChannelCount() {
        return AUDIO_CHANNEL_COUNT;
    }

    public int getAudioBitDepth() {
        return AUDIO_BIT_DEPTH;
    }

    @Override
    public void run() {
        Log.d(TAG, "starting...");
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        audioRecord.startRecording();
        byte[] buffer = new byte[AUDIO_BUFFER_SIZE];
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    for (OutputStream outputStream : rawAudioOutputStreams) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                    }
                    //Log.d(TAG, "AudioRecord bytesRead: " + bytesRead);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception getting raw audio output.", e);
                break;
            }
        }

        Log.d(TAG, "stopping...");
        audioRecord.stop();
        audioRecord.release();
        try {
            for (OutputStream outputStream : rawAudioOutputStreams) {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing raw audio output streams.", e);
        }
    }
}
