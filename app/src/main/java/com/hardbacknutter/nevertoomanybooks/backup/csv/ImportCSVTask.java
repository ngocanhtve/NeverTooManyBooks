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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListenerBase;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Task result: Integer: # books processed (i.e. not # books imported which could be lower)
 */
public class ImportCSVTask
        extends TaskBase<ImportHelper> {

    private static final String TAG = "ImportCSVTask";

    @NonNull
    private final Uri mUri;
    @NonNull
    private final ImportHelper mImportHelper;
    @NonNull
    private final Importer mImporter;
    private final ProgressListener mProgressListener = new ProgressListenerBase() {
        private int mPos;

        @Override
        public void onProgress(final int pos,
                               @Nullable final String message) {
            mPos = pos;
            publishProgress(new TaskListener.ProgressMessage(mTaskId, getMax(), pos, message));
        }

        @Override
        public void onProgressStep(final int delta,
                                   @Nullable final String message) {
            mPos += delta;
            publishProgress(new TaskListener.ProgressMessage(mTaskId, getMax(), mPos, message));
        }

        @Override
        public boolean isCancelled() {
            return ImportCSVTask.this.isCancelled();
        }
    };

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param uri          to read from
     * @param importHelper the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ImportCSVTask(@NonNull final Context context,
                         @NonNull final Uri uri,
                         @NonNull final ImportHelper importHelper,
                         @NonNull final TaskListener<ImportHelper> taskListener) {
        super(R.id.TASK_ID_CSV_IMPORT, taskListener);
        mUri = uri;
        mImportHelper = importHelper;
        mImporter = new CsvImporter(context, importHelper);
    }

    @Override
    @UiThread
    protected void onCancelled(@NonNull final ImportHelper result) {
        cleanup();
        super.onCancelled(result);
    }

    @Override
    @WorkerThread
    @NonNull
    protected ImportHelper doInBackground(final Void... params) {
        Thread.currentThread().setName("ImportCSVTask");

        Context localContext = App.getLocalizedAppContext();

        try (InputStream is = localContext.getContentResolver().openInputStream(mUri)) {
            if (is == null) {
                throw new IOException("InputStream was NULL");
            }

            mImportHelper.addResults(mImporter.doBooks(localContext, is,
                                                       null, mProgressListener));

            return mImportHelper;

        } catch (@NonNull final IOException | ImportException e) {
            Logger.error(localContext, TAG, e);
            mException = e;
            return mImportHelper;
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        try {
            mImporter.close();
        } catch (@NonNull final IOException ignore) {
        }
    }
}
