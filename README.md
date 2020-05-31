<img align="left" width="120" height="120" src="https://cloud.githubusercontent.com/assets/3988421/25033468/8d0ed758-20a3-11e7-8dc9-c141ceaeb3a6.png">

# Vinyl Cast

**Listen to vinyl records wirelessly throughout your home using an Android device.**

<br>

Vinyl Cast is an Android app used to wirelessly stream the audio of a vinyl record player (or any audio source for that matter) to Google Cast-enabled (Chromecast built-in) devices or groups.

The Vinyl Cast App makes use of Android's USB audio peripheral support, audio recorder, media codecs, Google Oboe libary, media APIs, and Cast API to stream the audio from a connected audio source to Cast-enabled devices.

#### Download: [Latest GitHub Release](https://github.com/aschober/vinyl-cast/releases/latest) or [Google Play Store](https://play.google.com/store/apps/details?id=tech.schober.vinylcast.playstore)

#### Demo: [YouTube](https://youtu.be/HBDkxEvCcHQ)

#### Website: [Vinyl Cast](https://vinylcast.schober.tech)

Note: The initial release of Vinyl Cast included using Audio ACR to detect the song being played, but unfortunately this feature relied on a now discontinued third-party library/service so the feature was removed. Other options are being explored to add this feature back to Vinyl Cast in the future.

#### Simple UI: Tap the record or play button to start streaming

The record in the app spins when actively streaming.
<p float="left">
  <img src="https://user-images.githubusercontent.com/3988421/82761785-aa06a580-9dc2-11ea-8de4-c0425a5e30fe.png" width="200">
  <img src="https://user-images.githubusercontent.com/3988421/82761783-a83ce200-9dc2-11ea-9dbf-4a3e22496cf8.png" width="200">
  <img src="https://user-images.githubusercontent.com/3988421/82761782-a70bb500-9dc2-11ea-930f-b05e1405eca7.png" width="200">
</p>

# Required Hardware

