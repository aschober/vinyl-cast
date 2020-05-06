package tech.schober.vinylcast.ui.settings;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.StringRes;
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
        Preference audioApiPref = findPreference(R.string.prefs_key_audio_api);
        Preference androidApiLevelPref = findPreference(R.string.prefs_key_android_api_level);
        Preference appVersionPref = findPreference(R.string.prefs_key_app_version);

        if (recordingDevicePref != null) {
            recordingDevicePref.setDirectionType(AudioManager.GET_DEVICES_INPUTS);
        }
        if (playbackDevicePref != null) {
            playbackDevicePref.setDirectionType(AudioManager.GET_DEVICES_OUTPUTS);
        }
        if (audioEncodingPref != null) {
            audioEncodingPref.setEntries(R.array.prefs_audio_encoding_entries);
            audioEncodingPref.setEntryValues(R.array.prefs_audio_encoding_entry_values);
        }
        if (audioApiPref != null) {
            if (binder == null) {
                audioApiPref.setSummary(R.string.prefs_default_summary_audio_api);
            } else {
                audioApiPref.setSummary(binder.getAudioApi());
            }
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
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected");
        this.binder = (VinylCastService.VinylCastBinder) service;
        HttpStreamServer httpStreamServer = binder.getHttpStreamServer();

        // set initial state of http server preferences
        sendUpdateDynamicPreferencesMessage();

        if (httpStreamServer != null) {
            // if httpStreamServer exists, add an http server listener to update preferences accordingly
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
    }

    protected <T extends Preference> T findPreference(@StringRes int key_id) {
        return (T) findPreference(getString(key_id));
    }

    private void sendUpdateDynamicPreferencesMessage() {
        Message updateDynamicPrefsMessage = handler.obtainMessage(MSG_UPDATE_DYNAMIC_PREFS);
        updateDynamicPrefsMessage.sendToTarget();
    }

    private final static int MSG_UPDATE_DYNAMIC_PREFS = 1;

    // handler to update UI on main thread
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {

            switch (inputMessage.what) {
                case MSG_UPDATE_DYNAMIC_PREFS:
                    // update http server preferences on main thread
                    HttpStreamServer httpStreamServer = binder.getHttpStreamServer();

                    final ListPreference audioEncodingPref = findPreference(R.string.prefs_key_audio_encoding);
                    final Preference httpServerPref = findPreference(R.string.prefs_key_http_server);
                    final Preference httpClientsPref = findPreference(R.string.prefs_key_http_clients);
                    final Preference audioApiPref = findPreference(R.string.prefs_key_audio_api);

                    if (httpStreamServer == null) {
                        audioEncodingPref.setSelectable(true);
                        httpServerPref.setSummary(R.string.prefs_default_summary_http_server);
                        httpClientsPref.setSummary(R.string.prefs_default_summary_http_clients);
                    } else {
                        audioEncodingPref.setSelectable(false);
                        httpServerPref.setSummary(binder.getHttpStreamServer().getStreamUrl());
                        httpClientsPref.setSummary(Integer.toString(binder.getHttpStreamServer().getClientCount()));
                    }

                    audioApiPref.setSummary(binder.getAudioApi());
                    break;
                default:
                    // Pass along other messages from the UI
                    super.handleMessage(inputMessage);
            }
        }
    };

    // create http server listener to update state of http server preferences via Handler on main thread
    private HttpStreamServerListener httpStreamServerListener = new HttpStreamServerListener() {
        @Override
        public void onStarted() {
            sendUpdateDynamicPreferencesMessage();
        }

        @Override
        public void onStopped() {
            sendUpdateDynamicPreferencesMessage();
        }

        @Override
        public void onClientConnected(HttpClient client) {
            sendUpdateDynamicPreferencesMessage();
        }

        @Override
        public void onClientDisconnected(HttpClient client) {
            sendUpdateDynamicPreferencesMessage();
        }
    };
}

