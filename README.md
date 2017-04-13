# vinyl-cast

**Listen to vinyl records wirelessly throughout your home.**

Vinyl Cast is an Android app used to wirelessly stream the audio of a record vinyl player to Chromecast-enabled devices while also detecting the track being played via audio acr and storing the track metadata in a local db for future features around sharing, review or data analysis.

App makes use of Android's USB audio peripheral support, audio recorder, Cast API and http server-capability to serve an audio stream of a connected audio-sorce to Chromecast-devices while also providing rich metadata and notifications for the analog audio stream via audio acr (automatic content recognition).

![screenshot](https://cloud.githubusercontent.com/assets/3988421/24994190/524ae738-1fef-11e7-9a33-0e585112228c.png)


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


# Dev Setup

#### Gracenote SDK for Music ACR
The Gracenote SDK is used to perform msuic ACR. You will need to provide your own `Client ID` and `Client Tag` in `MusicRecognizer.java`. This can be obtained by signing up and creating an app at the [Gracenote Developer](https://developer.gracenote.com/gnsdk) site.








