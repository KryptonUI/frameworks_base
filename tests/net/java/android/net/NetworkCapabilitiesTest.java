/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import static android.net.NetworkCapabilities.LINK_BANDWIDTH_UNSPECIFIED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CBS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_EIMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.RESTRICTED_CAPABILITIES;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.UNRESTRICTED_CAPABILITIES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetworkCapabilitiesTest {
    @Test
    public void testMaybeMarkCapabilitiesRestricted() {
        // verify EIMS is restricted
        assertEquals((1 << NET_CAPABILITY_EIMS) & RESTRICTED_CAPABILITIES,
                (1 << NET_CAPABILITY_EIMS));

        // verify CBS is also restricted
        assertEquals((1 << NET_CAPABILITY_CBS) & RESTRICTED_CAPABILITIES,
                (1 << NET_CAPABILITY_CBS));

        // verify default is not restricted
        assertEquals((1 << NET_CAPABILITY_INTERNET) & RESTRICTED_CAPABILITIES, 0);

        // just to see
        assertEquals(RESTRICTED_CAPABILITIES & UNRESTRICTED_CAPABILITIES, 0);

        // check that internet does not get restricted
        NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // metered-ness shouldn't matter
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // add EIMS - bundled with unrestricted means it's unrestricted
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertTrue(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // just a restricted cap should be restricted regardless of meteredness
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        // try 2 restricted caps
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_CBS);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.addCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
        netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_CBS);
        netCap.addCapability(NET_CAPABILITY_EIMS);
        netCap.removeCapability(NET_CAPABILITY_NOT_METERED);
        netCap.maybeMarkCapabilitiesRestricted();
        assertFalse(netCap.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
    }

    @Test
    public void testDescribeImmutableDifferences() {
        NetworkCapabilities nc1;
        NetworkCapabilities nc2;

        // Transports changing
        nc1 = new NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR);
        nc2 = new NetworkCapabilities().addTransportType(TRANSPORT_WIFI);
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Mutable capability changing
        nc1 = new NetworkCapabilities().addCapability(NET_CAPABILITY_VALIDATED);
        nc2 = new NetworkCapabilities();
        assertEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // NOT_METERED changing (http://b/63326103)
        nc1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_NOT_METERED)
                .addCapability(NET_CAPABILITY_INTERNET);
        nc2 = new NetworkCapabilities().addCapability(NET_CAPABILITY_INTERNET);
        assertEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Immutable capability changing
        nc1 = new NetworkCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
        nc2 = new NetworkCapabilities().addCapability(NET_CAPABILITY_INTERNET);
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));

        // Specifier changing
        nc1 = new NetworkCapabilities().addTransportType(TRANSPORT_WIFI);
        nc2 = new NetworkCapabilities()
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(new StringNetworkSpecifier("specs"));
        assertNotEquals("", nc1.describeImmutableDifferences(nc2));
        assertEquals("", nc1.describeImmutableDifferences(nc1));
    }

    @Test
    public void testLinkBandwidthUtils() {
        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, NetworkCapabilities
                .minBandwidth(LINK_BANDWIDTH_UNSPECIFIED, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(LINK_BANDWIDTH_UNSPECIFIED, 10));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(10, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .minBandwidth(10, 20));

        assertEquals(LINK_BANDWIDTH_UNSPECIFIED, NetworkCapabilities
                .maxBandwidth(LINK_BANDWIDTH_UNSPECIFIED, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(10, NetworkCapabilities
                .maxBandwidth(LINK_BANDWIDTH_UNSPECIFIED, 10));
        assertEquals(10, NetworkCapabilities
                .maxBandwidth(10, LINK_BANDWIDTH_UNSPECIFIED));
        assertEquals(20, NetworkCapabilities
                .maxBandwidth(10, 20));
    }

    @Test
    public void testSetUids() {
        final NetworkCapabilities netCap = new NetworkCapabilities();
        final Set<UidRange> uids = new ArraySet<>();
        uids.add(new UidRange(50, 100));
        uids.add(new UidRange(3000, 4000));
        netCap.setUids(uids);
        assertTrue(netCap.appliesToUid(50));
        assertTrue(netCap.appliesToUid(80));
        assertTrue(netCap.appliesToUid(100));
        assertTrue(netCap.appliesToUid(3000));
        assertTrue(netCap.appliesToUid(3001));
        assertFalse(netCap.appliesToUid(10));
        assertFalse(netCap.appliesToUid(25));
        assertFalse(netCap.appliesToUid(49));
        assertFalse(netCap.appliesToUid(101));
        assertFalse(netCap.appliesToUid(2000));
        assertFalse(netCap.appliesToUid(100000));

        assertTrue(netCap.appliesToUidRange(new UidRange(50, 100)));
        assertTrue(netCap.appliesToUidRange(new UidRange(70, 72)));
        assertTrue(netCap.appliesToUidRange(new UidRange(3500, 3912)));
        assertFalse(netCap.appliesToUidRange(new UidRange(1, 100)));
        assertFalse(netCap.appliesToUidRange(new UidRange(49, 100)));
        assertFalse(netCap.appliesToUidRange(new UidRange(1, 10)));
        assertFalse(netCap.appliesToUidRange(new UidRange(60, 101)));
        assertFalse(netCap.appliesToUidRange(new UidRange(60, 3400)));

        NetworkCapabilities netCap2 = new NetworkCapabilities();
        // A new netcap object has null UIDs, so anything will satisfy it.
        assertTrue(netCap2.satisfiedByUids(netCap));
        // Still not equal though.
        assertFalse(netCap2.equalsUids(netCap));
        netCap2.setUids(uids);
        assertTrue(netCap2.satisfiedByUids(netCap));
        assertTrue(netCap.equalsUids(netCap2));
        assertTrue(netCap2.equalsUids(netCap));

        uids.add(new UidRange(600, 700));
        netCap2.setUids(uids);
        assertFalse(netCap2.satisfiedByUids(netCap));
        assertFalse(netCap.appliesToUid(650));
        assertTrue(netCap2.appliesToUid(650));
        netCap.combineCapabilities(netCap2);
        assertTrue(netCap2.satisfiedByUids(netCap));
        assertTrue(netCap.appliesToUid(650));
        assertFalse(netCap.appliesToUid(500));

        assertTrue(new NetworkCapabilities().satisfiedByUids(netCap));
        netCap.combineCapabilities(new NetworkCapabilities());
        assertTrue(netCap.appliesToUid(500));
        assertTrue(netCap.appliesToUidRange(new UidRange(1, 100000)));
        assertFalse(netCap2.appliesToUid(500));
        assertFalse(netCap2.appliesToUidRange(new UidRange(1, 100000)));
        assertTrue(new NetworkCapabilities().satisfiedByUids(netCap));
    }

    @Test
    public void testParcelNetworkCapabilities() {
        final Set<UidRange> uids = new ArraySet<>();
        uids.add(new UidRange(50, 100));
        uids.add(new UidRange(3000, 4000));
        final NetworkCapabilities netCap = new NetworkCapabilities()
            .addCapability(NET_CAPABILITY_INTERNET)
            .setUids(uids)
            .addCapability(NET_CAPABILITY_EIMS)
            .addCapability(NET_CAPABILITY_NOT_METERED);
        assertEqualsThroughMarshalling(netCap);
    }

    @Test
    public void testOemPaid() {
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.maybeMarkCapabilitiesRestricted();
        assertFalse(nc.hasCapability(NET_CAPABILITY_OEM_PAID));
        assertTrue(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));

        nc.addCapability(NET_CAPABILITY_OEM_PAID);
        nc.maybeMarkCapabilitiesRestricted();
        assertTrue(nc.hasCapability(NET_CAPABILITY_OEM_PAID));
        assertFalse(nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED));
    }

    private void assertEqualsThroughMarshalling(NetworkCapabilities netCap) {
        Parcel p = Parcel.obtain();
        netCap.writeToParcel(p, /* flags */ 0);
        p.setDataPosition(0);
        byte[] marshalledData = p.marshall();

        p = Parcel.obtain();
        p.unmarshall(marshalledData, 0, marshalledData.length);
        p.setDataPosition(0);
        assertEquals(NetworkCapabilities.CREATOR.createFromParcel(p), netCap);
    }
}
