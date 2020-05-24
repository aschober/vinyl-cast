package tech.schober.vinylcast.ui.settings;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.sample.audio_device.AudioDeviceListEntry;
import com.google.sample.audio_device.AudioDevicePreference;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    private static final Set<Integer> RECORDING_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_MIC));
    private static final Set<Integer> PLAYBACK_DEVICES_BUILTIN = new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));

    private VinylCastService.VinylCastBinder binder;

    private Preference.OnPreferenceClickListener disabledPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (binder != null && binder.isRecording()) {
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

    private Preference.OnPreferenceChangeListener audioDeviceOnChangeListener = (preference, newValue) -> {
        Log.d(TAG, "audioDeviceOnChangeListener: " + preference.getKey() + " - " + newValue);

        AudioDevicePreference audioDevicePreference = (AudioDevicePreference) preference;
        AudioDevicePreference recordingDevicePref = findPreference(R.string.prefs_key_recording_device_id);
        AudioDevicePreference playbackDevicePref = findPreference(R.string.prefs_key_local_playback_device_id);

        AudioDeviceListEntry recordingDevice = recordingDevicePref.getSelectedAudioDeviceListEntry();
        AudioDeviceListEntry playbackDevice = playbackDevicePref.getSelectedAudioDeviceListEntry();

        if (preference.getKey().equals(getString(R.string.prefs_key_recording_device_id))) {
            recordingDevice = recordingDevicePref.getAudioDeviceListEntry(Integer.valueOf((String) newValue));
        } else if (preference.getKey().equals(getString(R.string.prefs_key_local_playback_device_id))) {
            playbackDevice = playbackDevicePref.getAudioDeviceListEntry(Integer.valueOf((String) newValue));
        }

        if (recordingDevice == null || playbackDevice == null ||
                recordingDevice.getId() == AudioRecordStreamProvider.AUDIO_DEVICE_ID_AUTO_SELECT ||
                playbackDevice.getId() == AudioRecordStreamProvider.AUDIO_DEVICE_ID_AUTO_SELECT ||
                playbackDevice.getId() == AudioRecordStreamProvider.AUDIO_DEVICE_ID_NONE) {
            return true;
        }

        if (RECORDING_DEVICES_BUILTIN.contains(recordingDevice.getType()) &&
                (PLAYBACK_DEVICES_BUILTIN.contains(playbackDevice.getType()))) {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.alert_builtin_warning_title)
                    .setMessage(R.string.alert_builtin_warning_message)
                    // Specifying a listener allows you to take an action before dismissing the dialog.
                    // The dialog is automatically dismissed when a dialog button is clicked.
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        audioDevicePreference.setValue((String) newValue);
                    })
                    .setIcon(R.drawable.ic_warning_accent2_24dp)
                    .show();
        }
        return true;
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
            recordingDevicePref.setOnPreferenceChangeListener(audioDeviceOnChangeListener);
        }
        if (playbackDevicePref != null) {
            playbackDevicePref.setDirectionType(AudioManager.GET_DEVICES_OUTPUTS);
            playbackDevicePref.setOnPreferenceClickListener(disabledPreferenceClickListener);
            playbackDevicePref.setOnPreferenceChangeListener(audioDeviceOnChangeListener);
        }
        if (lowLatencyPref != null) {
            lowLatencyPref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (audioEncodingPref != null) {
            audioEncodingPref.setEntries(R.array.prefs_audio_encoding_entries);
            audioEncodingPref.setEntryValues(R.array.prefs_audio_encoding_entry_values);
            audioEncodingPref.setOnPreferenceClickListener(disabledPreferenceClickListener);
        }
        if (feedbackPref != null && BuildConfig.FLAVOR.equals("playstore")) {
            feedbackPref.setVisible(true);
            feedbackPref.setOnPreferenceClickListener(preference -> {
                try {
                    // Use reflection to get Instabug since only available in playstore product flavor
                    Class instabugClazz = Class.forName("com.instabug.library.Instabug");
                    if (binder != null) {
                        //Instabug.setUserAttribute("Audio API", getAudioApiVersionString());
                        Method setUserAttributeMethod = instabugClazz.getMethod("setUserAttribute", String.class, String.class);
                        setUserAttributeMethod.invoke(null, "Audio API", getAudioApiVersionString());
                    }
                    //Instabug.show();
                    Method showMethod = instabugClazz.getMethod("show");
                    showMethod.invoke(null);
                } catch (ReflectiveOperationException e) {
                    e.printStackTrace();
                }
                return true;
            });
        }
        if (androidApiLevelPref != null) {
            androidApiLevelPref.setSummaryProvider(preference ->
                    Integer.toString(Build.VERSION.SDK_INT)
            );
        }
        if (appVersionPref != null) {
            appVersionPref.setSummaryProvider(preference ->
                    String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            );
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

    public static void printViewHierarchy(ViewGroup vg, String prefix) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View v = vg.getChildAt(i);
            String desc = prefix + " | " + "[" + i + "/" + (vg.getChildCount()-1) + "] "+ v.getClass().getSimpleName() + " " + v.getId();
            Log.v(TAG, desc);

            if (v instanceof ViewGroup) {
                printViewHierarchy((ViewGroup)v, desc);
            }
        }
    }

    class UpdateDynamicPrefsRunnable implements Runnable {
        @Override
        public void run() {
            // update audio and http server preferences on main thread
            boolean isRecording = (binder != null && binder.isRecording());

            InfoButtonListPreferencePref audioEncodingPref = findPreference(R.string.prefs_key_audio_encoding);
            Preference httpServerPref = findPreference(R.string.prefs_key_http_server);
            Preference httpClientsPref = findPreference(R.string.prefs_key_http_clients);
            Preference audioApiPref = findPreference(R.string.prefs_key_audio_api);

            // handle audio encoding info button
            if (isRecording) {
                audioEncodingPref.setShowInfoButton(true);
                audioEncodingPref.setImageButtonClickListener(listener ->
                        getAudioEncodingInfoDialog(Integer.valueOf(audioEncodingPref.getValue())).show()
                );
            } else {
                audioEncodingPref.setShowInfoButton(false);
                audioEncodingPref.setImageButtonClickListener(null);
            }
            // trigger refresh of fragment listview holding audioEncodingPref to make sure the info
            // button gets redrawn with latest state
            audioEncodingPref.notifyPreferenceListItemChanged(SettingsFragment.this);

            // handle updating read-only pref summaries
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
        int bufferAudioDelay;
        int titleResId;

        switch (audioEncoding) {
            case AUDIO_ENCODING_AAC:
                sampleRateKhz = ConvertAudioStreamProvider.getConvertAudioStreamSampleRate() / 1000f;
                channelCount = ConvertAudioStreamProvider.getConvertAudioStreamChannelCount();
                bitRateKbps = ConvertAudioStreamProvider.getConvertAudioStreamBitRate() / 1000f;
                bufferAudioDelay = 20;
                titleResId = R.string.alert_encodingdetails_aac_title;
                break;
            default:
                sampleRateKhz = AudioRecordStreamProvider.getAudioRecordStreamSampleRate() / 1000f;
                channelCount = AudioRecordStreamProvider.getAudioRecordStreamChannelCount();
                bitRateKbps = AudioRecordStreamProvider.getAudioRecordStreamBitRate() / 1000f;
                bufferAudioDelay = 10;
                titleResId = R.string.alert_encodingdetails_wav_title;
                break;
        }

        String message = String.format(Locale.getDefault(),
                "Sample Rate: %s kHz\nChannels: %d\nBitrate: %s kbps\n\nNote: ~%dsecs audio delay due to player buffering on Cast device.",
                new DecimalFormat("#.#").format(sampleRateKhz),
                channelCount,
                new DecimalFormat("#.#").format(bitRateKbps),
                bufferAudioDelay);

        return new AlertDialog.Builder(getContext())
                .setTitle(titleResId)
                .setMessage(message)
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(R.drawable.ic_info_outline_accent2_24dp)
                .create();
    }
}

