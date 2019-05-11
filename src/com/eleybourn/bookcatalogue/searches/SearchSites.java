package com.eleybourn.bookcatalogue.searches;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.App;

/**
 * Manages the setup of search engines/sites.
 * <p>
 * <br>NEWKIND: adding a new search engine:
 * A search engine for a particular site should implement {@link SearchSiteManager}.
 * Configure in this class here below:
 * <ol>
 * <li>Add an identifier (bit) + add it to {@link Site#SEARCH_ALL}.</li>
 * <li>Add your new engine to {@link Site#getSearchSiteManager};</li>
 * <li>Create+add a new {@link Site} instance to {@link #SEARCH_ORDER_DEFAULTS}
 * and {@link #COVER_SEARCH_ORDER_DEFAULTS}</li>
 * <li>Optional: add to res/xml/preferences.xml if the url should be editable.</li>
 * </ol>
 */
public final class SearchSites {

    /** tag. */
    public static final String TAG = SearchSites.class.getSimpleName();

    /** */
    public static final String BKEY_SEARCH_SITES = TAG + ":searchSitesList";

    /** the default search site order for standard data/covers. */
    private static final ArrayList<Site> SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** the default search site order for _dedicated_ cover searches. */
    private static final ArrayList<Site> COVER_SEARCH_ORDER_DEFAULTS = new ArrayList<>();
    /** ENHANCE: reliability order is not user configurable for now, but plumbing installed. */
    private static final List<Site> PREFERRED_RELIABILITY_ORDER;

    /** the users preferred search site order. */
    private static ArrayList<Site> sPreferredSearchOrder;
    /** the users preferred search site order specific for covers. */
    private static ArrayList<Site> sPreferredCoverSearchOrder;

    /*
     * default search order.
     *
     *  Original app reliability order was:
     *  {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING}
     */
    static {
        /*
         * standard searches for full details.
         */
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_AMAZON, 0, 1));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOODREADS, 1, 0));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOOGLE, 2, 2));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_LIBRARY_THING, 3, 3));
        SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_ISFDB, 4, 4));

        // bottom of the list, and disabled by default
        Site openLibrary = new Site(Site.SEARCH_OPEN_LIBRARY, 5, 5);
        openLibrary.setEnabled(false);
        SEARCH_ORDER_DEFAULTS.add(openLibrary);

        /* ************************************************************************************** */
        /*
         * dedicated cover lookup; does not use a reliability index.
         */
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOOGLE, "cover", 0));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_LIBRARY_THING, "cover", 1));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_ISFDB, "cover", 2));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_GOODREADS, "cover", 3));
        COVER_SEARCH_ORDER_DEFAULTS.add(new Site(Site.SEARCH_AMAZON, "cover", 4));

        // bottom of the list, and disabled by default
        openLibrary = new Site(Site.SEARCH_OPEN_LIBRARY, "cover", 5);
        openLibrary.setEnabled(false);
        COVER_SEARCH_ORDER_DEFAULTS.add(openLibrary);

        /* ************************************************************************************** */

        PREFERRED_RELIABILITY_ORDER = new ArrayList<>(SEARCH_ORDER_DEFAULTS);
        /*
         * Create the user configurable lists.
         */
        // we're going to use set(index,...), so make them big enough
        sPreferredSearchOrder = new ArrayList<>(SEARCH_ORDER_DEFAULTS);
        sPreferredCoverSearchOrder = new ArrayList<>(COVER_SEARCH_ORDER_DEFAULTS);
        // yes, this shows that sPreferredSearchOrder should be Map's but for now
        // the code was done with List so this was the easiest to make them configurable.
        // To be redone.
        for (Site searchSite : SEARCH_ORDER_DEFAULTS) {
            sPreferredSearchOrder.set(searchSite.getPriority(), searchSite);
            PREFERRED_RELIABILITY_ORDER.set(searchSite.getReliability(), searchSite);
        }
        for (Site searchSite : COVER_SEARCH_ORDER_DEFAULTS) {
            sPreferredCoverSearchOrder.set(searchSite.getPriority(), searchSite);
        }
    }

    private SearchSites() {
    }

    /**
     * Reset all back to the hardcoded defaults.
     */
    public static void reset() {
        setSearchOrder(SEARCH_ORDER_DEFAULTS);
        setCoverSearchOrder(COVER_SEARCH_ORDER_DEFAULTS);
    }

    @NonNull
    static List<Site> getSitesByReliability() {
        return PREFERRED_RELIABILITY_ORDER;
    }

    @NonNull
    public static ArrayList<Site> getSites() {
        return sPreferredSearchOrder;
    }

    /**
     * Update the standard search order/enabled list.
     *
     * @param newList to use
     */
    public static void setSearchOrder(@NonNull final ArrayList<Site> newList) {
        sPreferredSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }

    @NonNull
    public static ArrayList<Site> getSitesForCoverSearches() {
        return sPreferredCoverSearchOrder;
    }

    /**
     * Update the dedicated cover search order/enabled list.
     *
     * @param newList to use
     */
    public static void setCoverSearchOrder(@NonNull final ArrayList<Site> newList) {
        sPreferredCoverSearchOrder = newList;
        SharedPreferences.Editor ed = App.getPrefs().edit();
        for (Site site : newList) {
            site.saveToPrefs(ed);
        }
        ed.apply();
    }
}
