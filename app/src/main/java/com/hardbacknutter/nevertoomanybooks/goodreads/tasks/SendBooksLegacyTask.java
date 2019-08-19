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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.Task;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;

/**
 * Background task class to send all books in the database to Goodreads.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
class SendBooksLegacyTask
        extends SendBooksLegacyTaskBase {

    private static final long serialVersionUID = -1933000305276643875L;

    /**
     * Flag indicating if we should only send UPDATED books to Goodreads;
     * {@code false} == all books.
     */
    private final boolean mUpdatesOnly;

    /** Last book ID processed. */
    private long mLastId;
    /** Total count of books processed. */
    private int mCount;
    /** Total count of books that are in cursor. */
    private int mTotalBooks;

    /**
     * Constructor.
     */
    SendBooksLegacyTask(@NonNull final String description,
                        final boolean updatesOnly) {
        super(description);
        mUpdatesOnly = updatesOnly;
    }

    /**
     * Check that no other sync-related jobs are queued, and that Goodreads is
     * authorized for this app.
     * <p>
     * This does network access and should not be called in the UI thread.
     *
     * @param grManager the Goodreads Manager
     *
     * @return StringRes id of message for user,
     * or {@link GoodreadsTasks#GR_RESULT_CODE_AUTHORIZED}
     * or {@link GoodreadsTasks#GR_RESULT_CODE_AUTHORIZATION_NEEDED}.
     */
    @WorkerThread
    @StringRes
    static int checkWeCanExport(@NonNull final GoodreadsManager grManager) {
        if (QueueManager.getQueueManager().hasActiveTasks(CAT_GOODREADS_EXPORT_ALL)) {
            return R.string.gr_tq_requested_task_is_already_queued;
        }
        if (QueueManager.getQueueManager().hasActiveTasks(CAT_GOODREADS_IMPORT_ALL)) {
            return R.string.gr_tq_import_task_is_already_queued;
        }

        // Make sure GR is authorized for this app
        if (grManager.hasValidCredentials()) {
            return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZED;
        } else {
            return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_NEEDED;
        }
    }

    /**
     * Perform the main task. Called from within {@link #run}
     * <p>
     * Deal with restarts by using mLastId as starting point.
     * (Remember: the task gets serialized to the taskqueue database.)
     *
     * @param context   Current context
     * @param grManager the Goodreads Manager
     *
     * @return {@code true} on success.
     */
    @Override
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsManager grManager) {

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBooksForExportToGoodreads(mLastId, mUpdatesOnly)) {

            mTotalBooks = bookCursor.getCount() + mCount;
            boolean needsRetryReset = true;
            while (bookCursor.moveToNext()) {
                if (!sendOneBook(queueManager, context, grManager, db, bookCursor)) {
                    // quit on error
                    return false;
                }

                // Update internal status
                mCount++;
                mLastId = bookCursor.getId();
                // If we have done one successfully, reset the counter so a
                // subsequent network error does not result in a long delay
                if (needsRetryReset) {
                    needsRetryReset = false;
                    resetRetryCounter();
                }

                queueManager.updateTask(this);

                if (isAborting()) {
                    queueManager.updateTask(this);
                    return false;
                }
            }
        }

        // Notify the user with a system Notification.
        App.showNotification(context,
                             context.getString(R.string.gr_title_send_book),
                             context.getString(R.string.gr_send_all_books_results,
                                               mCount, mSent, mNoIsbn, mNotFound));
        return true;
    }

    @Override
    public int getCategory() {
        return Task.CAT_GOODREADS_EXPORT_ALL;
    }

    /**
     * Provide a more informative description.
     *
     * @param context Current context
     */
    @NonNull
    @Override
    @CallSuper
    public String getDescription(@NonNull final Context context) {
        return super.getDescription(context)
               + " (" + context.getString(R.string.x_of_y, mCount, mTotalBooks) + ')';
    }
}