- [Android Device](#iphone-android-device)
- [USB Audio Device](#microphone-usb-audio-device)
- [USB OTG Adapter](#electric_plug-usb-otg-adapter)
- [Audio Source](#notes-audio-source)
- [Cast-Enabled Device](#satellite-cast-enabled-device)

#### :iphone: Android Device
An Android device will be used to capture raw audio from your record player (or any analog audio source), perform audio format conversion (if selected), and act as a webserver to stream the digital audio stream to Cast-enabled devices. The Vinyl Cast app currently requires an Android device running Android 7.1 or later.

The app was developed and testing using a Nexus 6, Pixel 5C, and Pixel 3.

#### :microphone: USB Audio Device
A USB audio interface is used to capture the raw audio from your Audio Source (e.g. record player) and make the analog audio stream available for recording/streaming by the Vinyl Cast app.  If your analog audio source includes a USB interface, you can use this. If your record player only has analog audio output, I would recommend the [Behringer UCA202](http://a.co/35VGwrV) (without pre-amp), [Behringer UFO202](http://a.co/hThUxAL) (with pre-amp), or [Dynasty ProAudio DA-UA2D](https://www.amazon.com/dp/B07MJ1W974/ref=cm_sw_r_tw_dp_U_x_hlDYEbEJXJB26) (standalone pre-amp with USB interface).

The app was developed and tested using a Behringer UCA202.

#### :electric_plug: USB Adapter
You will need a way to connect the USB Audio Device to your Android device. Typically, your USB Audio Device has USB-A male connector, and you will need a USB adapter/cable to attach the USB Audio Device to your Android device.

The USB adapter you need depends on the type of USB connector your Android device has (usually how you charge the device). If you have USB-C connector on your Android device, you'll need a USB-C to USB-A female adapter. If you have USB Micro-B connector, you'll need a USB OTG adapter from USB Micro-B to USB-A female. Note that USB adapters are now often included in the box with new Android phones to help transfer data from an old device (e.g. Pixel devices include a ["Quick Switch Adapter"](https://support.google.com/pixelphone/answer/7158537?hl=en) going from USB-C to USB-A female). Otherwise you can purchase one separately that should look like [this USB Micro-B OTG cable](https://www.amazon.com/dp/B00LN3LQKQ/ref=cm_sw_em_r_mt_dp_U_X5DYEbMD7Y7EG) or like [this USB-C adapter](https://www.amazon.com/dp/B01GGKYYT0/ref=cm_sw_em_r_mt_dp_U_SJDYEb6P22JRB).

An adapter with power passthrough like this [one](https://www.amazon.com/dp/B07KMC3DTL/ref=cm_sw_r_tw_dp_U_x_6TgeDbFQRBDRE) can be extra useful to be able to charge your Android device while also connected to your USB Audio Device.

#### :notes: Audio Source
You'll need an audio source (e.g. a vinyl record player) to provide audio input to the Vinyl Cast app. If you're not familiar, it could look like [this](https://www.amazon.com/dp/B07N3XJ66N/ref=cm_sw_em_r_mt_dp_U_AvDYEb4J3NQZ0).

#### :satellite: Cast-Enabled Device
You'll need a Google Cast-enabled (aka Chromecast built-in) device hooked up to speakers to receive and playback the audio streamed from the Vinyl Cast app.

The app was developed and tested using a Google Home, Chromecast Audio, and Chromecast (2nd Generation).

# Setup Hardware

The hardware should be set up as expected with the goal of recording audio from audio source via the Android device and wirelessly transmitting the audio to a Cast-enabled device.

| Hardware Setup Flow Chart      |
| :---: |
| Android Device :point_right: USB OTG Adapter :point_right: USB Audio Device :point_right: Audio Source |
| :satellite: |
| Cast-Enabled Device :point_right: Speakers |

<img src="https://cloud.githubusercontent.com/assets/3988421/25034113/e0cbc72e-20a9-11e7-8be4-b42e6c410c8e.png" width="530">

# Get Casting
With the app installed and hardware setup:

- open the Vinyl Cast app and open Settings via the gear (:gear:) icon<br>
- select a `Recording Device` (if connected, you should see your USB Audio Device in dropdown) - default: `Auto select`
- select a `Local Playback Device` (if you want to hear the recorded audio on the Android Device speakers or USB output device)  - default: `None`
- select an `Audio Encoding` to adjust how much network bandwidth is used when streaming audio: WAV (lossless but more network bandwidth) or ADTC AAC (lossy but much less network bandwidth) - default: `WAV`
- return to app home screen and tap the Cast button to begin a Cast sesion to a Cast-enabled device or group (for synced multi-room audio).
- tap the vinyl record image or play button to begin audio recording & streaming

The vinyl record image will rotate to signify that the app is actively recording/streaming and a real-time spectral plot from the FFT of the raw audio (like a Graphic EQ) will give real-time feedback of the audio input.

Tap the record again to stop the stream or access controls via the Android playback notification.

Note there is about a ~10-20 sec audio delay in the playback on any Cast-enabled devices due to buffering of the media player of the Cast-enabled device.

# Dev Notes

#### Google Oboe
Vinly Cast uses the [Google Oboe](https://github.com/google/oboe) library to access low-latency audio APIs on Android devices.

#### Audio Conversion
The app provides two choices for audio encoding: WAV and AAC. If AAC is selected, the app converts the raw 16 bit PCM stereo audio data captured from the USB audio device at a sample rate of 48kHz to an AAC LC 192kbps encoded stream with ADTS headers which is sent via HTTP 1.1 chunked transfer encoding.

# Future Ideas

##### Audio ACR + Rich Notifications

Add Audio ACR to detect the current song being played and display the related metadata.

The song metadata is determined via audio acr (automatic content recognition) using fingerprints of the raw audio stream which upon successful matching, provides rich metadata for display and storage. The playback history is then stored in a local database for future features around sharing and analysis.

> Check out what's playing from the notification bar or lockscreen.
> 
> <img src="https://cloud.githubusercontent.com/assets/3988421/24994901/6d4dd452-1ff2-11e7-92d7-ef6ae8061901.png" width="200">

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
