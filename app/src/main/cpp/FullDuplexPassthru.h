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
 * Based on FullDuplexPass.h
 * https://github.com/google/oboe/tree/0a78e50b64/samples/LiveEffect/src/main/cpp/FullDuplexPass.h
 *
 * Modifications Copyright 2020 Allen Schober
 *
 */

#ifndef OBOE_FULLDUPLEXPASSTHRU_H
#define OBOE_FULLDUPLEXPASSTHRU_H
#include "FullDuplexStream.h"

class FullDuplexPassthru : public FullDuplexStream {

public:

    void setSkipLocalPlayback(bool skipLocalPlayback) {
        mSkipLocalPlayback = skipLocalPlayback;
    }

    void setAudioDataCallback(JavaVM* javaVm, jobject audioDataCallbackInstance,
            jmethodID audioDataCallbackMethod) {
        LOGD("FullDuplexPassthru - setAudioDataCallback");
        mJavaVm = javaVm;
        mAudioDataCallbackInstance = audioDataCallbackInstance;
        mAudioDataCallbackMethod = audioDataCallbackMethod;
    }

    virtual oboe::DataCallbackResult onBothStreamsReady(const void *inputData, int numInputFrames,
            void *outputData, int numOutputFrames) {
        size_t bytesPerFrame = this->getOutputStream()->getBytesPerFrame();
        size_t bytesFromInput = numInputFrames * bytesPerFrame;
        size_t bytesForOutput = numOutputFrames * bytesPerFrame;
        size_t byteDiff = (numOutputFrames - numInputFrames) * bytesPerFrame;
        size_t bytesToZero = (byteDiff > 0) ? byteDiff : 0;

        if (bytesFromInput == 0 && bytesForOutput == 0) {
            LOGE("Streams not ready - bytesFromInput: %zu, bytesForOutput: %zu",
                    bytesFromInput, bytesForOutput);
            return oboe::DataCallbackResult::Continue;
        }

        if (bytesForOutput != 0) {
            if (!mSkipLocalPlayback) {
                // copy audio data to output stream with (if needed) zeroed out bytes at end
                memcpy(outputData, inputData, bytesFromInput);
                memset((u_char *) outputData + bytesFromInput, 0, bytesToZero);
            } else {
                // set zeroed bytes to output
                memset((u_char *) bytesForOutput, 0, bytesToZero);
            }
        }

        // send to audio data to jni callback
        if (bytesFromInput > 0 && mJavaVm != NULL) {
            JNIEnv* env;
            int getEnvStat = mJavaVm->GetEnv((void **)&env, JNI_VERSION_1_6);
            if (getEnvStat == JNI_EDETACHED) {
                LOGD("GetEnv: not attached");
                if (mJavaVm->AttachCurrentThread(&env, NULL) != 0) {
                    LOGE("GetEnv: Failed to attach");
                } else {
                    LOGD("GetEnv: now attached");
                }
            } else if (getEnvStat == JNI_OK) {
                // already attached
            } else if (getEnvStat == JNI_EVERSION) {
                LOGE("GetEnv: version not supported");
            }

            jbyteArray buffer = env->NewByteArray(bytesFromInput);
            env->SetByteArrayRegion(buffer, 0, bytesFromInput, (jbyte *)inputData);
            env->CallVoidMethod(mAudioDataCallbackInstance, mAudioDataCallbackMethod, buffer);
            env->DeleteLocalRef(buffer);
        };

        return oboe::DataCallbackResult::Continue;
    }

private:
    bool mSkipLocalPlayback = false;

    JavaVM* mJavaVm;
    jobject mAudioDataCallbackInstance;
    jmethodID mAudioDataCallbackMethod;
};
#endif //OBOE_FULLDUPLEXPASSTHRU_H