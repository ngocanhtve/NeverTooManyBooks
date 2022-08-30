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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Searches a single {@link SearchEngine}.
 */
public class SearchTask
        extends LTask<Bundle> {

    /** Log tag. */
    private static final String TAG = "SearchTask";

    @NonNull
    private final SearchEngine searchEngine;
    /** Whether to fetch covers. */
    @Nullable
    private boolean[] fetchCovers;
    /** What criteria to search by. */
    private By by;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String externalId;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String isbnStr;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String author;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String title;
    /** Search criteria. Usage depends on {@link #by}. */
    @Nullable
    private String publisher;

    /**
     * Constructor. Will search according to passed parameters.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param taskId       a unique task identifier, returned with each message
     * @param searchEngine the search site engine
     * @param taskListener for the results
     */
    SearchTask(final int taskId,
               @NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Bundle> taskListener) {
        super(taskId, TAG + ' ' + searchEngine.getName(ServiceLocator.getAppContext()),
              taskListener);

        this.searchEngine = searchEngine;
        this.searchEngine.setCaller(this);
    }

    @NonNull
    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    void setSearchBy(@NonNull final By by) {
        this.by = by;
    }

    @NonNull
    public By getSearchBy() {
        return by;
    }

    /**
     * Set/reset the criteria.
     *
     * @param externalId to search for
     */
    void setExternalId(@Nullable final String externalId) {
        this.externalId = externalId;
    }

    /**
     * Set/reset the criteria.
     *
     * @param isbnStr to search for
     */
    void setIsbn(@Nullable final String isbnStr) {
        this.isbnStr = isbnStr;
    }

    /**
     * Set/reset the criteria.
     *
     * @param author to search for
     */
    void setAuthor(@Nullable final String author) {
        this.author = author;
    }

    /**
     * Set/reset the criteria.
     *
     * @param title to search for
     */
    void setTitle(@Nullable final String title) {
        this.title = title;
    }

    /**
     * Set/reset the criteria.
     *
     * @param publisher to search for
     */
    void setPublisher(@Nullable final String publisher) {
        this.publisher = publisher;
    }

    /**
     * Set/reset the criteria.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    void setFetchCovers(@Nullable final boolean[] fetchCovers) {
        if (fetchCovers == null || fetchCovers.length == 0) {
            this.fetchCovers = new boolean[2];
        } else {
            this.fetchCovers = fetchCovers;
        }
    }

    void startSearch() {
        execute();
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (searchEngine) {
            searchEngine.cancel();
        }
    }

    @NonNull
    @Override
    @WorkerThread
    protected Bundle doWork(@NonNull final Context context)
            throws StorageException, SearchException, CredentialsException, IOException {

        publishProgress(1, context.getString(R.string.progress_msg_searching_site,
                                             searchEngine.getName(context)));

        // Checking this each time a search starts is not needed...
        // But it makes error handling slightly easier and doing
        // it here offloads it from the UI thread.
        if (!NetworkUtils.isNetworkAvailable(context)) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        // can we reach the site ?
        NetworkUtils.ping(searchEngine.getHostUrl());

        // sanity check, see #setFetchCovers
        if (fetchCovers == null) {
            fetchCovers = new boolean[2];
        }

        final Bundle bookData;
        switch (by) {
            case ExternalId:
                SanityCheck.requireValue(externalId, "externalId");
                bookData = ((SearchEngine.ByExternalId) searchEngine)
                        .searchByExternalId(context, externalId, fetchCovers);
                break;

            case Isbn:
                SanityCheck.requireValue(isbnStr, "isbnStr");
                bookData = ((SearchEngine.ByIsbn) searchEngine)
                        .searchByIsbn(context, isbnStr, fetchCovers);
                break;

            case Barcode:
                SanityCheck.requireValue(isbnStr, "isbnStr");
                bookData = ((SearchEngine.ByBarcode) searchEngine)
                        .searchByBarcode(context, isbnStr, fetchCovers);
                break;

            case Text:
                bookData = ((SearchEngine.ByText) searchEngine)
                        .search(context, isbnStr, author, title, publisher, fetchCovers);
                break;

            default:
                // we should never get here...
                throw new IllegalArgumentException("SearchEngine "
                                                   + searchEngine.getName(context)
                                                   + " does not implement By=" + by);
        }

        return bookData;
    }

    public enum By {
        ExternalId,
        Isbn,
        Barcode,
        Text
    }
}
