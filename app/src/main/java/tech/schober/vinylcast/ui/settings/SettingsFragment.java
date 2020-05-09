package tech.schober.vinylcast.ui.settings;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.sample.audio_device.AudioDevicePreference;

import java.text.DecimalFormat;
import java.util.Locale;

import tech.schober.vinylcast.BuildConfig;
import tech.schober.vinylcast.R;
import tech.schober.vinylcast.VinylCastService;
import tech.schober.vinylcast.audio.AudioRecordStreamProvider;
import tech.schober.vinylcast.audio.AudioStreamProvider;
import tech.schober.vinylcast.audio.ConvertAudioStreamProvider;
import tech.schober.vinylcast.audio.NativeAudioEngine;
import tech.schober.vinylcast.server.HttpClient;
import tech.schober.vinylcast.server.HttpStreamServer;
import tech.schober.vinylcast.server.HttpStreamServerListener;

import static tech.schober.vinylcast.audio.AudioStreamProvider.AUDIO_ENCODING_AAC;


public class SettingsFragment extends PreferenceFragmentCompat implements ServiceConnection {
    private static final String TAG = "SettingsFragment";

    private VinylCastService.VinylCastBinder binder;

    private Preference.OnPreferenceClickListener disabledPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            Log.d(TAG, "onPreferenceClick: " + preference.getKey());
            boolean isRecording = (binder != null && binder.isRecording());
            if (isRecording) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.alert_recordinginprogress_title)
                        .setMessage(R.string.alert_recordinginprogress_message)
                        // The dialog is automatically dismissed when a dialog button is clicked.
                        .setPositiveButton(android.R.string.ok, null)
                        .setIcon(R.drawable.ic_warning_accent2_24dp)
                        .show();
                return true;
            } else {
                return false;
            }
        }
    };

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        this.binder = ((SettingsActivity) getActivity()).getVinylCastBinder();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Log.d(TAG, "onCreatePreferences");

        setPreferencesFromResource(R.xml.preferences, rootKey);

        AudioDevicePreference recordingDevicePref = findPreference(R.string.prefs_key_recording_device_id);
        AudioDevicePreference playbackDevicePref = findPreference(R.string.prefs_key_local_playback_device_id);
        CheckBoxPreference lowLatencyPref = findPreference(R.string.prefs_key_low_latency);
        ListPreference audioEncodingPref = findPreference(R.string.prefs_key_audio_encoding);
        Preference feedbackPref = findPreference(R.string.prefs_key_feedback);
        Preference androidApiLevelPref = findPreference(R.string.prefs_key_android_api_level);
        Preference appVersionPref = findPreference(R.string.prefs_key_app_version);

        if (recordingDevicePref != null) {
            recordingDevicePref.setDirectionType(AudioManager.GET_DEVICES_INPUTS);
            recordingDevicePref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (playbackDevicePref != null) {
            playbackDevicePref.setDirectionType(AudioManager.GET_DEVICES_OUTPUTS);
            playbackDevicePref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (lowLatencyPref != null) {
            lowLatencyPref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (audioEncodingPref != null) {
            audioEncodingPref.setWidgetLayoutResource(R.layout.pref_widget_button);
            audioEncodingPref.setEntries(R.array.prefs_audio_encoding_entries);
            audioEncodingPref.setEntryValues(R.array.prefs_audio_encoding_entry_values);
            audioEncodingPref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (feedbackPref != null) {
            feedbackPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
//                    if (binder != null) {
//                        Instabug.setUserAttribute("Audio API", binder.getAudioApi());
//                    }
//                    Instabug.show();
                    return true;
                }
            });
        }
        if (androidApiLevelPref != null) {
            androidApiLevelPref.setSummaryProvider(new Preference.SummaryProvider<Preference>() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    return Integer.toString(Build.VERSION.SDK_INT);
                }
            });
        }
        if (appVersionPref != null) {
            appVersionPref.setSummaryProvider(new Preference.SummaryProvider<Preference>() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    return String.format("%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
                }
            });
        }

        // set initial state of dynamic preferences
        updateDynamicPreferences();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected");
        this.binder = (VinylCastService.VinylCastBinder) service;

        // update state of dynamic preferences now that we're bound
        updateDynamicPreferences();

        HttpStreamServer httpStreamServer = binder.getHttpStreamServer();
        // if httpStreamServer exists, add an http server listener to update preferences accordingly
        if (httpStreamServer != null) {
            httpStreamServer.addServerListener(httpStreamServerListener);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        HttpStreamServer httpStreamServer = binder.getHttpStreamServer();
        if (httpStreamServer != null) {
            binder.getHttpStreamServer().removeServerListener(httpStreamServerListener);
        }
        this.binder = null;

        // update state of dynamic preferences now that we're bound
        updateDynamicPreferences();
    }

    protected <T extends Preference> T findPreference(@StringRes int key_id) {
        return (T) findPreference(getString(key_id));
    }

    private void updateDynamicPreferences() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new UpdateDynamicPrefsRunnable());
        }
    }

    class UpdateDynamicPrefsRunnable implements Runnable {
        @Override
        public void run() {
            // update audio and http server preferences on main thread
            boolean isRecording = (binder != null && binder.isRecording());

            ListPreference audioEncodingPref = findPreference(R.string.prefs_key_audio_encoding);
            Preference httpServerPref = findPreference(R.string.prefs_key_http_server);
            Preference httpClientsPref = findPreference(R.string.prefs_key_http_clients);
            Preference audioApiPref = findPreference(R.string.prefs_key_audio_api);

            if (getView() != null) {
                ImageButton prefImageButton = getView().findViewById(R.id.pref_image_button);
                if (isRecording) {
                    prefImageButton.setVisibility(View.VISIBLE);
                    prefImageButton.setOnClickListener(l -> {
                        Log.d(TAG, "Pref Button pressed!");
                        getAudioEncodingInfoDialog(Integer.valueOf(audioEncodingPref.getValue())).show();
                    });
                } else {
                    prefImageButton.setVisibility(View.GONE);
                    prefImageButton.setOnClickListener(null);
                }
            }

            if (!isRecording) {
                httpServerPref.setSummary(R.string.prefs_default_summary_http_server);
                httpClientsPref.setSummary(R.string.prefs_default_summary_http_clients);
                audioApiPref.setSummary(getAudioApiVersionString());
            } else {
                audioApiPref.setSummary(getAudioApiVersionString());
                httpServerPref.setSummary(binder.getHttpStreamServer().getStreamUrl());
                httpClientsPref.setSummary(Integer.toString(binder.getHttpStreamServer().getClientCount()));
            }
        }
    }

    // create http server listener to update state of http server preferences via Handler on main thread
    private HttpStreamServerListener httpStreamServerListener = new HttpStreamServerListener() {
        @Override
        public void onStarted() {
            updateDynamicPreferences();
        }

        @Override
        public void onStopped() {
            updateDynamicPreferences();
        }

        @Override
        public void onClientConnected(HttpClient client) {
            updateDynamicPreferences();
        }

        @Override
        public void onClientDisconnected(HttpClient client) {
            updateDynamicPreferences();
        }
    };

    private String getAudioApiVersionString() {
        if (binder != null && binder.isRecording()) {
            return "Oboe " + NativeAudioEngine.getOboeVersion() + ": " + binder.getRecorderAudioApi();
        } else {
            boolean isAAudio = NativeAudioEngine.isAAudioSupportedAndRecommended();
            return "Oboe " + NativeAudioEngine.getOboeVersion() + ": " + (isAAudio ? "AAudio" : "OpenSL ES");
        }
    }

    private AlertDialog getAudioEncodingInfoDialog(@AudioStreamProvider.AudioEncoding int audioEncoding) {
        float sampleRateKhz;
        int channelCount;
        float bitRateKbps;
        int titleResId;

        switch (audioEncoding) {
            case AUDIO_ENCODING_AAC:
                sampleRateKhz = ConvertAudioStreamProvider.getConvertAudioStreamSampleRate() / 1000f;
                channelCount = ConvertAudioStreamProvider.getConvertAudioStreamChannelCount();
                bitRateKbps = ConvertAudioStreamProvider.getConvertAudioStreamBitRate() / 1000f;
                titleResId = R.string.alert_encodingdetails_aac_title;
                break;
            default:
                sampleRateKhz = AudioRecordStreamProvider.getAudioRecordStreamSampleRate() / 1000f;
                channelCount = AudioRecordStreamProvider.getAudioRecordStreamChannelCount();
                bitRateKbps = AudioRecordStreamProvider.getAudioRecordStreamBitRate() / 1000f;
                titleResId = R.string.alert_encodingdetails_aac_title;
                break;
        }

        String message = String.format(Locale.getDefault(),
                "Sample Rate: %s kHz\nChannels: %d\nBit Rate: %s kbps",
                new DecimalFormat("#.#").format(sampleRateKhz),
                channelCount,
                new DecimalFormat("#.#").format(bitRateKbps));

        return new AlertDialog.Builder(getContext())
                .setTitle(titleResId)
                .setMessage(message)
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(R.drawable.ic_info_outline_accent2_24dp)
                .create();
    }
}

