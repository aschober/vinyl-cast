/*
 * Copyright 2018 The Android Open Source Project
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

#ifndef OBOE_NATIVEAUDIOENGINE_H
#define OBOE_NATIVEAUDIOENGINE_H

#include <jni.h>
#include <oboe/Oboe.h>
#include <string>
#include <thread>

class NativeAudioEngine : public oboe::AudioStreamCallback {
   public:
    NativeAudioEngine();
    ~NativeAudioEngine();
    void setRecordingDeviceId(int32_t deviceId);
    void setPlaybackDeviceId(int32_t deviceId);

    void prepareRecording();
    void startRecording();
    void stopRecording();

    /*
     * oboe::AudioStreamCallback interface implementation
     */
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream,
                                          void *audioData, int32_t numFrames) override;
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

    bool setAudioApi(oboe::AudioApi);
    bool isAAudioSupported(void);

    void setAudioDataListener(JNIEnv *env, jobject instance, jobject callback);
    int32_t getSampleRate();
    int32_t getChannelCount();

   private:
    FullDuplexPassthru mFullDuplexPassthru;
    bool mIsRecording = false;
    bool mSkipLocalPlayback = false;
    int32_t mRecordingDeviceId = oboe::kUnspecified;
    int32_t mPlaybackDeviceId = oboe::kUnspecified;
    oboe::AudioFormat mFormat = oboe::AudioFormat::I16;
    int32_t mSampleRate = oboe::kUnspecified;
    int32_t mInputChannelCount = oboe::ChannelCount::Stereo;
    int32_t mOutputChannelCount = oboe::ChannelCount::Stereo;

    oboe::ManagedStream mRecordingStream;
    oboe::ManagedStream mPlayStream;

    oboe::AudioApi mAudioApi = oboe::AudioApi::AAudio;

    oboe::Result openRecordingStream();
    oboe::Result openPlaybackStream();

    oboe::Result startStream(oboe::ManagedStream &stream);
    void stopStream(oboe::ManagedStream &stream);
    void closeStream(oboe::ManagedStream &stream);

    oboe::Result openAllStreams();
    oboe::Result startAllStreams();
    void closeAllStreams();

    oboe::AudioStreamBuilder *setupCommonStreamParameters(
        oboe::AudioStreamBuilder *builder);
    oboe::AudioStreamBuilder *setupRecordingStreamParameters(
        oboe::AudioStreamBuilder *builder);
    oboe::AudioStreamBuilder *setupPlaybackStreamParameters(
        oboe::AudioStreamBuilder *builder);
    void warnIfNotLowLatency(oboe::ManagedStream &stream);
};

#endif  // OBOE_NATIVEAUDIOENGINE_H
