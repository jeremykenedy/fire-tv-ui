package com.jeremykenedy.firetv.ui;

import java.util.Arrays;
import java.util.Collections;

public final class PlexProfileConnectionTest {
    public static void main(String[] args) throws Exception {
        PlexConnection owner = new PlexConnection("http://fixture.invalid:32400", "owner-access", "server-one");
        PlexConnection.Profile calvin = new PlexConnection.Profile("calvin-profile", "Calvin", "calvin-access");
        PlexConnection.Profile second = new PlexConnection.Profile("second-profile", "Second", "second-access");
        PlexConnection household = owner.withProfiles(Arrays.asList(calvin, second));
        PlexConnection first = household.forProfile(calvin);
        PlexConnection other = household.forProfile(second);
        check(owner.sourceKey().equals(household.sourceKey()), "Adding profiles preserves existing owner card identities.");
        check(!first.sourceKey().equals(other.sourceKey()) && !first.sourceKey().equals(owner.sourceKey()),
                "The same server and media key have separate profile card identities.");
        check("calvin-access".equals(household.forSource(first.sourceKey()).token),
                "A Calvin card resolves only Calvin's PMS credential.");
        check("second-access".equals(household.forSource(other.sourceKey()).token),
                "A second user's card cannot resolve through Calvin or the owner.");
        check(household.forSource("old-source") == null, "A removed or foreign source cannot open with owner access.");
        check(household.withProfiles(Collections.<PlexConnection.Profile>emptyList()).forSource(first.sourceKey()) == null,
                "Removing a profile invalidates its existing card routes.");
        check(household.withFeeds(PlexFeedSettings.defaults()).profiles.size() == 2,
                "Changing ordinary feed options preserves authorized profile connections.");
        boolean duplicate = false;
        try { owner.withProfiles(Arrays.asList(calvin, calvin)); }
        catch (IllegalArgumentException expected) { duplicate = true; }
        check(duplicate, "The same profile cannot produce ambiguous credentials.");
        System.out.println("8 Plex profile connection checks passed.");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
