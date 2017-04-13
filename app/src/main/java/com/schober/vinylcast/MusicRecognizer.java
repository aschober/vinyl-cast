package com.schober.vinylcast;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import com.gracenote.gnsdk.GnAlbum;
import com.gracenote.gnsdk.GnAlbumIterator;
import com.gracenote.gnsdk.GnAssetFetch;
import com.gracenote.gnsdk.GnDescriptor;
import com.gracenote.gnsdk.GnError;
import com.gracenote.gnsdk.GnException;
import com.gracenote.gnsdk.GnLanguage;
import com.gracenote.gnsdk.GnLicenseInputMode;
import com.gracenote.gnsdk.GnList;
import com.gracenote.gnsdk.GnLocale;
import com.gracenote.gnsdk.GnLocaleGroup;
import com.gracenote.gnsdk.GnLookupData;
import com.gracenote.gnsdk.GnLookupMode;
import com.gracenote.gnsdk.GnManager;
import com.gracenote.gnsdk.GnMusicIdStream;
import com.gracenote.gnsdk.GnMusicIdStreamIdentifyingStatus;
import com.gracenote.gnsdk.GnMusicIdStreamPreset;
import com.gracenote.gnsdk.GnMusicIdStreamProcessingStatus;
import com.gracenote.gnsdk.GnRegion;
import com.gracenote.gnsdk.GnResponseAlbums;
import com.gracenote.gnsdk.GnStatus;
import com.gracenote.gnsdk.GnStorageSqlite;
import com.gracenote.gnsdk.GnTrack;
import com.gracenote.gnsdk.GnUser;
import com.gracenote.gnsdk.GnUserStore;
import com.gracenote.gnsdk.IGnCancellable;
import com.gracenote.gnsdk.IGnMusicIdStreamEvents;
import com.gracenote.gnsdk.IGnSystemEvents;
import com.schober.vinylcast.utils.DatabaseAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Used to perform ACR of the stream from the raw audio input stream.
 * Based on Android Gracenote example app.
 */

public class MusicRecognizer {
    private static final String TAG = "MusicRecognizer";

    private static final String GRACENOTE_CLIENT_ID = "[CLIENT_ID]";
    private static final String GRACENOTE_CLIENT_TAG = "[CLIENT_TAG]";
    private static final String GRACENOTE_LICENSE_FILENAME = "license.txt";	// app expects this file as an "asset"
    private static final String GRACENOTE_APP_STRING = "Vinyl Cast";

    // Gracenote objects
    private GnManager gnManager;
    private GnUser gnUser;
    private GnMusicIdStream gnMusicIdStream;
    private List<GnMusicIdStream> streamIdObjects = new ArrayList<GnMusicIdStream>();

    // store some tracking info about the most recent MusicID-Stream lookup
    protected volatile boolean lastLookup_local	= false;	// indicates whether the match came from local storage
    protected volatile long lastLookup_matchTime = 0;  		// total lookup time for query
    private volatile long lastLookup_startTime;  			// start time of query
    private volatile boolean audioProcessingStarted = false;

    private String lastTrackUid;

    private Context context;
    private MainActivity activity;

    public MusicRecognizer(Context context, MainActivity activity) {
        this.context = context;
        this.activity = activity;
        initialize();
    }

    public void start() {
        try {
            if (gnMusicIdStream != null) {
                gnMusicIdStream.identifyAlbumAsync();
                lastLookup_startTime = SystemClock.elapsedRealtime();
            }
        } catch (GnException e) {
            Log.e(TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule(), e);
        }
    }

    public void stop() {
        if (gnMusicIdStream != null) {
            try {
                // to ensure no pending identifications deliver results while your app is
                // paused it is good practice to call cancel
                // it is safe to call identifyCancel if no identify is pending
                gnMusicIdStream.identifyCancel();

                // stopping audio processing stops the audio processing thread started
                // in onResume
                gnMusicIdStream.audioProcessStop();
            } catch (GnException e) {
                Log.e(TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule(), e);
            }
        }
    }

