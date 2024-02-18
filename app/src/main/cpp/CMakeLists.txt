#
# Copyright 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Based on CMakeLists.txt
# https://github.com/google/oboe/tree/0a78e50b64/samples/LiveEffect/src/main/cpp/CMakeLists.txt
#
# Modifications Copyright 2020 Allen Schober
#

cmake_minimum_required(VERSION 3.4.1)
project(vinylCast LANGUAGES C CXX)

get_filename_component(VINYL_CAST_ROOT_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../.. ABSOLUTE)

### INCLUDE OBOE LIBRARY ###
set (OBOE_DIR ${VINYL_CAST_ROOT_DIR}/oboe)
add_subdirectory(${OBOE_DIR} ./oboe-bin)

add_library(vinylCast
    SHARED
        NativeAudioEngine.cpp
        jni_bridge.cpp
        ${VINYL_CAST_ROOT_DIR}/debug-utils/trace.cpp)
target_include_directories(vinylCast
    PRIVATE
        ${VINYL_CAST_ROOT_DIR}/debug-utils
        ${OBOE_DIR}/include)
target_link_libraries(vinylCast
    PRIVATE
        oboe
        android
        atomic
        log)

# Enable optimization flags: if having problems with source level debugging,
# disable -Ofast ( and debug ), re-enable it after done debugging.
target_compile_options(vinylCast PRIVATE -Wall -Werror -Wno-unused-variable "$<$<CONFIG:RELEASE>:-Ofast>")

