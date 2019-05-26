package com.schober.vinylcast.recognizer;

import android.content.Context;

import com.schober.vinylcast.MainActivity;

/**
 * Used to perform ACR of the stream from the raw audio input stream.
 *
 */

public class MusicRecognizerImpl implements MusicRecognizer {
    private static final String TAG = "MusicRecognizerImpl";


    private Context context;
    private MainActivity activity;

    public MusicRecognizerImpl(Context context, MainActivity activity) {
        this.context = context;
        this.activity = activity;
    }

    public void start() {

    }

    public void stop() {

    }

}