    public GnMusicIdStream getGnMusicIdStream() {
        return gnMusicIdStream;
    }

    public boolean getMusicRecognizerStarted() {
        return audioProcessingStarted;
    }

    private void initialize() {
        // check the client id and tag have been set
        if ( (GRACENOTE_CLIENT_ID == null) || (GRACENOTE_CLIENT_TAG == null) ){
            Log.e(TAG, "Please set Client ID and Client Tag");
            return;
        }

        // get the gnsdk license from the application assets
        String gnsdkLicense = null;
        if (GRACENOTE_LICENSE_FILENAME == null || GRACENOTE_LICENSE_FILENAME.length() == 0) {
            Log.e(TAG, "License filename not set" );
        } else {
            gnsdkLicense = getAssetAsString(GRACENOTE_LICENSE_FILENAME);
            if (gnsdkLicense == null){
                Log.e(TAG, "License file not found: " + GRACENOTE_LICENSE_FILENAME);
                return;
            }
        }

        try {
            // GnManager must be created first, it initializes GNSDK
            gnManager = new GnManager(context, gnsdkLicense, GnLicenseInputMode.kLicenseInputModeString);

            // provide handler to receive system events, such as locale update needed
            gnManager.systemEventHandler(new SystemEvents());

            // get a user, if no user stored persistently a new user is registered and stored
            // Note: Android persistent storage used, so no GNSDK storage provider needed to store a user
            gnUser = new GnUser(new GnUserStore(context), GRACENOTE_CLIENT_ID, GRACENOTE_CLIENT_TAG, GRACENOTE_APP_STRING);

            // enable storage provider allowing GNSDK to use its persistent stores
            GnStorageSqlite.enable();

            // enable local MusicID-Stream recognition (GNSDK storage provider must be enabled as pre-requisite)
            // GnLookupLocalStream.enable();

            // Loads data to support the requested locale, data is downloaded from Gracenote Service if not
            // found in persistent storage. Once downloaded it is stored in persistent storage (if storage
            // provider is enabled). Download and write to persistent storage can be lengthy so perform in
            // another thread
            Thread localeThread = new Thread(
                    new LocaleLoadRunnable(GnLocaleGroup.kLocaleGroupMusic,
                            GnLanguage.kLanguageEnglish,
                            GnRegion.kRegionGlobal,
                            GnDescriptor.kDescriptorDefault,
                            gnUser),
                    "LocaleLoad"
            );
            localeThread.start();

            // Set up for continuous listening from the microphone
            // - create GnMusicIdStream instance, this can live for lifetime of app
            // - configure
            // Starting and stopping continuous listening should be started and stopped
            // based on Activity life-cycle, see onPause and onResume for details
            // To show audio visualization we wrap GnMic in a visualization adapter

            gnMusicIdStream = new GnMusicIdStream(gnUser, GnMusicIdStreamPreset.kPresetMicrophone, new MusicIDStreamEvents());
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataContent, true);
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataSonicData, true);
            gnMusicIdStream.options().lookupMode(GnLookupMode.kLookupModeOnline);
            gnMusicIdStream.options().resultSingle(true);

