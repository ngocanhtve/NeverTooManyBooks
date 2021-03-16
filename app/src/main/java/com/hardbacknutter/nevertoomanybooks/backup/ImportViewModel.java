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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReadMetaDataTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultIntentOwner;

public class ImportViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Log tag. */
    private static final String TAG = "ImportViewModel";

    public static final String BKEY_URL = TAG + ":url";

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    private final ArchiveReadMetaDataTask mArchiveReadMetaDataTask = new ArchiveReadMetaDataTask();
    private final ArchiveReaderTask mArchiveReaderTask = new ArchiveReaderTask();

    /** The import configuration. */
    @Nullable
    private ImportHelper mImportHelper;
    private boolean mInitWasCalled;
    @Nullable
    private ArchiveMetaData mArchiveMetaData;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (!mInitWasCalled) {
            mInitWasCalled = true;
            if (args != null) {
                final String url = args.getString(BKEY_URL);
                if (url != null) {
                    mImportHelper = ImportHelper.withRemoteServer(
                            Uri.parse(url), ArchiveEncoding.CalibreCS);
                }
            }
        }
    }

    @NonNull
    ImportHelper createImportHelper(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws InvalidArchiveException, FileNotFoundException {

        mImportHelper = ImportHelper.withFile(context, uri);
        return mImportHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return mImportHelper != null;
    }

    /**
     * The caller <strong>must</strong> ensure that the helper has been created previously.
     *
     * @return the helper
     *
     * @throws NullPointerException as a bug
     */
    @NonNull
    ImportHelper getImportHelper() {
        return Objects.requireNonNull(mImportHelper, "mImportHelper");
    }

    @Override
    protected void onCleared() {
        mArchiveReadMetaDataTask.cancel(true);
        mArchiveReaderTask.cancel(true);
        super.onCleared();
    }

    public void readMetaData() {
        Objects.requireNonNull(mImportHelper, "mImportHelper");
        mArchiveReadMetaDataTask.start(mImportHelper);
    }

    @NonNull
    public LiveData<FinishedMessage<ArchiveMetaData>> onMetaDataRead() {
        return mArchiveReadMetaDataTask.onFinished();
    }

    @NonNull
    public LiveData<FinishedMessage<Exception>> onMetaDataFailure() {
        return mArchiveReadMetaDataTask.onFailure();
    }

    @Nullable
    ArchiveMetaData getArchiveMetaData() {
        return mArchiveMetaData;
    }

    void setArchiveMetaData(@Nullable final ArchiveMetaData archiveMetaData) {
        mArchiveMetaData = archiveMetaData;
    }

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @NonNull
    Intent onImportFinished(@NonNull final ImportResults result) {
        mResultIntent.putExtra(ImportResults.BKEY_IMPORT_RESULTS, result);
        return mResultIntent;
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    boolean isReadyToGo() {
        if (mImportHelper != null && mImportHelper.getEncoding() == ArchiveEncoding.CalibreCS) {
            @Nullable
            final CalibreLibrary selectedLibrary = mImportHelper.getExtraArgs().getParcelable(
                    CalibreContentServer.BKEY_LIBRARY);
            return selectedLibrary != null && selectedLibrary.getTotalBooks() > 0;
        } else {
            return mArchiveMetaData != null;
        }
    }

    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mArchiveReaderTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<ImportResults>> onImportCancelled() {
        return mArchiveReaderTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onImportFailure() {
        return mArchiveReaderTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<ImportResults>> onImportFinished() {
        return mArchiveReaderTask.onFinished();
    }

    void startImport() {
        Objects.requireNonNull(mImportHelper, "mImportHelper");
        mArchiveReaderTask.start(mImportHelper);
    }

    void connectProgressDialog(@NonNull final ProgressDialogFragment dialog) {
        dialog.setCanceller(mArchiveReaderTask);
    }
}
