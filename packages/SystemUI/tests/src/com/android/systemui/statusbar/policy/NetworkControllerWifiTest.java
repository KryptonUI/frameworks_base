package com.android.systemui.statusbar.policy;

import android.content.Intent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;

import com.android.systemui.statusbar.policy.NetworkController.IconState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class NetworkControllerWifiTest extends NetworkControllerBaseTest {
    // These match the constants in WifiManager and need to be kept up to date.
    private static final int MIN_RSSI = -100;
    private static final int MAX_RSSI = -55;

    @Test
    public void testWifiIcon() {
        String testSsid = "Test SSID";
        setWifiEnabled(true);
        verifyLastWifiIcon(false, WifiIcons.WIFI_NO_NETWORK);

        setWifiState(true, testSsid);
        verifyLastWifiIcon(true, WifiIcons.WIFI_SIGNAL_STRENGTH[0][0]);

        for (int testLevel = 0; testLevel < WifiIcons.WIFI_LEVEL_COUNT; testLevel++) {
            setWifiLevel(testLevel);

            setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, true, true);
            verifyLastWifiIcon(true, WifiIcons.WIFI_SIGNAL_STRENGTH[1][testLevel]);
            setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, false, true);
            verifyLastWifiIcon(true, WifiIcons.WIFI_SIGNAL_STRENGTH[0][testLevel]);
        }
    }

    @Test
    public void testQsWifiIcon() {
        String testSsid = "Test SSID";

        setWifiEnabled(false);
        verifyLastQsWifiIcon(false, false, WifiIcons.QS_WIFI_NO_NETWORK, null);

        setWifiEnabled(true);
        verifyLastQsWifiIcon(true, false, WifiIcons.QS_WIFI_NO_NETWORK, null);

        setWifiState(true, testSsid);
        for (int testLevel = 0; testLevel < WifiIcons.WIFI_LEVEL_COUNT; testLevel++) {
            setWifiLevel(testLevel);

            setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, true, true);
            verifyLastQsWifiIcon(true, true, WifiIcons.QS_WIFI_SIGNAL_STRENGTH[1][testLevel],
                    testSsid);
            setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, false, true);
            verifyLastQsWifiIcon(true, true, WifiIcons.QS_WIFI_SIGNAL_STRENGTH[0][testLevel],
                    testSsid);
        }
    }

    @Test
    public void testQsDataDirection() {
        // Setup normal connection
        String testSsid = "Test SSID";
        int testLevel = 2;
        setWifiEnabled(true);
        setWifiState(true, testSsid);
        setWifiLevel(testLevel);
        setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, true, true);
        verifyLastQsWifiIcon(true, true,
                WifiIcons.QS_WIFI_SIGNAL_STRENGTH[1][testLevel], testSsid);

        // Set to different activity state first to ensure a callback happens.
        setWifiActivity(WifiManager.DATA_ACTIVITY_IN);

        setWifiActivity(WifiManager.DATA_ACTIVITY_NONE);
        verifyLastQsDataDirection(false, false);
        setWifiActivity(WifiManager.DATA_ACTIVITY_IN);
        verifyLastQsDataDirection(true, false);
        setWifiActivity(WifiManager.DATA_ACTIVITY_OUT);
        verifyLastQsDataDirection(false, true);
        setWifiActivity(WifiManager.DATA_ACTIVITY_INOUT);
        verifyLastQsDataDirection(true, true);
    }

    @Test
    public void testRoamingIconDuringWifi() {
        // Setup normal connection
        String testSsid = "Test SSID";
        int testLevel = 2;
        setWifiEnabled(true);
        setWifiState(true, testSsid);
        setWifiLevel(testLevel);
        setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, true, true);
        verifyLastWifiIcon(true, WifiIcons.WIFI_SIGNAL_STRENGTH[1][testLevel]);

        setupDefaultSignal();
        setGsmRoaming(true);
        // Still be on wifi though.
        setConnectivity(NetworkCapabilities.TRANSPORT_WIFI, true, true);
        setConnectivity(NetworkCapabilities.TRANSPORT_CELLULAR, false, false);
        verifyLastMobileDataIndicators(true,
                DEFAULT_LEVEL,
                0, true);
    }

    protected void setWifiActivity(int activity) {
        // TODO: Not this, because this variable probably isn't sticking around.
        mNetworkController.mWifiSignalController.setActivity(activity);
    }

    protected void setWifiLevel(int level) {
        float amountPerLevel = (MAX_RSSI - MIN_RSSI) / (WifiIcons.WIFI_LEVEL_COUNT - 1);
        int rssi = (int)(MIN_RSSI + level * amountPerLevel);
        // Put RSSI in the middle of the range.
        rssi += amountPerLevel / 2;
        Intent i = new Intent(WifiManager.RSSI_CHANGED_ACTION);
        i.putExtra(WifiManager.EXTRA_NEW_RSSI, rssi);
        mNetworkController.onReceive(mContext, i);
    }

    protected void setWifiEnabled(boolean enabled) {
        Intent i = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        i.putExtra(WifiManager.EXTRA_WIFI_STATE,
                enabled ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);
        mNetworkController.onReceive(mContext, i);
    }

    protected void setWifiState(boolean connected, String ssid) {
        Intent i = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        NetworkInfo networkInfo = Mockito.mock(NetworkInfo.class);
        Mockito.when(networkInfo.isConnected()).thenReturn(connected);

        //TODO(b/69974497) mock of mWifiManager.getConnectionInfo() needed
        // Mockito.when(wifiInfo.getSSID()).thenReturn(ssid);

        i.putExtra(WifiManager.EXTRA_NETWORK_INFO, networkInfo);
        mNetworkController.onReceive(mContext, i);
    }

    protected void verifyLastQsDataDirection(boolean in, boolean out) {
        ArgumentCaptor<Boolean> inArg = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> outArg = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(mCallbackHandler, Mockito.atLeastOnce()).setWifiIndicators(
                anyBoolean(), any(), any(), inArg.capture(), outArg.capture(), any(), anyBoolean());
        assertEquals("WiFi data in, in quick settings", in, (boolean) inArg.getValue());
        assertEquals("WiFi data out, in quick settings", out, (boolean) outArg.getValue());
    }

    protected void verifyLastQsWifiIcon(boolean enabled, boolean connected, int icon,
            String description) {
        ArgumentCaptor<IconState> iconArg = ArgumentCaptor.forClass(IconState.class);
        ArgumentCaptor<Boolean> enabledArg = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> descArg = ArgumentCaptor.forClass(String.class);

        Mockito.verify(mCallbackHandler, Mockito.atLeastOnce()).setWifiIndicators(
                enabledArg.capture(), any(), iconArg.capture(), anyBoolean(),
                anyBoolean(), descArg.capture(), anyBoolean());
        IconState iconState = iconArg.getValue();
        assertEquals("WiFi enabled, in quick settings", enabled, (boolean) enabledArg.getValue());
        assertEquals("WiFi connected, in quick settings", connected, iconState.visible);
        assertEquals("WiFi signal, in quick settings", icon, iconState.icon);
        // TODO(b/69974497) Need to mock mWifiManager.getConnectionInfo() to supply the ssid.
        // assertEquals("WiFI desc (ssid), in quick settings", description, descArg.getValue());
    }

    protected void verifyLastWifiIcon(boolean visible, int icon) {
        ArgumentCaptor<IconState> iconArg = ArgumentCaptor.forClass(IconState.class);

        Mockito.verify(mCallbackHandler, Mockito.atLeastOnce()).setWifiIndicators(
                anyBoolean(), iconArg.capture(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean());
        IconState iconState = iconArg.getValue();
        assertEquals("WiFi visible, in status bar", visible, iconState.visible);
        assertEquals("WiFi signal, in status bar", icon, iconState.icon);
    }
}
