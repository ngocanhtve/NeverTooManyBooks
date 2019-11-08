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
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class IsfdbGetBookTask
        extends AsyncTask<Void, Void, Bundle> {

    private static final String TAG = "IsfdbGetBookTask";

    /** Where to send our results to. */
    @NonNull
    private final WeakReference<IsfdbResultsListener> mTaskListener;

    private final long mIsfdbId;
    /** whether the TOC should get parsed for Series information. */
    private final boolean mAddSeriesFromToc;
    @Nullable
    private final List<IsfdbEditionsHandler.Edition> mEditions;

    /**
     * Constructor. Initiate a single book lookup by edition.
     *
     * @param editions         List of ISFDB native ID's
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param taskListener     where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(@NonNull final List<IsfdbEditionsHandler.Edition> editions,
                            final boolean addSeriesFromToc,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = 0;
        mEditions = editions;
        mAddSeriesFromToc = addSeriesFromToc;

        mTaskListener = new WeakReference<>(taskListener);
    }

    /**
     * Constructor. Initiate a single book lookup by ID.
     *
     * @param isfdbId          Single ISFDB native ID's
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param taskListener     where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(final long isfdbId,
                            final boolean addSeriesFromToc,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = isfdbId;
        mAddSeriesFromToc = addSeriesFromToc;
        mEditions = null;

        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Bundle doInBackground(final Void... params) {
        Thread.currentThread().setName("IsfdbGetBookTask");
        Context context = App.getAppContext();

        try {
            if (mEditions != null) {
                return new IsfdbBookHandler(context)
                        .fetch(mEditions, mAddSeriesFromToc, false);

            } else if (mIsfdbId != 0) {
                return new IsfdbBookHandler(context)
                        .fetch(mIsfdbId, mAddSeriesFromToc, false);

            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "doInBackground|how did we get here?", new Throwable());
                }
            }

        } catch (@NonNull final SocketTimeoutException e) {
            Logger.warn(context, TAG, "doInBackground", e.getLocalizedMessage());
        }

        return null;
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Bundle result) {
        // always send result, even if empty
        if (mTaskListener.get() != null) {
            mTaskListener.get().onGotIsfdbBook(result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onPostExecute" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }
}
