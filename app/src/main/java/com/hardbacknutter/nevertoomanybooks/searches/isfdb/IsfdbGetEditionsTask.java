/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * This task is bypassing {@link SearchEngine.AlternativeEditions}
 * as in this particular circumstance it's faster.
 */
public class IsfdbGetEditionsTask
        extends MTask<List<Edition>> {

    /** Log tag. */
    private static final String TAG = "IsfdbGetEditionsTask";

    /** The isbn we're looking up. */
    private String mIsbn;

    public IsfdbGetEditionsTask() {
        super(R.id.TASK_ID_SEARCH_EDITIONS, TAG);
    }

    @UiThread
    public void search(@NonNull final ISBN isbn) {
        mIsbn = isbn.asText();
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected List<Edition> doWork(@NonNull final Context context)
            throws IOException {

        final IsfdbSearchEngine searchEngine = (IsfdbSearchEngine)
                SearchEngineRegistry.getInstance().createSearchEngine(SearchSites.ISFDB);
        searchEngine.setCaller(this);

        return searchEngine.fetchEditionsByIsbn(mIsbn);
    }
}
