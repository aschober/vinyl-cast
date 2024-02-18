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
 * https://github.com/google/oboe/blob/86165b82/samples/LiveEffect/src/main/cpp/FullDuplexPass.h
 *
 * Modifications Copyright 2020 Allen Schober
 *
 */

#ifndef OBOE_FULLDUPLEXPASSTHRU_H
#define OBOE_FULLDUPLEXPASSTHRU_H

constexpr float kScaleI16ToFloat = (1.0f / 32768.0f);

// Applies gain, overwrites the value pointed to by *sample.
inline void applyGain(int16_t* sample, float_t gain) {
    // Convert int16 -> float.
    float_t fval = static_cast<float_t>(*sample) * kScaleI16ToFloat;
    // Apply gain.
    fval *= gain;
    // Convert float->i16.
    fval += 1.0; // to avoid discontinuity at 0.0 caused by truncation
    fval *= 32768.0f;
    auto ival = static_cast<int32_t>(fval);
    // clip to 16-bit range
    if (ival < 0) ival = 0;
    else if (ival > 0x0FFFF) ival = 0x0FFFF;
    // center at zero
    ival -= 32768;
    // Replace original value.
    *sample = static_cast<int16_t>(ival);
}

class FullDuplexPassthru : public oboe::FullDuplexStream {

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

    void setGainDecibels(double decibels) {
        // Convert decibels into a multiplier.
        mGain = pow(10, decibels/20.0f);
    }

    virtual oboe::DataCallbackResult
    onBothStreamsReady(
            const void *inputData,
            int   numInputFrames,
            void *outputData,
            int   numOutputFrames) {

        size_t outBytesPerFrame = getOutputStream()->getBytesPerFrame();
        size_t inBytesPerFrame = getInputStream()->getBytesPerFrame();
        size_t bytesFromInput = numInputFrames * inBytesPerFrame;
        size_t bytesForOutput = numOutputFrames * outBytesPerFrame;
        size_t byteDiff = bytesForOutput - bytesFromInput;
        size_t bytesToZero = (byteDiff > 0) ? byteDiff : 0;

        if (bytesFromInput == 0 && bytesForOutput == 0) {
            LOGE("Streams not ready - bytesFromInput: %zu, bytesForOutput: %zu",
                    bytesFromInput, bytesForOutput);
            return oboe::DataCallbackResult::Continue;
        }

        if (bytesFromInput > 0 && mGain != 1.0) {
            // Apply gain in-place to inputData.
            // Assumes we're dealing with int16 samples.
            if (this->getInputStream()->getFormat() == oboe::AudioFormat::I16) {
                void *inputDataEnd = (void *) ((u_char *) inputData + bytesFromInput);
                for (int16_t *sample = (int16_t *) inputData; sample < inputDataEnd; sample++) {
                    applyGain(sample, mGain);
                }
            }
        }

        if (bytesForOutput != 0) {
            if (!mSkipLocalPlayback) {
                // copy audio data to output stream with (if needed) zeroed out bytes at end
                memcpy(outputData, inputData, bytesFromInput);
                memset((u_char *) outputData + bytesFromInput, 0, bytesToZero);
            } else {
                // set zeroed bytes to output
                memset((u_char *) outputData , 0, bytesForOutput);
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
    float_t mGain = 1.0;

    JavaVM* mJavaVm;
    jobject mAudioDataCallbackInstance;
    jmethodID mAudioDataCallbackMethod;
};
#endif //OBOE_FULLDUPLEXPASSTHRU_H
