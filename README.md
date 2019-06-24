<img src="https://cloud.githubusercontent.com/assets/3988421/25033468/8d0ed758-20a3-11e7-8dc9-c141ceaeb3a6.png" width="100">

# vinyl-cast

**Listen to vinyl records wirelessly throughout your home.**

Vinyl Cast is an Android app used to wirelessly stream the audio of a vinyl record player to Chromecast-enabled devices while also detecting the current song being played and displaying the related metadata. The playback history is then stored in a local database for future features around sharing and analysis.

App makes use of Android's USB audio peripheral support, audio recorder, media codecs, media APIs, Cast API and http server-capability to stream the audio of a connected audio-source to Chromecast-devices. ~The song metadata is determined via audio acr (automatic content recognition) using fingerprints of the raw audio stream which upon successful matching, provides rich metadata for display and storage.~

#### Demo: [YouTube](https://youtu.be/HBDkxEvCcHQ)

#### Simple UI: Tap the record to start streaming

The record in the app spins when actively streaming.

<img src="https://cloud.githubusercontent.com/assets/3988421/24994190/524ae738-1fef-11e7-9a33-0e585112228c.png" width="200">

#### Rich Notifications: Audio ACR + Android Media APIs

NOTE: For now, removed Audio ACR features as the 3rd Party SDK is no longer available.

> Check out what's playing from the notification bar or lockscreen.
> 
> <img src="https://cloud.githubusercontent.com/assets/3988421/24994901/6d4dd452-1ff2-11e7-92d7-ef6ae8061901.png" width="200">

# Required Hardware

**Android Phone**: An Android phone will be used to capture raw audio and act as a webserver to stream to Chromecast devices. The app was developed using a Nexus 6.

**USB Soundcard**: A USB sound card is used to capture the raw audio from the Vinyl Record Player and make the raw audio stream available to the app. I would recommend the [Behringer UCA202](http://a.co/35VGwrV) or [Behringer UFO202](http://a.co/hThUxAL). If your record player does not have a built-in phono pre-amp, should get the UFO202. The app was developed using a Behringer UCA202. You may also use your record player's USB audio output if available.

**USB OTG Cable**: If your Android device does not have a USB A Female port, you will need a USB OTG cable to attach the USB soundcard to your device. An OTG y-cable with a power lead like this [one](http://a.co/b7Qw9NI) can be extra useful to save battery power and perhaps charge phone as well. A USB-C adapter like this [one](https://www.amazon.com/dp/B07KMC3DTL/ref=cm_sw_r_tw_dp_U_x_6TgeDbFQRBDRE) is also available for USB-C equipped phones.

**Vinyl Record Player**: You'll need a vinyl record player to cast the audio from. If you're not familiar, it will look like [this](http://a.co/63s5QD1)

**Chromecast-enabled Device and Speakers**: You'll need a Chromecast-enabled device hooked up to speakers to receive the audio from the record player. The app was developed using a Chromecast Audio.

# Hardware Setup

The hardware should be set up as expected with the goal of wirelessly transmitting the audio from the record player to a Chromecast using an Android device.

**Android Phone -> USB OTG Cable -> USB Soundcard -> Vinyl Record Player**

**Chromecast -> Powered Speakers**

<img src="https://cloud.githubusercontent.com/assets/3988421/25034113/e0cbc72e-20a9-11e7-8be4-b42e6c410c8e.png" width="530">

# Get Streaming
With the app installed and hardware setup:
- open the app
- select a `Recording Device` (should see your USB audio input device in dropdown if connected) - default: `Auto select`
- select a `Local Playback Device` (if you want to hear the recorded audio on the Android-device speakers or USB output device)  - default: `None`
- select a `Stream Encoding` to adjust how much network bandwidth is used when streaming audio: WAV (lossless but more network bandwidth) or ADTC AAC (lossy but much less network bandwidth) - default: `WAV`
- tap the vinyl record image to begin audio recording & streaming
- tap the Cast button to begin streaming to a Cast-enabled device

The vinyl record image will rotate to signify that the app is actively recording and streaming audio.

Tap the record again to stop the stream or access controls via the Android rich notification.

Note there is about a 3-10 sec delay in the audio stream from the record player to a Cast-enabled device due to buffering of the audio stream by the Cast-enabled device.

# Dev Notes

#### Audio Conversion
The app converts the raw 16 bit PCM stereo audio data captured from the USB sound card at a sample rate of 48kHz to an AAC LC 192kbps encoded stream with ADTS headers which is sent via HTTP 1.1 chunked transfer encoding.

# Future Ideas

##### Scrobble to Last.fm / Discogs / Twitter
The app can scrobble the listening history to Last.fm or update a user collection in Discogs or send out a tweet when a new record starts playing.

##### DJ Mode: add sound effects or record loops
With the raw audio stream, can add sound effects to the live stream *[insert record scraaaatttch]* or record loops of audio for later use.

##### Additional Analytics from Metadata
When a track is detected, there are additional traits available including genre, tempo, time progress, etc. Idea is to create insights into listening history data like what genre has been played the most, what record has been listened to the most or least, what's the favorite record for Tuesdays.

##### Client app to display metadata and provide controls
Since the Android device used is connected via USB to a record player, it's not very mobile while streaming. A client app can be created to run on a more-mobile device to display the latest metadata, access quick controls to pause/play, and even fetch playback history.

##### Record Flip Warning
If the track is detected, can give a warning based on track number and track progress time to notify the user that the side is about to be finished and will need to be flipped.

##### Android Things Support
This seems like a prime use case for an Android Things device to handle USB connection to record player and streaming audio. Android Things Developer Preview 2 did add support for USB audio devices.

During development, I attempted to use a Raspberry Pi 3 and Android Things DP 3, but routinely encountered a buffer overflow in the audio record thread. I did not investigate this issue any further.

##### Additional Hardware Integration
If the Android device is able to hook up to additional hardware devices (via GPIO or USB), idea is to build out additional-levels of hardware integration by modifying the record player hardware to be connected to the Android device and control the record player's start, stop, tone-arm position buttons via the app.
