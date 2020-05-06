package com.google.sample.audio_device;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class AudioDevicePreference extends ListPreference {
    private static final String TAG = AudioDeviceSpinner.class.getName();

    private static final int AUDIO_DEVICE_ID_NONE = -1;
    private static final int AUDIO_DEVICE_ID_AUTO_SELECT = 0;

    private final Context mContext;
    private AudioDeviceAdapter mDeviceAdapter;
    private int mDirectionType;
    private Spinner mSpinner;
    private AudioManager mAudioManager;

    private final AdapterView.OnItemSelectedListener mItemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            if (position >= 0) {
                String value = getEntryValues()[position].toString();
                if (!value.equals(getValue()) && callChangeListener(value)) {
                    setValue(value);
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // noop
        }
    };

    public AudioDevicePreference(Context context) {
        this(context, null);
    }

    public AudioDevicePreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.dropdownPreferenceStyle);
    }

    public AudioDevicePreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public AudioDevicePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mDeviceAdapter = createAdapter(mContext);

        // Add a default entry to the list
        mDeviceAdapter.add(new AudioDeviceListEntry(AUDIO_DEVICE_ID_AUTO_SELECT, AudioDeviceInfo.TYPE_UNKNOWN,
                context.getString(R.string.auto_select)));
    }

    @Override
    protected void onClick() {
        // first check onPreferenceClick before continuing
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            return;
        }

        // open spinner instead of a dialog
        if (mSpinner != null) {
            mSpinner.performClick();
        }
    }

    protected AudioDeviceAdapter createAdapter(Context context) {
        AudioDeviceAdapter audioDeviceAdapter = new AudioDeviceAdapter(context);

        return audioDeviceAdapter;
    }

    @Override
    public void setValueIndex(int index) {
        setValue(getEntryValues()[index].toString());
    }

    @Override
    protected void notifyChanged() {
        super.notifyChanged();
        // When setting a SummaryProvider for this Preference, this method may be called before
        // mAdapter has been set in ListPreference's constructor.
        if (mDeviceAdapter != null) {
            mDeviceAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        mSpinner = view.itemView.findViewById(R.id.spinner);
        mSpinner.setAdapter(mDeviceAdapter);
        mSpinner.setOnItemSelectedListener(mItemSelectedListener);
        mSpinner.setSelection(findSpinnerIndexOfValue(getValue()));
        super.onBindViewHolder(view);
    }

    private int findSpinnerIndexOfValue(String value) {
        CharSequence[] entryValues = getEntryValues();
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) {
                if (entryValues[i].equals(value)) {
                    return i;
                }
            }
        }
        return Spinner.INVALID_POSITION;
    }

    @TargetApi(23)
    public void setDirectionType(int directionType){
        this.mDirectionType = directionType;

        if (directionType == AudioManager.GET_DEVICES_OUTPUTS) {
            // Add an entry for NONE to the list
            mDeviceAdapter.insert(new AudioDeviceListEntry(AUDIO_DEVICE_ID_NONE, AudioDeviceInfo.TYPE_UNKNOWN,
                    mContext.getString(R.string.none)), 0);
        }

        setupAudioDeviceCallback();
    }

    @TargetApi(23)
    private void setupAudioDeviceCallback(){

        // Note that we will immediately receive a call to onDevicesAdded with the list of
        // devices which are currently connected.
        mAudioManager.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {

                List<AudioDeviceListEntry> deviceList =
                        AudioDeviceListEntry.createFilteredListFrom(addedDevices, mDirectionType, new HashSet<>(Arrays.asList(AudioDeviceInfo.TYPE_TELEPHONY)));
                if (deviceList.size() > 0){
                    mDeviceAdapter.addAll(deviceList);
                }

                updateEntries();
            }

            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {

                List<AudioDeviceListEntry> deviceList =
                        AudioDeviceListEntry.createListFrom(removedDevices, mDirectionType);
                for (AudioDeviceListEntry entry : deviceList){
                    mDeviceAdapter.remove(entry);
                }

                updateEntries();
            }
        }, null);
    }

    private void updateEntries() {
        CharSequence[] entries = new CharSequence[mDeviceAdapter.getCount()];
        CharSequence[] entryValues = new CharSequence[mDeviceAdapter.getCount()];
        for (int i = 0; i < mDeviceAdapter.getCount(); i++) {
            mDeviceAdapter.getItem(0);
            entries[i] = mDeviceAdapter.getItem(i).getName();
            entryValues[i] = Long.toString(mDeviceAdapter.getItemId(i));
            Log.d(TAG, "audio device: [" + entryValues[i] + "] " + entries[i]);
        }
        setEntries(entries);
        setEntryValues(entryValues);
        notifyChanged();
    }
}
