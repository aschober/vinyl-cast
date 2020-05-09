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
 *
 * Based on LiveEffectEngine.cpp
 * https://github.com/google/oboe/tree/0a78e50b64/samples/LiveEffect/src/main/cpp/LiveEffectEngine.cpp
 *
 * Modifications Copyright 2020 Allen Schober
 *
 */

#include <cassert>
#include <logging_macros.h>
#include <climits>

#include "NativeAudioEngine.h"

NativeAudioEngine::NativeAudioEngine(JNIEnv *env) {
    assert(mOutputChannelCount == mInputChannelCount);
    // cache pointer to JavaVM based on initial JNIEnv.
    jint rs = env->GetJavaVM(&mJavaVm);
    assert (rs == JNI_OK);
}

NativeAudioEngine::~NativeAudioEngine() {
    stopRecording(nullptr);
}

void NativeAudioEngine::setRecordingDeviceId(int32_t deviceId) {
    mRecordingDeviceId = deviceId;
}

void NativeAudioEngine::setPlaybackDeviceId(int32_t deviceId) {
    if (deviceId == -1) {
        mFullDuplexPassthru.setSkipLocalPlayback(true);
        deviceId = 0;
    } else {
        mFullDuplexPassthru.setSkipLocalPlayback(false);
    }
    mPlaybackDeviceId = deviceId;
}

bool NativeAudioEngine::isAAudioSupportedAndRecommended() {
    oboe::AudioStreamBuilder builder;
    return (builder.isAAudioSupported() && builder.isAAudioRecommended());
}

bool NativeAudioEngine::setAudioApi(oboe::AudioApi api) {
    if (mIsRecording) {
        LOGW("Recording already in progress - ignoring this setAudioApi request");
        return false;
    }

    mAudioApi = api;
    return true;
}

bool NativeAudioEngine::setLowLatency(bool lowLatency) {
    if (mIsRecording) {
        LOGW("Recording already in progress - ignoring this setLowLatency request");
        return false;
    }

    mLowLatency = lowLatency;
    return true;
}

void NativeAudioEngine::setAudioDataListener(JNIEnv *env, jobject instance, jobject callbackObject) {
    //declare ref java class
    jclass jClassAudioDataListener = env->GetObjectClass(callbackObject);

    //declare java method id
    jmethodID jMethodIdOnAudioData = env->GetMethodID(jClassAudioDataListener, "onAudioData", "([B)V");

    //check null
    if(jMethodIdOnAudioData == 0){
        LOGE("jMethodID for onAudioData not found");
        return;
    }

    JavaVM* javaVm;
    env->GetJavaVM(&javaVm); //store JavaVM reference for later use of env
    mCallbackObject = env->NewGlobalRef(callbackObject);
    mFullDuplexPassthru.setAudioDataCallback(javaVm, mCallbackObject, jMethodIdOnAudioData);
}

int32_t NativeAudioEngine::getSampleRate() {
    if (mRecordingStream && mPlayStream) {
        return mSampleRate;
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
        return -1;
    }
}

int32_t NativeAudioEngine::getChannelCount() {
    if (mRecordingStream && mPlayStream) {
        return mInputChannelCount;
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
        return -1;
    }
}

int32_t NativeAudioEngine::getBitRate() {
    if (mRecordingStream && mPlayStream) {
        int bitsPerSample = 0;
        if (mFormat == oboe::AudioFormat::I16) {
            bitsPerSample = 16;
        } else if (mFormat == oboe::AudioFormat::Float) {
            bitsPerSample = 32;
        }
        return mSampleRate * mInputChannelCount * bitsPerSample;
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
        return -1;
    }
}

int32_t NativeAudioEngine::getAudioApi() {
    if (mRecordingStream && mPlayStream) {
        return static_cast<int32_t>(mAudioApi);
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
        return -1;
    }
}

const char * NativeAudioEngine::getOboeVersion() {
    return oboe::Version::Text;
}

bool NativeAudioEngine::prepareRecording(JNIEnv *env) {
    LOGD("prepareRecording");
    if (mIsRecording) {
        LOGW("Recording already in progress - ignoring this prepareRecording request");
        return false;
    }

    // Note: The order of stream creation is important. We create the playback
    // stream first, then use properties from the playback stream
    // (e.g. sample rate) to create the recording stream. By matching the
    // properties we should get the lowest latency path
    oboe::AudioStreamBuilder inBuilder, outBuilder;
    setupPlaybackStreamParameters(&outBuilder);
    oboe::Result result = outBuilder.openManagedStream(mPlayStream);
    if (result != oboe::Result::OK) {
        return false;
    }
    warnIfNotLowLatency(mPlayStream);
    mSampleRate = mPlayStream->getSampleRate();

    setupRecordingStreamParameters(&inBuilder);
    result = inBuilder.openManagedStream(mRecordingStream);
    if (result != oboe::Result::OK) {
        closeStream(mPlayStream);
        return false;
    }
    warnIfNotLowLatency(mRecordingStream);
    mAudioApi = mRecordingStream->getAudioApi();

    mFullDuplexPassthru.setInputStream(mRecordingStream.get());
    mFullDuplexPassthru.setOutputStream(mPlayStream.get());

    return true;
}

bool NativeAudioEngine::startRecording(JNIEnv *env) {
    if (mIsRecording) {
        LOGW("Recording already in progress - ignoring this startRecording request");
        return false;
    }

    if (mRecordingStream && mPlayStream) {
        mIsRecording = true;
        oboe::Result result = mFullDuplexPassthru.start();
        return (result == oboe::Result::OK);
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
        return false;
    }
}

