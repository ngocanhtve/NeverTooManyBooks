package com.eleybourn.bookcatalogue.backup.csv;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

public class ExportCSVTask
        extends AsyncTask<Void, Object, Void> {

    /** Fragment manager tag. */
    private static final String TAG = ExportCSVTask.class.getSimpleName();
    @NonNull
    private final ProgressDialogFragment<Void> mFragment;
    @NonNull
    private final Exporter mExporter;
    @NonNull
    private final File tmpFile;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param fragment ProgressDialogFragment
     * @param settings the export settings
     */
    @UiThread
    public ExportCSVTask(@NonNull final Context context,
                         @NonNull final ProgressDialogFragment<Void> fragment,
                         @NonNull final ExportSettings settings) {
        mFragment = fragment;
        mExporter = new CsvExporter(context, settings);
        tmpFile = StorageUtils.getFile(CsvExporter.EXPORT_TEMP_FILE_NAME);
    }

    @Override
    @UiThread
    protected void onCancelled(final Void result) {
        cleanup();
    }

    private void cleanup() {
        StorageUtils.deleteFile(tmpFile);
    }

    @Override
    @Nullable
    @WorkerThread
    protected Void doInBackground(final Void... params) {
        try (OutputStream out = new FileOutputStream(tmpFile)) {
            // start the export
            mExporter.doBooks(out, new Exporter.ExportListener() {
                @Override
                public void onProgress(@NonNull final String message,
                                       final int position) {
                    publishProgress(message, position);
                }

                @Override
                public boolean isCancelled() {
                    return ExportCSVTask.this.isCancelled();
                }

                @Override
                public void setMax(final int max) {
                    mFragment.setMax(max);
                }
            });

            if (isCancelled()) {
                return null;
            }
            // success
            CsvExporter.renameFiles(tmpFile);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(this, e);
            mException = e;
            cleanup();
        }
        return null;
    }

    /**
     * @param values: [0] String message, [1] Integer position/delta
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        mFragment.onProgress((String) values[0], (Integer) values[1]);
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Void result) {
        mFragment.onTaskFinished(mException == null, result);
    }
}