            // Retain GnMusicIdStream object so we can cancel an active identification if requested
            streamIdObjects.add(gnMusicIdStream);

        } catch (GnException e) {
            Log.e(TAG, e.errorAPI() + ": " + e.errorDescription(), e);
            return;
        } catch ( Exception e ) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * Loads a locale
     */
    class LocaleLoadRunnable implements Runnable {
        GnLocaleGroup group;
        GnLanguage language;
        GnRegion region;
        GnDescriptor descriptor;
        GnUser user;

        LocaleLoadRunnable(GnLocaleGroup group,
                           GnLanguage language,
                           GnRegion region,
                           GnDescriptor descriptor,
                           GnUser user) {
            this.group = group;
            this.language = language;
            this.region = region;
            this.descriptor = descriptor;
            this.user = user;
        }

        @Override
        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                GnLocale locale = new GnLocale(group,language,region,descriptor,gnUser);
                locale.setGroupDefault();
            } catch (GnException e) {
                Log.e(TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule(), e);
            }
        }
    }

    /**
     * Helpers to read license file from assets as string
     */
    private String getAssetAsString(String assetName) {

        String assetString = null;
        InputStream assetStream;

        try {
            assetStream = context.getAssets().open(assetName);
            if(assetStream != null){
                java.util.Scanner s = new java.util.Scanner(assetStream).useDelimiter("\\A");
                assetString = s.hasNext() ? s.next() : "";
                assetStream.close();
            } else{
                Log.e(TAG, "Asset not found:" + assetName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting asset as string: " + e.getMessage(), e);
        }

        return assetString;
    }

    /**
     * Updates a locale
     */
    class LocaleUpdateRunnable implements Runnable {
        GnLocale locale;
        GnUser user;

        LocaleUpdateRunnable(GnLocale locale, GnUser user) {
            this.locale = locale;
            this.user = user;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                locale.update(user);
            } catch (GnException e) {
                Log.e( TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule(), e);
            }
        }
    }

    /**
     * Updates a list
     */
    class ListUpdateRunnable implements Runnable {
        GnList list;
        GnUser user;

        ListUpdateRunnable(GnList list, GnUser user) {
            this.list = list;
            this.user = user;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                list.update(user);
            } catch (GnException e) {
                Log.e( TAG, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule(), e);
            }
        }
    }

    /**
     * Receives system events from GNSDK
     */
    class SystemEvents implements IGnSystemEvents {
        @Override
        public void localeUpdateNeeded( GnLocale locale ){
            // Locale update is detected
            Thread localeUpdateThread = new Thread(new LocaleUpdateRunnable(locale, gnUser), "LocaleUpdate");
            localeUpdateThread.start();
        }

        @Override
        public void listUpdateNeeded( GnList list ) {
            // List update is detected
            Thread listUpdateThread = new Thread(new ListUpdateRunnable(list ,gnUser), "ListUpdate");
            listUpdateThread.start();
        }

        @Override
        public void systemMemoryWarning(long currentMemorySize, long warningMemorySize) {
            // only invoked if a memory warning limit is configured
        }
    }

    /**
     * GNSDK MusicID-Stream event delegate
     */
    private class MusicIDStreamEvents implements IGnMusicIdStreamEvents {

        HashMap<String, String> gnStatus_to_displayStatus;

        public MusicIDStreamEvents() {
            gnStatus_to_displayStatus = new HashMap<>();
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingStarted.toString(), "Identification started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingFpGenerated.toString(), "Fingerprinting complete");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted.toString(), "Lookup started");
            gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted.toString(), "Lookup started");
			//gnStatus_to_displayStatus.put(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingEnded.toString(), "Identification complete");
        }

        @Override
        public void statusEvent(GnStatus status, long percentComplete, long bytesTotalSent, long bytesTotalReceived, IGnCancellable cancellable) {
            //activity.setStatus(String.format("%d%%",percentComplete), true);
        }

        @Override
        public void musicIdStreamProcessingStatusEvent(GnMusicIdStreamProcessingStatus status, IGnCancellable canceller ) {
            if(GnMusicIdStreamProcessingStatus.kStatusProcessingAudioStarted.compareTo(status) == 0) {
                Log.d(TAG, "Audio Processing Started");
                audioProcessingStarted = true;
            }
        }

        @Override
        public void musicIdStreamIdentifyingStatusEvent(GnMusicIdStreamIdentifyingStatus status, IGnCancellable canceller) {
            if(gnStatus_to_displayStatus.containsKey(status.toString())) {
                activity.setStatus(String.format("%s", gnStatus_to_displayStatus.get(status.toString())), true);
            }

            if(status.compareTo(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingLocalQueryStarted) == 0 ) {
                lastLookup_local = true;
            }
            else if(status.compareTo(GnMusicIdStreamIdentifyingStatus.kStatusIdentifyingOnlineQueryStarted) == 0) {
                lastLookup_local = false;
            }
        }

        @Override
        public void musicIdStreamAlbumResult(GnResponseAlbums result, IGnCancellable canceller ) {
            lastLookup_matchTime = SystemClock.elapsedRealtime() - lastLookup_startTime;
            activity.runOnUiThread(new UpdateResultsRunnable(result));
        }

        @Override
        public void musicIdStreamIdentifyCompletedWithError(GnError error) {
            if (error.isCancelled()) {
                activity.setStatus("Cancelled", true);
            } else {
                activity.setStatus(error.errorDescription(), true);
            }
        }
    }

    /**
     * Adds album results to UI via Runnable interface
     */
    private int noMatchCount;
    class UpdateResultsRunnable implements Runnable {

        GnResponseAlbums albumsResult;

        UpdateResultsRunnable(GnResponseAlbums albumsResult) {
            this.albumsResult = albumsResult;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            try {
                if (albumsResult.resultCount() == 0) {
                    activity.setStatus("No match", true);
                    if (noMatchCount > 2) {
                        noMatchCount = 0;
                        lastTrackUid = null;
                        activity.clearMetadata();
                    } else {
                        noMatchCount++;
                    }

                } else {
                    activity.setStatus("Match found", true);
                    noMatchCount = 0;
                    GnAlbumIterator iter = albumsResult.albums().getIterator();
                    while (iter.hasNext()) {
                        GnAlbum matchedAlbum = iter.next();
                        GnTrack matchedTrack = matchedAlbum.trackMatched();
                        if (!matchedTrack.gnUId().equals(lastTrackUid)) {
                            lastTrackUid = matchedTrack.gnUId();
                            activity.updateMetaDataFields(matchedAlbum);
                            trackChanges(albumsResult);
                        }
                    }
                }
            } catch (GnException e) {
                activity.setStatus(e.errorDescription(), true);
                return;
            }
        }
    }

    /**
     * Helpers to load and set cover art image in the application display
     */
    public void loadAndDisplayCoverArt(String coverArtUrl, ImageView imageView) {
        Thread runThread = new Thread(new CoverArtLoaderRunnable(coverArtUrl, imageView), "CoverArtLoader");
        runThread.start();
    }

    class CoverArtLoaderRunnable implements Runnable {
        String coverArtUrl;
        ImageView imageView;

        CoverArtLoaderRunnable(String coverArtUrl, ImageView imageView) {
            this.coverArtUrl = coverArtUrl;
            this.imageView = imageView;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            Drawable coverArt = null;

            if (coverArtUrl != null && !coverArtUrl.isEmpty()) {
                try {
                    GnAssetFetch assetData = new GnAssetFetch(gnUser, coverArtUrl);
                    byte[] data = assetData.data();
                    coverArt =  new BitmapDrawable(BitmapFactory.decodeByteArray(data, 0, data.length));
                } catch (GnException e) {
                    e.printStackTrace();
                }
            }

            if (coverArt != null) {
                activity.setCoverArt(coverArt, imageView);
            } else {
                activity.setCoverArt(context.getResources().getDrawable(R.drawable.no_album_image), imageView);
            }
        }
    }

    /**
     * History Tracking:
     * initiate the process to insert values into database.
     *
     * @param albums
     *            - contains all the information to be inserted into DB,
     *            except location.
     */
    private synchronized void trackChanges(GnResponseAlbums albums) {
        Thread thread = new Thread (new InsertChangesRunnable(albums));
        thread.start();
    }

    class InsertChangesRunnable implements Runnable {
        GnResponseAlbums row;

        InsertChangesRunnable(GnResponseAlbums row) {
            this.row = row;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                DatabaseAdapter db = new DatabaseAdapter(context, gnUser);
                db.open();
                db.insertChanges(row);
                db.close();
            } catch (GnException e) {
                // ignore
            }
        }
    }
}
