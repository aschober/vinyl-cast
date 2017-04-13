# vinyl-cast

**Listen to vinyl records wirelessly throughout your home.**

Vinyl Cast is an Android app used to wirelessly stream the audio of a record vinyl player to Chromecast-enabled devices while also detecting the track being played via audio acr and storing the track metadata in a local db for future features around sharing, review or data analysis.

App makes use of Android's USB audio peripheral support, audio recorder, Cast API and http server-capability to serve an audio stream of a connected audio-sorce to Chromecast-devices while also providing rich metadata and notifications for the analog audio stream via audio acr (automatic content recognition).

### Simple UI: Tap the record and start streaming
![screenshot](https://cloud.githubusercontent.com/assets/3988421/24994190/524ae738-1fef-11e7-9a33-0e585112228c.png)

### Rich Notifications: Check out what's playing from the notification bar or lockscreen
![screenshot_20170413-023732](https://cloud.githubusercontent.com/assets/3988421/24994901/6d4dd452-1ff2-11e7-92d7-ef6ae8061901.png)

# Required Hardware

**Android Phone**: An Android phone will be used to capture raw audio and act as a webserver to stream to Chromecast devices. The app was developed using a Nexus 6.

**USB Soundcard**: A USB sound card is used to capture the raw audio from the Vinyl Record Player and make the raw audio stream available to the app. I would recommend the [Behringer UCA202](http://a.co/35VGwrV) or [Behringer UFO202](http://a.co/hThUxAL). Note if your record player does not have a built-in phono pre-amp, should get the UFO202. The app was developed using a Behringer UCA202. 

**USB OTG Cable**: If your Android device does not have a USB A Female port, you will need a USB OTG cable to attach the soundcard to your device. An OTG y-cable with a power lead like this [one](http://a.co/b7Qw9NI) can be extra useful to save battery power and perhaps charge phone as well.

**Vinyl Record Player**: You'll need a vinyl record player to cast the audio from. If you're not familiar, it will look like [this](http://a.co/63s5QD1)

**Chromecast-enabled Device and Speakers**: You'll need a Chromecast-enabled device hooked up to speakers to receive the audio from the record player. The app was developed using a Chromecast Audio.

# Hardware Setup

The hardware should be set up as expected with the goal of wirelessly transmitting the audio from the record player to a Chromecast using an Android device.

**Android Phone -> USB OTG Cable -> USB Soundcard -> Vinyl Record Player**

**Chromecast -> Powered Speakers**

[TODO: better image/diagram]

# Get Streaming
With the app installed and hardware setup, open the app and tap the vinyl record image to select a Chromecast device and begin streaming.

The vinyl record image will rotate to signify that the app is actively streaming audio.

Tap the record again to stop the stream or access controls via the Android rich notification.

Note there is about a 3 sec delay in the audio stream from the record player to Chromecast speakers most likely due to buffering of the audio stream by the Chromecast device.

# Dev Setup

#### Gracenote SDK for Music ACR
The Gracenote SDK is used to perform msuic ACR. You will need to provide your own `Client ID` and `Client Tag` in `MusicRecognizer.java`. This can be obtained by signing up and creating an app at the [Gracenote Developer](https://developer.gracenote.com/gnsdk) site.




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