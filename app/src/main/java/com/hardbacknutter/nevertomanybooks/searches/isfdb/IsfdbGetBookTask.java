package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.util.List;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

public class IsfdbGetBookTask
        extends AsyncTask<Void, Void, Bundle> {

    /** Where to send our results to. */
    @NonNull
    private final WeakReference<IsfdbResultsListener> mTaskListener;

    private final long mIsfdbId;
    @Nullable
    private final List<Editions.Edition> mEditions;

    /**
     * Constructor. Initiate a single book lookup by edition.
     *
     * @param editions     List of ISFDB native ID's
     * @param taskListener where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(@NonNull final List<Editions.Edition> editions,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = 0;
        mEditions = editions;

        mTaskListener = new WeakReference<>(taskListener);
    }

    /**
     * Constructor. Initiate a single book lookup by id.
     *
     * @param isfdbId      Single ISFDB native ID's
     * @param taskListener where to send the results to
     */
    @UiThread
    public IsfdbGetBookTask(final long isfdbId,
                            @NonNull final IsfdbResultsListener taskListener) {
        mIsfdbId = isfdbId;
        mEditions = null;

        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Bundle doInBackground(final Void... params) {
        Thread.currentThread().setName("IsfdbGetBookTask");
        try {
            //TODO: should be using a user context.
            Context userContext = App.getAppContext();

            if (mEditions != null) {
                return new IsfdbBook().fetch(mEditions, false, userContext);

            } else if (mIsfdbId != 0) {
                return new IsfdbBook().fetch(mIsfdbId, false, userContext);
            } else {
                if (BuildConfig.DEBUG) {
                    Logger.debugWithStackTrace(this, "doInBackground", "how did we get here?");
                }
            }

        } catch (@NonNull final SocketTimeoutException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.warn(this, "doInBackground", e.getLocalizedMessage());
            }
        }

        return null;
    }

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Bundle result) {
        // always send result, even if empty
        if (mTaskListener.get() != null) {
            mTaskListener.get().onGotISFDBBook(result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }
}
