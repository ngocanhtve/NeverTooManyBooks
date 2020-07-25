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
package com.hardbacknutter.nevertoomanybooks.searches.kbnl;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 * <p>
 * 2020-01-04: "http://opc4.kb.nl" is not available on https.
 * see "src/main/res/xml/network_security_config.xml"
 */
@SearchEngine.Configuration(
        id = SearchSites.KB_NL,
        nameResId = R.string.site_kb_nl,
        url = "http://opc4.kb.nl",
        prefKey = KbNlSearchEngine.PREF_KEY,
        lang = "nl",
        country = "NL",
        supportsMultipleCoverSizes = true
)
public class KbNlSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.CoverByIsbn {

    /** Preferences prefix. */
    public static final String PREF_KEY = "kbnl";
    /** Type: {@code String}. */
    @VisibleForTesting
    public static final String PREFS_HOST_URL = PREF_KEY + ".host.url";

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";
    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_KB";

    /* Response with English labels. */
    //private static final String BOOK_URL =
    //      "/DB=1/SET=1/TTL=1/LNG=EN/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";
    /* param 1: site specific author id. */
//    private static final String AUTHOR_URL = getBaseURL(context)
//    + "/DB=1/SET=1/TTL=1/REL?PPN=%1$s";
    /**
     * Response with Dutch labels.
     * <p>
     * param 1: isb
     */
    private static final String BOOK_URL =
            "/DB=1/SET=1/TTL=1/LNG=NE/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public KbNlSearchEngine(@NonNull final Context appContext) {
        super(appContext);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final String url = getSiteUrl() + String.format(BOOK_URL, validIsbn);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final KbNlBookHandler handler = new KbNlBookHandler(new Bundle());

        // Don't follow redirects, so we get the XML instead of the rendered page
        try (TerminatorConnection con = createConnection(url, false)) {
            final SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "searchByIsbn", e);
            }
            throw new IOException(e);
        }

        final Bundle bookData = handler.getResult();

        if (isCancelled()) {
            return bookData;
        }

        if (fetchThumbnail[0]) {
            searchBestCoverImageByIsbn(validIsbn, 0, bookData);
        }
        return bookData;
    }

    /**
     * Ths kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        final String sizeSuffix;
        if (size == null) {
            sizeSuffix = "large";
        } else {
            switch (size) {
                case Small:
                    sizeSuffix = "small";
                    break;
                case Medium:
                    sizeSuffix = "medium";
                    break;
                case Large:
                default:
                    sizeSuffix = "large";
                    break;
            }
        }

        final String url = String.format(BASE_URL_COVERS, validIsbn, sizeSuffix);
        return saveImage(url, validIsbn, FILENAME_SUFFIX + "_" + sizeSuffix, 0);
    }
}