/**
 * Stops and closes the playback and recording streams.
 */
bool NativeAudioEngine::stopRecording(JNIEnv *env) {
    if(!mIsRecording) {
        LOGW("Recording not in progress, but going to try stopping anyway.");
    }

    mFullDuplexPassthru.stop();

    // if JNIEnv not provided, get one from cached JavaVM
    if (env == nullptr) {
        LOGW("JNIEnv not provided so getting a new one");
        jint rs = mJavaVm->AttachCurrentThread(&env, NULL);
        assert (rs == JNI_OK);
    }
    env->DeleteGlobalRef(mCallbackObject);

    /*
     * Note: The order of events is important here.
     * The playback stream must be closed before the recording stream. If the
     * recording stream were to be closed first the playback stream's
     * callback may attempt to read from the recording stream
     * which would cause the app to crash since the recording stream would be
     * null.
     */
    if (mPlayStream != nullptr) {
        closeStream(mPlayStream);
        mPlayStream = nullptr;
    }
    if (mRecordingStream != nullptr) {
        closeStream(mRecordingStream);
        mRecordingStream = nullptr;
    }

    mIsRecording = false;
    return true;
}

/**
 * Sets the stream parameters which are specific to recording including device
 * id and the dataCallback function, which must be set for low latency
 * playback.
 * @param builder The recording stream builder
 */
oboe::AudioStreamBuilder *NativeAudioEngine::setupRecordingStreamParameters(
    oboe::AudioStreamBuilder *builder) {
    builder->setCallback(nullptr)
        ->setDeviceId(mRecordingDeviceId)
        ->setDirection(oboe::Direction::Input)
        ->setSampleRate(mSampleRate)
        ->setChannelCount(mInputChannelCount);
    return setupCommonStreamParameters(builder);
}

/**
 * Sets the stream parameters which are specific to playback, including device
 * id and the sample rate which is determined from the recording stream.
 * @param builder The playback stream builder
 */
oboe::AudioStreamBuilder *NativeAudioEngine::setupPlaybackStreamParameters(
    oboe::AudioStreamBuilder *builder) {
    builder->setCallback(this)
        ->setDeviceId(mPlaybackDeviceId)
        ->setDirection(oboe::Direction::Output)
        ->setChannelCount(mOutputChannelCount);

    return setupCommonStreamParameters(builder);
}

/**
 * Set the stream parameters which are common to both recording and playback
 * streams.
 * @param builder The playback or recording stream builder
 */
oboe::AudioStreamBuilder *NativeAudioEngine::setupCommonStreamParameters(
    oboe::AudioStreamBuilder *builder) {
    // Can request EXCLUSIVE mode to get the lowest possible latency.
    // If EXCLUSIVE mode isn't available the builder will fall back to SHARED
    // mode.
    builder->setAudioApi(mAudioApi)
           ->setFormat(mFormat);

    if (mLowLatency) {
        builder->setSharingMode(oboe::SharingMode::Exclusive)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder->setSharingMode(oboe::SharingMode::Shared)
               ->setPerformanceMode(oboe::PerformanceMode::None);
    }

    return builder;
}

/**
 * Close the stream. AudioStream::close() is a blocking call so
 * the application does not need to add synchronization between
 * onAudioReady() function and the thread calling close().
 * [the closing thread is the UI thread in this sample].
 * @param stream the stream to close
 */
void NativeAudioEngine::closeStream(oboe::ManagedStream &stream) {
    if (stream) {
        oboe::Result result = stream->close();
        if (result != oboe::Result::OK) {
            LOGE("Error closing stream. %s", oboe::convertToText(result));
        }
        LOGW("Successfully closed stream");
        stream.reset();
    }
}

/**
 * Warn in logcat if non-low latency stream is created
 * @param stream: newly created stream
 *
 */
void NativeAudioEngine::warnIfNotLowLatency(oboe::ManagedStream &stream) {
    if (stream->getPerformanceMode() != oboe::PerformanceMode::LowLatency) {
        LOGW(
            "Stream is NOT low latency."
            "Check your requested format, sample rate and channel count");
    }
}

/**
 * Handles playback stream's audio request. In this sample, we simply block-read
 * from the record stream for the required samples.
 *
 * @param oboeStream: the record stream that providing available samples
 * @param audioData:  the buffer containing audio samples from record stream
 * @param numFrames:  number of frames provided in audioData buffer
 * @return: DataCallbackResult::Continue.
 */
oboe::DataCallbackResult NativeAudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    return mFullDuplexPassthru.onAudioReady(oboeStream, audioData, numFrames);
}

/**
 * Oboe notifies the application for "about to close the stream".
 *
 * @param oboeStream: the stream to close
 * @param error: oboe's reason for closing the stream
 */
void NativeAudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream,
                                          oboe::Result error) {
    LOGE("%s stream Error before close: %s",
         oboe::convertToText(oboeStream->getDirection()),
         oboe::convertToText(error));
}

/**
 * Oboe notifies application that "the stream is closed"
 *
 * @param oboeStream: the stream to close
 * @param error: oboe's reason for closing the stream
 */
void NativeAudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream,
                                         oboe::Result error) {
    LOGE("%s stream Error after close: %s",
         oboe::convertToText(oboeStream->getDirection()),
         oboe::convertToText(error));
}
