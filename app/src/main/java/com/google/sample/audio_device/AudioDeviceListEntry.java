package com.google.sample.audio_device;
/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.TargetApi;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * POJO which represents basic information for an audio device.
 *
 * Example: id: 8, type: 2, deviceName: "built-in speaker"
 */
public class AudioDeviceListEntry {

    private int mId;
    private int mType;
    private String mName;

    public AudioDeviceListEntry(int deviceId, int type, String deviceName){
        mId = deviceId;
        mType = type;
        mName = deviceName;
    }

    public int getId() {
        return mId;
    }

    public int getType() {
        return mType;
    }

    public String getName(){
        return mName;
    }

    public String toString(){
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AudioDeviceListEntry that = (AudioDeviceListEntry) o;

        if (mId != that.mId) return false;
        if (mType != that.mType) return false;
        return mName != null ? mName.equals(that.mName) : that.mName == null;
    }

    @Override
    public int hashCode() {
        int result = mId + mType;
        result = 31 * result + (mName != null ? mName.hashCode() : 0);
        return result;
    }

    /**
     * Create a list of AudioDeviceListEntry objects from a list of AudioDeviceInfo objects.
     *
     * @param devices A list of {@Link AudioDeviceInfo} objects
     * @param directionType Only audio devices with this direction will be included in the list.
     *                      Valid values are GET_DEVICES_ALL, GET_DEVICES_OUTPUTS and
     *                      GET_DEVICES_INPUTS.
     * @return A list of AudioDeviceListEntry objects
     */
    @TargetApi(23)
    static List<AudioDeviceListEntry> createListFrom(AudioDeviceInfo[] devices, int directionType){

        List<AudioDeviceListEntry> listEntries = new Vector<>();
        for (AudioDeviceInfo info : devices) {
            if (directionType == AudioManager.GET_DEVICES_ALL ||
                    (directionType == AudioManager.GET_DEVICES_OUTPUTS && info.isSink()) ||
                    (directionType == AudioManager.GET_DEVICES_INPUTS && info.isSource())) {
                listEntries.add(new AudioDeviceListEntry(info.getId(), info.getType(),
                        info.getProductName() + " " + AudioDeviceInfoConverter.typeToString(info.getType())));
            }
        }
        return listEntries;
    }

    @TargetApi(23)
    static List<AudioDeviceListEntry> createFilteredListFrom(AudioDeviceInfo[] devices, int directionType, Set<Integer> filteredTypes){

        List<AudioDeviceListEntry> listEntries = new Vector<>();
        for (AudioDeviceInfo info : devices) {
            if (directionType == AudioManager.GET_DEVICES_ALL ||
                    (directionType == AudioManager.GET_DEVICES_OUTPUTS && info.isSink()) ||
                    (directionType == AudioManager.GET_DEVICES_INPUTS && info.isSource())) {
                if (filteredTypes.contains(info.getType())) {
                    continue;
                }
                listEntries.add(new AudioDeviceListEntry(info.getId(), info.getType(),
                        info.getProductName() + ": " + AudioDeviceInfoConverter.typeToString(info.getType())));
            }
        }
        return listEntries;
    }
}
