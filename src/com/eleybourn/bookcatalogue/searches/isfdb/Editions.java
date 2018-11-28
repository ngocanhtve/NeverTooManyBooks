package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

class Editions extends AbstractBase {

    private static final String EDITIONS_URL = "/cgi-bin/se.cgi?arg=%s&type=ISBN";
    private List<String> mEditions;

    /**
     * We assume the isbn is already checked & valid!
     */
    Editions(final @NonNull String isbn) {
        mPath = ISFDBManager.getBaseURL() + String.format(EDITIONS_URL, isbn);
    }

    /**
     * @return a list with native ISFDB book id's pointing to individual editions (with the same isbn)
     */
    @NonNull
    long[] getBookIds() throws SocketTimeoutException {
        if (mEditions == null) {
            fetch();
        }
        long[] ids = new long[mEditions.size()];

        for (int i = 0; i < mEditions.size(); i++) {
            ids[i] = stripNumber(mEditions.get(i));
        }
        return ids;
    }

    /**
     * Example return:  "http://www.isfdb.org/cgi-bin/pl.cgi?230949"
     *
     * Fails silently, returning an empty list.
     *
     * @return a list with URLs pointing to individual editions (with the same isbn)
     */
    List<String> fetch() throws SocketTimeoutException {
        if (mEditions == null) {
            mEditions = new ArrayList<>();
        }
        if (!loadPage()) {
            return mEditions;
        }

        findEntries(mDoc, "tr.table0", "tr.table1");
        // if no editions found, we might have been redirected to the book itself
        if (mEditions.size() == 0) {
            // check if the url looks like "http://www.isfdb.org/cgi-bin/pl.cgi?597467"
            if (mDoc.location().contains("pl.cgi")) {
                mEditions.add(mDoc.location());
            }
        }

        if (DEBUG_SWITCHES.ISFDB_SEARCH && BuildConfig.DEBUG) {
            Logger.info(this, mEditions.toString());
        }
        return mEditions;
    }

    private void findEntries(final @NonNull Document doc, final @NonNull String... selectors) {
        for (String selector : selectors) {
            Elements entries = doc.select(selector);
            for (Element entry : entries) {
                Element edLink = entry.select("a").first(); // first column has the book link
                if (edLink != null) {
                    String url = edLink.attr("href");
                    if (url != null) {
                        mEditions.add(url);
                    }
                }
            }
        }
    }
}
