package com.schober.vinylcast.utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.format.Formatter;

import androidx.media.session.MediaButtonReceiver;

import com.schober.vinylcast.R;
import com.schober.vinylcast.service.MediaRecorderService;

import static android.content.Context.WIFI_SERVICE;
import static androidx.core.app.NotificationCompat.*;
import static androidx.media.app.NotificationCompat.*;

/**
 * Created by Allen on 3/26/17.
 */

public class Helpers {

    private Helpers(){}	// not to be instantiated

    public static void createStopNotification(MediaSessionCompat mediaSession, Service context, Class<?> serviceClass, int NOTIFICATION_ID) {

        PendingIntent stopIntent = PendingIntent
                .getService(context, 0, getIntent(MediaRecorderService.REQUEST_TYPE_STOP, context, serviceClass),
                        PendingIntent.FLAG_CANCEL_CURRENT);

        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();
        // Start foreground service to avoid unexpected kill
        Notification notification = new Builder(context)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setDeleteIntent(stopIntent)
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

    public static Intent getIntent(String requestType, Context con, Class<?> serviceClass) {
        Intent intent = new Intent(con, serviceClass);
        intent.putExtra(MediaRecorderService.REQUEST_TYPE, requestType);
        return intent;
    }

    public static String getIpAddress(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }
}
