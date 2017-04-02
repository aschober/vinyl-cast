package com.schober.vinylcast;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Runnable that handles saving raw PCM audio data to an input stream.
 */

public class AudioRecordTask implements Runnable {
    private static final String TAG = "AudioRecorderRunnable";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.DEFAULT;
    private static final int AUDIO_SAMPLE_RATE = 48000;

    private static final int MIN_RAW_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT);

    private PipedOutputStream pipedOutputStream;
    private PipedInputStream rawInputStream;

    /**
     * Get InputStream that provides raw audio output. Must be called before starting Runnable.
     * @return
     */
    public InputStream getRawInputStream() {
        try {
            this.rawInputStream = new PipedInputStream(MIN_RAW_BUFFER_SIZE);
            this.pipedOutputStream = new PipedOutputStream(rawInputStream);
            return rawInputStream;
        } catch (IOException e) {
            Log.e(TAG, "Exception creating output stream", e);
            return null;
        }
    }

    public int getSampleRate() {
        return AUDIO_SAMPLE_RATE;
    }

    @Override
    public void run() {
        Log.d(TAG, "starting...");
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        AudioRecord audioRecord = new AudioRecord(
                AUDIO_SOURCE,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                MIN_RAW_BUFFER_SIZE);

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                MIN_RAW_BUFFER_SIZE,
                AudioTrack.MODE_STREAM);

        audioRecord.startRecording();
        //audioTrack.play();

        byte[] buffer = new byte[MIN_RAW_BUFFER_SIZE];
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int bufferReadResult = audioRecord.read(buffer, 0, buffer.length);
                pipedOutputStream.write(buffer, 0, bufferReadResult);
                pipedOutputStream.flush();
                //audioTrack.write(buffer, 0, bufferReadResult);
            } catch (InterruptedIOException e) {
                Log.d(TAG, "interrupted");
                break;
            } catch (IOException e) {
                Log.e(TAG, "Exception writing audio output", e);
                break;
            }
        }

        Log.d(TAG, "stopping...");
        audioRecord.stop();
        //audioTrack.stop();
        //audioTrack.flush();

        try {
            pipedOutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception closing streams", e);
        }
    }
}
