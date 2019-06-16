/**
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

#include "NativeAudioEngine.h"
#include <assert.h>
#include <logging_macros.h>
#include <climits>

/**
 * Duplex is not very stable right after starting up:
 *   the callbacks may not be happening at the right times
 * The time to get it stable varies on different systems. Half second
 * is used for this sample, during the time this sample plays silence.
 */
const float kSystemWarmupTime = 0.5f;

static JavaVM* mJavaVm;
static jbyteArray mAudioCallbackBuffer;
static jobject mAudioCallbackInstance;
static jmethodID mAudioCallbackMethod;

NativeAudioEngine::NativeAudioEngine() {
    assert(mOutputChannelCount == mInputChannelCount);
}

NativeAudioEngine::~NativeAudioEngine() {
    stopStream(mPlayStream);
    stopStream(mRecordingStream);

    closeStream(mPlayStream);

    closeStream(mRecordingStream);
}

void NativeAudioEngine::setRecordingDeviceId(int32_t deviceId) {
    mRecordingDeviceId = deviceId;
}

void NativeAudioEngine::setPlaybackDeviceId(int32_t deviceId) {
    if (deviceId == -1) {
        mSkipLocalPlayback = true;
        deviceId = 0;
    } else {
        mSkipLocalPlayback = false;
    }
    mPlaybackDeviceId = deviceId;
}

bool NativeAudioEngine::isAAudioSupported() {
    oboe::AudioStreamBuilder builder;
    return builder.isAAudioSupported();
}
bool NativeAudioEngine::setAudioApi(oboe::AudioApi api) {
    if (mIsRecording) return false;

    mAudioApi = api;
    return true;
}

void NativeAudioEngine::setAudioDataListener(JNIEnv *env, jobject instance, jobject callbackObject) {
    //declare ref java class
    jclass jClassAudioDataListener = env->GetObjectClass(callbackObject);

    //declare java method id
    jmethodID jMethodIdOnAudioData = env->GetMethodID(jClassAudioDataListener, "onAudioData", "([B)V");

    //check null
    if(jMethodIdOnAudioData == 0){
        return;
    }

    env->GetJavaVM(&mJavaVm); //store JavaVM reference for later use of env
    mAudioCallbackInstance = env->NewGlobalRef(callbackObject);
    mAudioCallbackMethod = jMethodIdOnAudioData;
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

void NativeAudioEngine::prepareRecording() {
    if (!mIsRecording) {
        openAllStreams();
    } else {
        LOGW("Recording already in progress - ignoring this request");
    }
}

void NativeAudioEngine::startRecording() {
    if (mIsRecording) {
        LOGW("Recording already in progress - ignoring this request");
    } else if (mRecordingStream && mPlayStream) {
        mIsRecording = true;
        startAllStreams();
    } else {
        LOGE("Recording and/or Playback streams not created yet. Need to call prepareRecording() first.");
    }
}

void NativeAudioEngine::stopRecording() {
    if (mIsRecording) {
        closeAllStreams();
        mIsRecording = false;
    } else {
        LOGW("Recording not in progress - ignoring this request");
    }
}


void NativeAudioEngine::openAllStreams() {
    // Note: The order of stream creation is important. We create the playback
    // stream first, then use properties from the playback stream
    // (e.g. sample rate) to create the recording stream. By matching the
    // properties we should get the lowest latency path
    openRecordingStream();
    openPlaybackStream();

    if (mJavaVm != NULL) {
        JNIEnv* env;
        mJavaVm->AttachCurrentThread(&env, NULL);
    }
}

void NativeAudioEngine::startAllStreams() {
    // Now start the recording stream first so that we can write from it during
    // the recording stream's dataCallback
    if (mRecordingStream && mPlayStream) {
        startStream(mRecordingStream);
        startStream(mPlayStream);
    } else {
        LOGE("Failed to create recording (%p) and/or playback (%p) stream",
             mRecordingStream, mPlayStream);
        closeAllStreams();
    }
}

/**
 * Stops and closes the playback and recording streams.
 */
void NativeAudioEngine::closeAllStreams() {
    /**
     * Note: The order of events is important here.
     * The recording stream must be closed before the playback stream. If the
     * playback stream were to be closed first, the recording stream's
     * callback may attempt to write to the playback stream
     * which would cause the app to crash since the playback stream would be
     * null.
     */

    if (mRecordingStream != nullptr) {
        closeStream(mRecordingStream);
        mRecordingStream = nullptr;
    }

    if (mPlayStream != nullptr) {
        closeStream(mPlayStream);  // Calling close will also stop the stream
        mPlayStream = nullptr;
    }
}

/**
 * Creates an audio stream for recording. The audio device used will depend on
 * mRecordingDeviceId.
 * If the value is set to oboe::Unspecified then the default recording device
 * will be used.
 */
void NativeAudioEngine::openRecordingStream() {
    // To create a stream we use a stream builder. This allows us to specify all
    // the parameters for the stream prior to opening it
    oboe::AudioStreamBuilder builder;

    setupRecordingStreamParameters(&builder);

    // Now that the parameters are set up we can open the stream
    oboe::Result result = builder.openStream(&mRecordingStream);
    if (result == oboe::Result::OK && mRecordingStream) {
        mSampleRate = mRecordingStream->getSampleRate();

        assert(mRecordingStream->getFormat() == oboe::AudioFormat::I16);
        assert(mOutputChannelCount == mRecordingStream->getChannelCount());

        // warnIfNotLowLatency(mRecordingStream);
    } else {
        LOGE("Failed to create recording stream. Error: %s",
             oboe::convertToText(result));
    }
}

/**
 * Creates an audio stream for playback. The audio device used will depend on
 * mPlaybackDeviceId.
 * If the value is set to oboe::Unspecified then the default playback device
 * will be used.
 */
void NativeAudioEngine::openPlaybackStream() {
    oboe::AudioStreamBuilder builder;

    setupPlaybackStreamParameters(&builder);
    oboe::Result result = builder.openStream(&mPlayStream);
    if (result == oboe::Result::OK && mPlayStream) {
        mSystemStartupFrames =
            static_cast<uint64_t>(mSampleRate * kSystemWarmupTime);
        mProcessedFrameCount = 0;

        assert(mPlayStream->getChannelCount() == mInputChannelCount);
        assert(mPlayStream->getSampleRate() == mSampleRate);
        assert(mPlayStream->getFormat() == oboe::AudioFormat::I16);

        // warnIfNotLowLatency(mPlayStream);

    } else {
        LOGE("Failed to create playback stream. Error: %s",
             oboe::convertToText(result));
    }
}

/**
 * Sets the stream parameters which are specific to recording including device
 * id and the dataCallback function, which must be set for low latency
 * playback.
 * @param builder The recording stream builder
 */
oboe::AudioStreamBuilder *NativeAudioEngine::setupRecordingStreamParameters(
    oboe::AudioStreamBuilder *builder) {
    builder->setCallback(this)
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
    builder->setCallback(nullptr)
        ->setDeviceId(mPlaybackDeviceId)
        ->setDirection(oboe::Direction::Output)
        ->setSampleRate(mSampleRate)
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
        ->setFormat(mFormat)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setPerformanceMode(oboe::PerformanceMode::None);
    return builder;
}

void NativeAudioEngine::startStream(oboe::AudioStream *stream) {
    assert(stream);
    if (stream) {
        oboe::Result result = stream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Error starting stream. %s", oboe::convertToText(result));
        }
    }
}

