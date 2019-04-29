package com.eleybourn.bookcatalogue.backup.csv;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentManager;

import java.io.FileInputStream;
import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.LocalCoverFinder;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;

public class ImportCSVTask
        extends AsyncTask<Void, Object, Void> {

    /** Fragment manager tag. */
    private static final String TAG = ImportCSVTask.class.getSimpleName();

    private final ImportSettings mSettings;
    private final Importer mImporter;
    private final ProgressDialogFragment<Void> mFragment;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    private Exception mException;

    /**
     * Constructor.
     *
     * @param context  caller context
     * @param fragment ProgressDialogFragment
     * @param settings the import settings
     */
    @UiThread
    public ImportCSVTask(@NonNull final Context context,
                         @NonNull final ProgressDialogFragment<Void> fragment,
                         @NonNull final ImportSettings settings) {

        mFragment = fragment;
        mSettings = settings;
        mImporter = new CsvImporter(context, settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Void doInBackground(final Void... params) {

        try (FileInputStream in = new FileInputStream(mSettings.file)) {
            //noinspection ConstantConditions
            mImporter.doBooks(in, new LocalCoverFinder(mSettings.file.getParent()),
                              new Importer.ImportListener() {

                                  @Override
                                  public void onProgress(@NonNull final String message,
                                                         final int position) {
                                      publishProgress(message, position);
                                  }

                                  @Override
                                  public boolean isCancelled() {
                                      return ImportCSVTask.this.isCancelled();
                                  }

                                  @Override
                                  public void setMax(final int max) {
                                      mFragment.setMax(max);
                                  }
                              });

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
            Logger.error(this, e);
            mException = e;
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

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Void result) {
        mFragment.onTaskFinished(mException == null, result);
    }
}
