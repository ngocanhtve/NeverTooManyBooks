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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;

/**
 * Task to send a single books details to Goodreads.
 * This is used during retrying a formerly failed book and is
 * <strong>initiated by the queue manager</strong>.
 * <p>
 * This Task *MUST* be serializable hence can not contain
 * any references to UI components or similar objects.
 */
public class SendOneBookLegacyTask
        extends SendBooksLegacyTaskBase {

    /** Log tag. */
    private static final String TAG = "SendOneBookLegacyTask";
    private static final long serialVersionUID = -7285893840841719107L;

    /** id of book to send. */
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param bookId      Book to send
     */
    public SendOneBookLegacyTask(@NonNull final String description,
                                 final long bookId) {
        super(description);
        mBookId = bookId;
    }

    /**
     * Perform the main task. Called from within {@link #run}
     *
     * @param queueManager QueueManager
     * @param context      Current context
     * @param apiHandler   the Goodreads Manager
     *
     * @return {@code true} for success
     */
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsHandler apiHandler) {

        try (DAO db = new DAO(TAG);
             Cursor cursor = db.fetchBookForExportToGoodreads(mBookId)) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                if (!sendOneBook(queueManager, context, apiHandler, db, rowData)) {
                    // quit on error
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getCategory() {
        return TQTask.CAT_EXPORT_ONE;
    }
}
