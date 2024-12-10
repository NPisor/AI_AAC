package com.example.ai_aac;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {

    /**
     * Get the device's local IP address in a Wi-Fi network.
     *
     * @param context The application context.
     * @return The local IP address as a string, or null if not connected to Wi-Fi.
     */
    public static String getWifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            int ip = wifiManager.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ip);
        }
        return null; // Not connected to Wi-Fi
    }

    /**
     * Get the device's local IP address for any active network.
     *
     * @return The local IP address as a string, or null if not found.
     */
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                for (InetAddress address : addresses) {
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // No valid IP address found
    }
}
