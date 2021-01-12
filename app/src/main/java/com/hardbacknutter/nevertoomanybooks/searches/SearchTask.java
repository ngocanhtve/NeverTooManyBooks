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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Searches a single {@link SearchEngine}.
 * <p>
 * When a context is needed, this class should call {@link SearchEngine#getAppContext()}
 * to ensure it runs/uses in the same context as the engine it is using.
 */
public class SearchTask
        extends LTask<Bundle> {

    static final int BY_EXTERNAL_ID = 0;
    static final int BY_ISBN = 1;
    static final int BY_BARCODE = 2;
    static final int BY_TEXT = 3;

    /** Log tag. */
    private static final String TAG = "SearchTask";

    /** progress title. e.g. "Searching Amazon". */
    private final String mProgressTitle;

    @NonNull
    private final SearchEngine mSearchEngine;
    /** whether to fetch thumbnails. */
    @Nullable
    private boolean[] mFetchThumbnail;
    /** What criteria to search by. */
    @By
    private int mBy;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mExternalId;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mIsbnStr;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mAuthor;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mTitle;
    /** Search criteria. Usage depends on {@link #mBy}. */
    @Nullable
    private String mPublisher;

    /**
     * Constructor. Will search according to passed parameters.
     * <ol>
     *      <li>external id</li>
     *      <li>valid ISBN</li>
     *      <li>valid barcode</li>
     *      <li>text</li>
     * </ol>
     *
     * @param searchEngine the search site engine
     * @param taskListener for the results
     */
    SearchTask(@NonNull final SearchEngine searchEngine,
               @NonNull final TaskListener<Bundle> taskListener) {
        super(searchEngine.getId(), taskListener);
        mSearchEngine = searchEngine;
        mSearchEngine.setCaller(this);

        mProgressTitle = mSearchEngine.getAppContext().getString(
                R.string.progress_msg_searching_site,
                searchEngine.getName(mSearchEngine.getAppContext()));
    }

    @NonNull
    public SearchEngine getSearchEngine() {
        return mSearchEngine;
    }

    void setSearchBy(@By final int by) {
        mBy = by;
    }

    /**
     * Set/reset the criteria.
     *
     * @param externalId to search for
     */
    void setExternalId(@Nullable final String externalId) {
        mExternalId = externalId;
    }

    /**
     * Set/reset the criteria.
     *
     * @param isbnStr to search for
     */
    void setIsbn(@Nullable final String isbnStr) {
        mIsbnStr = isbnStr;
    }

    /**
     * Set/reset the criteria.
     *
     * @param author to search for
     */
    void setAuthor(@Nullable final String author) {
        mAuthor = author;
    }

    /**
     * Set/reset the criteria.
     *
     * @param title to search for
     */
    void setTitle(@Nullable final String title) {
        mTitle = title;
    }

    /**
     * Set/reset the criteria.
     *
     * @param publisher to search for
     */
    void setPublisher(@Nullable final String publisher) {
        mPublisher = publisher;
    }

    /**
     * Set/reset the criteria.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     */
    void setFetchThumbnail(@Nullable final boolean[] fetchThumbnail) {
        if (fetchThumbnail == null || fetchThumbnail.length == 0) {
            mFetchThumbnail = new boolean[2];
        } else {
            mFetchThumbnail = fetchThumbnail;
        }
    }

    @Override
    protected void onPreExecute() {
        // sanity check
        Objects.requireNonNull(mBy, "mBy");
    }

    @NonNull
    @Override
    @WorkerThread
    protected Bundle doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG + ' ' + mSearchEngine.getName(context));

        publishProgress(new ProgressMessage(getTaskId(), mProgressTitle));

        try {
            // can we reach the site at all ?
            NetworkUtils.ping(context, mSearchEngine.getSiteUrl());

            // sanity check, see #setFetchThumbnail
            if (mFetchThumbnail == null) {
                mFetchThumbnail = new boolean[2];
            }

            final Bundle bookData;
            switch (mBy) {
                case BY_EXTERNAL_ID:
                    SanityCheck.requireValue(mExternalId, "mExternalId");
                    bookData = ((SearchEngine.ByExternalId) mSearchEngine)
                            .searchByExternalId(mExternalId, mFetchThumbnail);
                    break;

                case BY_ISBN:
                    SanityCheck.requireValue(mIsbnStr, "mIsbnStr");
                    bookData = ((SearchEngine.ByIsbn) mSearchEngine)
                            .searchByIsbn(mIsbnStr, mFetchThumbnail);
                    break;

                case BY_BARCODE:
                    SanityCheck.requireValue(mIsbnStr, "mIsbnStr");
                    bookData = ((SearchEngine.ByBarcode) mSearchEngine)
                            .searchByBarcode(mIsbnStr, mFetchThumbnail);
                    break;

                case BY_TEXT:
                    bookData = ((SearchEngine.ByText) mSearchEngine)
                            .search(mIsbnStr, mAuthor, mTitle, mPublisher, mFetchThumbnail);
                    break;

                default:
                    // we should never get here...
                    throw new IllegalArgumentException("SearchEngine "
                                                       + mSearchEngine.getName(context)
                                                       + " does not implement By=" + mBy);
            }

            return bookData;

        } catch (@NonNull final IOException | GeneralParsingException
                | RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return new Bundle();
        }
    }

    @IntDef({BY_EXTERNAL_ID, BY_ISBN, BY_BARCODE, BY_TEXT})
    @Retention(RetentionPolicy.SOURCE)
    @interface By {

    }
}
