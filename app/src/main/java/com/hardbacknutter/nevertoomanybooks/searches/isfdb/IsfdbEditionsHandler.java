/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Given an ISBN, search for other editions on the site.
 * <p>
 * Uses JSoup screen scraping.
 */
public class IsfdbEditionsHandler
        extends AbstractBase {

    private static final String TAG = "IsfdbEditionsHandler";

    /** Search URL template. */
    private static final String EDITIONS_URL = IsfdbManager.CGI_BIN
                                               + IsfdbManager.URL_SE_CGI + "?arg=%s&type=ISBN";

    /** List of ISFDB native book id for all found editions. */
    private final ArrayList<Edition> mEditions = new ArrayList<>();
    @NonNull
    private final Context mAppContext;

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    IsfdbEditionsHandler(@NonNull final Context appContext) {
        mAppContext = appContext;
    }

    /**
     * Fails silently, returning an empty list.
     *
     * @param isbn to get editions for. MUST be valid.
     *
     * @return a list with native ISFDB book ID's pointing to individual editions
     *
     * @throws SocketTimeoutException if the connection times out
     */
    public ArrayList<Edition> fetch(@NonNull final String isbn)
            throws SocketTimeoutException {

        String url = IsfdbManager.getBaseURL(mAppContext) + String.format(EDITIONS_URL, isbn);

        if (loadPage(mAppContext, url) == null) {
            // failed to load, return an empty list.
            return mEditions;
        }

        return parseDoc();
    }

    /**
     * Get the list with native ISFDB book ID's pointing to individual editions.
     *
     * @param url A fully qualified ISFDB search url
     *
     * @return list
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @SuppressWarnings("WeakerAccess")
    public ArrayList<Edition> fetchPath(@NonNull final String url)
            throws SocketTimeoutException {

        if (loadPage(mAppContext, url) == null) {
            // failed to load, return an empty list.
            return mEditions;
        }

        return parseDoc();
    }

    /**
     * Do the parsing of the Document.
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @NonNull
    private ArrayList<Edition> parseDoc() {
        // http://www.isfdb.org/cgi-bin/se.cgi?arg=0887331602&type=ISBN
        // http://www.isfdb.org/cgi-bin/pl.cgi?326539
        String pageUrl = mDoc.location();

        if (pageUrl.contains(IsfdbManager.URL_PL_CGI)) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            mEditions.add(new Edition(stripNumber(pageUrl, '?'), mDoc));

        } else if (pageUrl.contains(IsfdbManager.URL_TITLE_CGI)
                   || pageUrl.contains(IsfdbManager.URL_SE_CGI)
                   || pageUrl.contains(IsfdbManager.URL_ADV_SEARCH_RESULTS_CGI)) {
            // we have multiple editions. We get here from one of:
            // - direct link to the "title" of the publication; i.e. 'show the editions'
            // - search or advanced-search for the title.

            // first edition line is a "tr.table1", 2nd "tr.table0", 3rd "tr.table1" etc...
            findEntries(mDoc, "tr.table1", "tr.table0");
        } else {
            // dunno, let's log it
            Logger.warnWithStackTrace(TAG, "pageUrl=" + pageUrl);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
            Log.d(TAG, "EXIT|fetch|" + mEditions);
        }
        return mEditions;
    }

    /**
     * Search/scrape for the selectors to build the edition list.
     *
     * @param doc       to parse
     * @param selectors to search for
     */
    private void findEntries(@NonNull final Document doc,
                             @NonNull final String... selectors) {
        for (String selector : selectors) {
            Elements entries = doc.select(selector);
            for (Element entry : entries) {
                // first column has the book link
                Element edLink = entry.select("a").first();
                if (edLink != null) {
                    String url = edLink.attr("href");
                    if (url != null) {
                        mEditions.add(new Edition(stripNumber(url, '?')));
                    }
                }
            }
        }
    }

    /**
     * A data class for holding the ISFDB native book id and its (optional) doc (web page).
     */
    public static class Edition {

        /** The ISFDB native book ID. */
        final long isfdbId;
        /**
         * If a fetch of editions resulted in a single book returned (via redirects),
         * then the doc is kept here for immediate processing.
         */
        @Nullable
        final Document doc;

        /**
         * Constructor: we found a link to a book.
         *
         * @param isfdbId of the book link we found
         */
        Edition(final long isfdbId) {
            this.isfdbId = isfdbId;
            doc = null;
        }

        /**
         * Constructor: we found a single edition, the doc contains the book for further processing.
         *
         * @param isfdbId of the book we found
         * @param doc     of the book we found
         */
        Edition(final long isfdbId,
                @Nullable final Document doc) {
            this.isfdbId = isfdbId;
            this.doc = doc;
        }
    }
}
