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

#include <jni.h>
#include <logging_macros.h>
#include "NativeAudioEngine.h"

static const int kOboeApiAAudio = 0;
static const int kOboeApiOpenSLES = 1;

static NativeAudioEngine *engine = nullptr;

extern "C" {

JNIEXPORT bool JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_create(JNIEnv *env,
                                                          jclass) {
    if (engine == nullptr) {
        engine = new NativeAudioEngine();
    }

    return (engine != nullptr);
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_delete(JNIEnv *env,
                                                          jclass) {
    delete engine;
    engine = nullptr;
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_prepareRecording(
        JNIEnv *env, jclass) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine before calling this "
                "method");
        return;
    }

    engine->prepareRecording();
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_startRecording(
        JNIEnv *env, jclass) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine before calling this "
                "method");
        return;
    }

    engine->startRecording();
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_stopRecording(
        JNIEnv *env, jclass) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine before calling this "
                "method");
        return;
    }

    engine->stopRecording();
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_setRecordingDeviceId(
        JNIEnv *env, jclass, jint deviceId) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine before calling this "
                "method");
        return;
    }

    engine->setRecordingDeviceId(deviceId);
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_setPlaybackDeviceId(
        JNIEnv *env, jclass, jint deviceId) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine before calling this "
                "method");
        return;
    }

    engine->setPlaybackDeviceId(deviceId);
}

JNIEXPORT jboolean JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_setAPI(JNIEnv *env,
                                                          jclass type,
                                                          jint apiType) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine "
                "before calling this method");
        return JNI_FALSE;
    }

    oboe::AudioApi audioApi;
    switch (apiType) {
        case kOboeApiAAudio:
            audioApi = oboe::AudioApi::AAudio;
            break;
        case kOboeApiOpenSLES:
            audioApi = oboe::AudioApi::OpenSLES;
            break;
        default:
            LOGE("Unknown API selection to setAPI() %d", apiType);
            return JNI_FALSE;
    }

    return static_cast<jboolean>(engine->setAudioApi(audioApi) ? JNI_TRUE
                                                               : JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_isAAudioSupported(
        JNIEnv *env, jclass type) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine "
                "before calling this method");
        return JNI_FALSE;
    }
    return static_cast<jboolean>(engine->isAAudioSupported() ? JNI_TRUE
                                                             : JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_setAudioDataListener(JNIEnv *env, jclass type,
                                                                        jobject listener) {

    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine "
                "before calling this method");
        return;
    }
    engine->setAudioDataListener(env, type, listener);
}

JNIEXPORT jint JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_getSampleRate(
        JNIEnv *env, jclass type) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine "
                "before calling this method");
        return JNI_FALSE;
    }
    return static_cast<jint>(engine->getSampleRate());
}

JNIEXPORT jint JNICALL
Java_com_schober_vinylcast_audio_NativeAudioEngine_getChannelCount(
        JNIEnv *env, jclass type) {
    if (engine == nullptr) {
        LOGE(
                "Engine is null, you must call createEngine "
                "before calling this method");
        return JNI_FALSE;
    }
    return static_cast<jint>(engine->getChannelCount());
}
}