void NativeAudioEngine::stopStream(oboe::AudioStream *stream) {
    if (stream) {
        oboe::Result result = stream->stop(0L);
        if (result != oboe::Result::OK) {
            LOGE("Error stopping stream. %s", oboe::convertToText(result));
        }
    }
}

/**
 * Close the stream. AudioStream::close() is a blocking call so
 * the application does not need to add synchronization between
 * onAudioReady() function and the thread calling close().
 * [the closing thread is the UI thread in this sample].
 * @param stream the stream to close
 */
void NativeAudioEngine::closeStream(oboe::AudioStream *stream) {
    if (stream) {
        oboe::Result result = stream->close();
        if (result != oboe::Result::OK) {
            LOGE("Error closing stream. %s", oboe::convertToText(result));
        }
    }
}

/**
 * Restart the streams. During the restart operation subsequent calls to this
 * method will output a warning.
 */
void NativeAudioEngine::restartStreams() {
    LOGI("Restarting streams");

    if (mRestartingLock.try_lock()) {
        closeAllStreams();
        openAllStreams();
        mRestartingLock.unlock();
    } else {
        LOGW(
            "Restart stream operation already in progress - ignoring this "
            "request");
        // We were unable to obtain the restarting lock which means the restart
        // operation is currently
        // active. This is probably because we received successive "stream
        // disconnected" events.
        // Internal issue b/63087953
    }
}

/**
 * Warn in logcat if non-low latency stream is created
 * @param stream: newly created stream
 *
 */
void NativeAudioEngine::warnIfNotLowLatency(oboe::AudioStream *stream) {
    if (stream->getPerformanceMode() != oboe::PerformanceMode::LowLatency) {
        LOGW(
            "Stream is NOT low latency."
            "Check your requested format, sample rate and channel count");
    }
}

/**
 * Handles recording stream's audio request. In this method, we simply block-write
 * to the playback stream for the required samples.
 *
 * @param oboeStream: the record stream that providing available samples
 * @param audioData:  the buffer containing audio samples from record stream
 * @param numFrames:  number of frames provided in audioData buffer
 * @return: DataCallbackResult::Continue.
 */
oboe::DataCallbackResult NativeAudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    assert(oboeStream == mRecordingStream);

    int32_t framesWritten = 0;
    int32_t bytesPerFrame = mRecordingStream->getChannelCount() *
                            oboeStream->getBytesPerSample();
    int32_t bytesProvided = numFrames * bytesPerFrame;

    // skip writing audio to play stream if during the startup period or if we're skipping local playback
    if (mProcessedFrameCount >= mSystemStartupFrames && !mSkipLocalPlayback) {
        do {
            oboe::ResultWithValue<int32_t> status =
                    mPlayStream->write(audioData, numFrames, 0);
            if (!status) {
                LOGE("input stream read error: %s",
                     oboe::convertToText(status.error()));
                return oboe::DataCallbackResult::Stop;
            }
            framesWritten += status.value();
        } while (numFrames > framesWritten);
    }

    // send to audio data to jni callback
    if (bytesProvided > 0 && mJavaVm != NULL) {
        JNIEnv* env;
        mJavaVm->AttachCurrentThread(&env, NULL); // a NO-OP if already attached
        mAudioCallbackBuffer = env->NewByteArray(bytesProvided);
        env->SetByteArrayRegion(mAudioCallbackBuffer, 0, bytesProvided, (jbyte *)audioData);
        env->CallVoidMethod(mAudioCallbackInstance, mAudioCallbackMethod, mAudioCallbackBuffer);
        env->DeleteLocalRef(mAudioCallbackBuffer);
    };

    mProcessedFrameCount += numFrames;

    return oboe::DataCallbackResult::Continue;
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
 * @param oboeStream
 * @param error
 */
void NativeAudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream,
                                         oboe::Result error) {
    LOGE("%s stream Error after close: %s",
         oboe::convertToText(oboeStream->getDirection()),
         oboe::convertToText(error));
}
