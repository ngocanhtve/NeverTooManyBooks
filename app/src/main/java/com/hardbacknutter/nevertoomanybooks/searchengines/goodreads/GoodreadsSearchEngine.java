/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.searchengines.goodreads;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

/**
 * <a href="https://www.goodreads.com">https://www.goodreads.com</a>
 * <p>
 * Goodreads is owned by Amazon and is shutting their API down.
 * LibraryThing is 40% owned by AbeBooks which is owned by Amazon and the API is already shut down.
 */
public class GoodreadsSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ViewBookByExternalId {

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public GoodreadsSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    @NonNull
    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(EngineId.Goodreads,
                                              R.string.site_goodreads,
                                              "https://www.goodreads.com")

                .setDomainKey(DBKey.SID_GOODREADS_BOOK)
                .setDomainViewId(R.id.site_goodreads)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_GOODREADS)
                .build();
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getHostUrl() + "/book/show/" + externalId;
    }
}

