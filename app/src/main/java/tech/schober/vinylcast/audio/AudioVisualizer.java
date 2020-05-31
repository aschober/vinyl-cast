package tech.schober.vinylcast.audio;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import github.bewantbe.audio_analyzer_for_android.STFT;
import timber.log.Timber;

public class AudioVisualizer {
    private static final String TAG = "AudioVisualizer";

    private InputStream audioInputStream;
    private int audioBufferSize;
    private int fftBins;

    private STFT stft;

    private Thread audioVisualizerThread;
    private Handler audioVisualizerRenderHandler;
    private Runnable audioVisualizerRenderRunnable;
    private List<AudioVisualizerListener> audioVisualizerListenersImmutable;

    public AudioVisualizer(InputStream audioInputStream, int audioBufferSize, int sampleRate, int fftLength, int fftBins, CopyOnWriteArrayList audioVisualizerListeners) {
        this.audioInputStream = audioInputStream;
        this.audioBufferSize = audioBufferSize;
        this.fftBins = fftBins;
        this.stft = new STFT(fftLength, sampleRate, fftBins);
        this.audioVisualizerListenersImmutable = Collections.unmodifiableList(audioVisualizerListeners);
    }

    public void start() {

        Runnable audioVisualizerRunnable = new AudioVisualizerFeed();
        audioVisualizerRenderRunnable = new AudioVisualizerRender();

        audioVisualizerThread = new Thread(audioVisualizerRunnable, "AudioVisualizerFeed");
        audioVisualizerThread.start();

        audioVisualizerRenderHandler = new Handler(Looper.getMainLooper());
        audioVisualizerRenderHandler.post(audioVisualizerRenderRunnable);
    }

    public void stop() {
        if (audioVisualizerThread != null) {
            audioVisualizerThread.interrupt();
            audioVisualizerThread = null;
        }
        if (audioVisualizerRenderHandler != null) {
            audioVisualizerRenderHandler.removeCallbacks(audioVisualizerRenderRunnable);
            audioVisualizerRenderHandler = null;
            audioVisualizerRenderRunnable = null;
        }
    }

    public interface AudioVisualizerListener {
        void onAudioVisualizerData(double[] spectrumAmpDB);
    }

    class AudioVisualizerFeed implements Runnable {
        private static final String TAG = "AudioVisualizerFeed";

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            byte[] rawByteBuffer = new byte[audioBufferSize];
            short[] monoShortBuffer = new short[audioBufferSize / 4];

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = audioInputStream.read(rawByteBuffer, 0, rawByteBuffer.length);
                    if (bytesRead > 0) {

                        int rawAudioBufferIndex = 0;
                        int monoArrayIndex = 0;

                        // split audio into left/right channels and then average for mono audio
                        while (rawAudioBufferIndex < bytesRead) {
                            int leftSample = (short) ((rawByteBuffer[rawAudioBufferIndex] & 0xff) | (rawByteBuffer[rawAudioBufferIndex + 1] << 8));

                            rawAudioBufferIndex = rawAudioBufferIndex + 2;
                            int rightSample = (short) ((rawByteBuffer[rawAudioBufferIndex] & 0xff) | (rawByteBuffer[rawAudioBufferIndex + 1] << 8));
                            rawAudioBufferIndex = rawAudioBufferIndex + 2;

                            short mono = (short) ((leftSample + rightSample) / 2);

                            monoShortBuffer[monoArrayIndex] = mono;
                            monoArrayIndex++;
                        }

                        stft.feedData(monoShortBuffer, monoArrayIndex);
                    }
                } catch (Exception e) {
                    Timber.e(e, "Exception reading audio stream input. Exiting.");
                    break;
                }
            }
        }
    }

    class AudioVisualizerRender implements Runnable {

        private static final String TAG = "AudioVisualizerRender";

        @Override
        public void run() {
            double[] spectrumAmpDB = stft.getSpectrumAmpDB();
            spectrumAmpDB = Arrays.copyOf(spectrumAmpDB, fftBins);

            for (AudioVisualizerListener listener : audioVisualizerListenersImmutable) {
                if (listener != null) {
                    listener.onAudioVisualizerData(spectrumAmpDB);
                }
            }
            audioVisualizerRenderHandler.postDelayed(audioVisualizerRenderRunnable, 66);
        }
    }
}

