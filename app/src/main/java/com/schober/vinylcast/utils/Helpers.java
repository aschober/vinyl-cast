package com.schober.vinylcast.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.format.Formatter;
import android.util.Pair;

import androidx.media.session.MediaButtonReceiver;

import com.schober.vinylcast.MainActivity;
import com.schober.vinylcast.R;
import com.schober.vinylcast.VinylCastService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import static android.content.Context.WIFI_SERVICE;
import static androidx.core.app.NotificationCompat.Action;
import static androidx.core.app.NotificationCompat.Builder;
import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;
import static androidx.media.app.NotificationCompat.MediaStyle;

/**
 * Class for static helpers
 * Created by Allen Schober on 3/26/17.
 */

public class Helpers {

    private Helpers(){}	// not to be instantiated

    public static void createStopNotification(MediaSessionCompat mediaSession, Service context, Class<?> serviceClass, String NOTIFICATION_CHANNEL_ID, int NOTIFICATION_ID) {
        createNotificationChannel(context, NOTIFICATION_CHANNEL_ID);

        PendingIntent stopIntent = PendingIntent.getService(context, 0, getServiceActionIntent(VinylCastService.ACTION_STOP_RECORDING, context, serviceClass), PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent mainActivityIntent = PendingIntent.getActivity(context, 0, getActivityIntent(context, MainActivity.class), 0);

        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat mediaDescription = mediaMetadata == null ? null : mediaMetadata.getDescription();

        CharSequence contentTitle = mediaDescription == null ? null : mediaDescription.getTitle();
        CharSequence contentText = mediaDescription == null ? context.getString(R.string.status_recording) : mediaDescription.getSubtitle();
        CharSequence subText = mediaDescription == null ? null : mediaDescription.getDescription();
        Bitmap largeIcon = mediaDescription == null ? null : mediaDescription.getIconBitmap();

        // Start foreground service to avoid unexpected kill
        Notification notification = new Builder(context)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setLargeIcon(largeIcon)
                .setContentIntent(mainActivityIntent)
                .setDeleteIntent(stopIntent)
                .setShowWhen(false)
                // Add a pause button
                .addAction(new Action(
                        R.drawable.ic_stop_black_24dp, context.getString(R.string.stop),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                PlaybackStateCompat.ACTION_STOP)))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                PlaybackStateCompat.ACTION_STOP)))
                .setSmallIcon(R.drawable.ic_album_black_24dp)
                .setVisibility(VISIBILITY_PUBLIC)
                .build();

        context.startForeground(NOTIFICATION_ID, notification);
    }

    public static Intent getServiceActionIntent(String action, Context con, Class<?> serviceClass) {
        Intent intent = new Intent(con, serviceClass);
        intent.setAction(action);
        return intent;
    }

    public static Intent getActivityIntent(Context con, Class<?> activityClass) {
        Intent intent = new Intent(con, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static String getIpAddress(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }

    /**
     * Get an OutputStream and InputStream that provides raw audio output.
     * @return Pair<OutputStream, InputStream>
     */
    public static Pair<OutputStream, InputStream> getPipedAudioStreams(int bufferSize) throws IOException {
        PipedInputStream audioInputStream = new PipedInputStream(bufferSize);
        PipedOutputStream audioOutputStream = new PipedOutputStream(audioInputStream);
        return new Pair(audioOutputStream, audioInputStream);
    }

    private static void createNotificationChannel(Context context, String CHANNEL_ID) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_channel_name);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
