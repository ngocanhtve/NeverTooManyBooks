/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.googlebooks;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * FIXME: migrate to new googlebooks API or drop Google altogether?
 * <p>
 * The url's and xml formats used here are deprecated (but still works fine)
 * https://developers.google.com/gdata/docs/directory
 * https://developers.google.com/gdata/docs/2.0/reference
 * <p>
 * <p>
 * The new API:
 * <a href="https://developers.google.com/books/docs/v1/getting_started?csw=1">Getting started</a>
 *
 * <a href="https://developers.google.com/books/terms.html">T&C</a>
 * You may not charge users any fee for the use of your application,...
 * => so it seems if this SearchEngine is included, the entire app has to be free.
 * <p>
 * example:
 * https://stackoverflow.com/questions/7908954/google-books-api-searching-by-isbn
 */
@SearchEngine.Configuration(
        id = SearchSites.GOOGLE_BOOKS,
        nameResId = R.string.site_google_books,
        url = "https://books.google.com",
        prefKey = "googlebooks"
)
public final class GoogleBooksSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public GoogleBooksSearchEngine(@NonNull final Context appContext) {
        super(appContext);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {
        // %3A  :
        final String url = getSiteUrl() + "/books/feeds/volumes?q=ISBN%3A" + validIsbn;
        return fetchBook(url, fetchThumbnail, new Bundle());
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        // %2B  +
        // %3A  :
        if (author != null && !author.isEmpty()
            && title != null && !title.isEmpty()) {
            final String url = getSiteUrl() + "/books/feeds/volumes?q="
                               + "intitle%3A" + encodeSpaces(title)
                               + "%2B"
                               + "inauthor%3A" + encodeSpaces(author);
            return fetchBook(url, fetchThumbnail, new Bundle());

        } else {
            return new Bundle();
        }
    }

    private Bundle fetchBook(@NonNull final String url,
                             @NonNull final boolean[] fetchThumbnail,
                             @NonNull final Bundle bookData)
            throws IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            final SAXParser parser = factory.newSAXParser();

            // get the booklist, can return multiple books ('entry' elements)
            final GoogleBooksListHandler listHandler = new GoogleBooksListHandler();
            try (TerminatorConnection con = createConnection(url, true)) {
                parser.parse(con.getInputStream(), listHandler);
            }

            if (isCancelled()) {
                return bookData;
            }

            final List<String> urlList = listHandler.getResult();

            // The entry handler takes care of an individual book ('entry')
            final GoogleBooksEntryHandler handler =
                    new GoogleBooksEntryHandler(this, fetchThumbnail, bookData);
            if (!urlList.isEmpty()) {
                // only using the first one found, maybe future enhancement?
                final String oneBookUrl = urlList.get(0);

                try (TerminatorConnection con = createConnection(oneBookUrl, true)) {
                    parser.parse(con.getInputStream(), handler);
                }
            }
            return handler.getResult();

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "fetchBook", e);
            }
            throw new IOException(e);
        }
    }

    /**
     * replace spaces with %20.
     *
     * @param s String to encode
     *
     * @return encodes string
     */
    private String encodeSpaces(@NonNull final CharSequence s) {
//        return URLEncoder.encode(s, "UTF-8");
        return SPACE_PATTERN.matcher(s).replaceAll("%20");
    }
}
