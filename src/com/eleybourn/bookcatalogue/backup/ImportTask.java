package com.eleybourn.bookcatalogue.backup;

import android.support.annotation.NonNull;

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
public class ImportTask extends ManagedTask {
    @NonNull
    private TaskManager mManager;
    @NonNull
    private final String mFileSpec;
    @NonNull
    private final Importer.CoverFinder mCoverFinder;
    private final Importer.OnImporterListener mImportListener = new Importer.OnImporterListener() {

        @Override
        public void onProgress(final @NonNull String message, final int position) {
            if (position > 0) {
                mTaskManager.sendTaskProgressMessage(ImportTask.this, message, position);
            } else {
                mTaskManager.sendHeaderTaskProgressMessage(message);
            }
        }

		@Override
		public boolean isActive() {
			return !ImportTask.this.isCancelled();
		}

        @Override
        public void setMax(final int max) {
            mTaskManager.setMaxProgress(ImportTask.this, max);
        }
    };

    public ImportTask(final @NonNull TaskManager manager, final @NonNull String fileSpec) {
        super("ImportTask", manager);

        mManager = manager;

        final File file = new File(fileSpec);
        mFileSpec = file.getAbsolutePath();

        mCoverFinder = new LocalCoverFinder(mManager.getContext(),
                // the source is the folder from which we are importing.
                file.getParent(),
                // If this is not the SharedStorage folder, we'll be doing copies, else renames (to 'cover' folder)
                StorageUtils.getSharedStorage().getAbsolutePath());

        //getMessageSwitch().addListener(getId(), taskHandler, false);
        //Debug.startMethodTracing();
    }

    @Override
    protected void runTask() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(mFileSpec);
            new CsvImporter(mManager.getContext()).importBooks(in, mCoverFinder, mImportListener, Importer.IMPORT_ALL);

            if (isCancelled()) {
                showUserMessage(getString(R.string.progress_end_cancelled));
            } else {
                showUserMessage(getString(R.string.progress_end_import_complete));
            }
        } catch (IOException e) {
            showUserMessage(getString(R.string.error_import_failed_is_location_correct));
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

    @Override
    protected void onTaskFinish() {
        try {
            mCoverFinder.close();
        } catch (Exception ignore) {
        }
        super.onTaskFinish();
    }
}
