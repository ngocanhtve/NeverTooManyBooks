/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.goodreads.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertomanybooks.utils.NetworkUtils;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookLegacyTask} which is used internally by
 * {@link SendBooksLegacyTask}. The core of the task is (should be) identical.
 */
public class SendOneBookTask
        extends TaskBase<Integer> {

    private final long mBookId;

    /**
     * Constructor.
     *
     * @param bookId       the book to send
     * @param taskListener for sending progress and finish messages to.
     */
    public SendOneBookTask(final long bookId,
                           @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_SEND_ONE_BOOK, taskListener);
        mBookId = bookId;
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendOneBookTask " + mBookId);

        GoodreadsManager.ExportResult result = null;
        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }

            GoodreadsManager grManager = new GoodreadsManager();
            if (!grManager.hasValidCredentials()) {
                return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_NEEDED;
            }

            try (DAO db = new DAO();
                 BookCursor bookCursor = db.fetchBookForExportToGoodreads(mBookId)) {
                if (bookCursor.moveToFirst()) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }
                    publishProgress(new TaskProgressMessage(mTaskId,
                                                            R.string.progress_msg_sending));
                    result = grManager.sendOneBook(db, bookCursor.getCursorRow());
                    if (result == GoodreadsManager.ExportResult.sent) {
                        // Record the update
                        db.setGoodreadsSyncDate(mBookId);
                    }
                }
            }
        } catch (@NonNull final CredentialsException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.credentialsError;
            mException = e;
        } catch (@NonNull final BookNotFoundException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.notFound;
            mException = e;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.ioError;
            mException = e;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.error;
            mException = e;
        }

        if (result != null) {
            return result.getReasonStringId();
        }
        return R.string.error_unexpected_error;
    }
}
