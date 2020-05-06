package tech.schober.vinylcast.ui.settings;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.sample.audio_device.AudioDevicePreference;

import tech.schober.vinylcast.BuildConfig;
import tech.schober.vinylcast.R;
import tech.schober.vinylcast.VinylCastService;
import tech.schober.vinylcast.server.HttpClient;
import tech.schober.vinylcast.server.HttpStreamServer;
import tech.schober.vinylcast.server.HttpStreamServerListener;


public class SettingsFragment extends PreferenceFragmentCompat implements ServiceConnection {
    private static final String TAG = "SettingsFragment";

    private VinylCastService.VinylCastBinder binder;

    private Preference.OnPreferenceClickListener disabledPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            boolean isRecording = (binder != null && binder.isRecording());
            if (isRecording) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Recording In-Progress")
                        .setMessage("You cannot change this setting while recording is in-progress.")
                        // The dialog is automatically dismissed when a dialog button is clicked.
                        .setPositiveButton(android.R.string.ok, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
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
        setPreferencesFromResource(R.xml.preferences, rootKey);

        AudioDevicePreference recordingDevicePref = findPreference(R.string.prefs_key_recording_device_id);
        AudioDevicePreference playbackDevicePref = findPreference(R.string.prefs_key_local_playback_device_id);
        ListPreference audioEncodingPref = findPreference(R.string.prefs_key_audio_encoding);
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
        if (audioEncodingPref != null) {
            audioEncodingPref.setEntries(R.array.prefs_audio_encoding_entries);
            audioEncodingPref.setEntryValues(R.array.prefs_audio_encoding_entry_values);
            audioEncodingPref.setOnPreferenceClickListener(disabledPreferenceClickListener);
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
        getActivity().runOnUiThread(new UpdateDynamicPrefsRunnable());
    }

    class UpdateDynamicPrefsRunnable implements Runnable {
        @Override
        public void run() {
            // update audio and http server preferences on main thread
            boolean isRecording = (binder != null && binder.isRecording());

            Preference httpServerPref = findPreference(R.string.prefs_key_http_server);
            Preference httpClientsPref = findPreference(R.string.prefs_key_http_clients);
            Preference audioApiPref = findPreference(R.string.prefs_key_audio_api);

            if (!isRecording) {
                httpServerPref.setSummary(R.string.prefs_default_summary_http_server);
                httpClientsPref.setSummary(R.string.prefs_default_summary_http_clients);
                audioApiPref.setSummary(R.string.prefs_default_summary_audio_api);
            } else {
                audioApiPref.setSummary(binder.getAudioApi());
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
}

