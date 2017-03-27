package com.schober.vinylcast;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Allen on 3/26/17.
 */

class Helpers {

    private Helpers(){}	// not to be instantiated

    static void createStopNotification(String title, String stopText, Service con, Class<?> serviceClass, int NOTIFICATION_ID) {

        PendingIntent stopIntent = PendingIntent
                .getService(con, 0, getIntent(MediaRecorderService.REQUEST_TYPE_STOP, con, serviceClass),
                        PendingIntent.FLAG_CANCEL_CURRENT);

        // Start foreground service to avoid unexpected kill
        Notification notification = new Notification.Builder(con)
                .setContentTitle(title)
                .setContentText("").setSmallIcon(R.drawable.ic_album_black_24dp)
                .addAction(new Notification.Action(R.drawable.ic_stop_black_24dp, stopText, stopIntent))
                .build();
        con.startForeground(NOTIFICATION_ID, notification);
    }

    static Intent getIntent(String requestType, Context con, Class<?> serviceClass) {
        Intent intent = new Intent(con, serviceClass);
        intent.putExtra(MediaRecorderService.REQUEST_TYPE, requestType);
        return intent;
    }

}
