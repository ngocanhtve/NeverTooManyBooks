package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class to handle import in a separate thread.
 *
 * @author Philip Warner
 */
public class ImportThread extends ManagedTask {
    @NonNull
    private final String mFileSpec;
    @NonNull
    private final Importer.CoverFinder mCoverFinder;
    private final Importer.OnImporterListener mImportListener = new Importer.OnImporterListener() {

        @Override
        public void onProgress(@NonNull final String message, final int position) {
            if (position > 0) {
                mTaskManager.doProgress(ImportThread.this, message, position);
            } else {
                mTaskManager.doProgress(message);
            }
        }

		@Override
		public boolean isActive() {
			return !ImportThread.this.isCancelled();
		}

        @Override
        public void setMax(final int max) {
            mTaskManager.setMax(ImportThread.this, max);
        }
    };

    public ImportThread(@NonNull final TaskManager manager, @NonNull final String fileSpec) {
        super(manager);
        setName("ImportThread");

        final File file = new File(fileSpec);
        // Changed getCanonicalPath to getAbsolutePath based on this bug in Android 2.1:
        //     http://code.google.com/p/android/issues/detail?id=4961
        mFileSpec = file.getAbsolutePath();

        mCoverFinder = new LocalCoverFinder(
                // the source is the folder from which we are importing.
                file.getParent(),
                // If this is not the SharedStorage folder, we'll be doing copies, else renames (to 'cover' folder)
                StorageUtils.getSharedStorage().getAbsolutePath());

        //getMessageSwitch().addListener(getSenderId(), taskHandler, false);
        //Debug.startMethodTracing();
    }

    @Override
    protected void runTask() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(mFileSpec);
            new CsvImporter().importBooks(in, mCoverFinder, mImportListener, Importer.IMPORT_ALL);

            if (isCancelled()) {
                showBriefMessage(getString(R.string.cancelled));
            } else {
                showBriefMessage(getString(R.string.import_complete));
            }
        } catch (IOException e) {
            showBriefMessage(BookCatalogueApp.getResourceString(R.string.error_import_failed_is_location_correct));
            Logger.error(e);
        } finally {
            if (in != null && in.getChannel().isOpen())
                try {
                    in.close();
                } catch (IOException e) {
                    Logger.error(e);
                }
        }
    }

}
