package com.schober.vinylcast;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import static android.content.Context.WIFI_SERVICE;

/**
 * Created by Allen on 3/26/17.
 */

class Helpers {

    private Helpers(){}	// not to be instantiated

    static String getIpAddress(Context context) {
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }
}
