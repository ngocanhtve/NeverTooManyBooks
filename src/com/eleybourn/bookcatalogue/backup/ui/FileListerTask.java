package com.eleybourn.bookcatalogue.backup.ui;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.ui.BRBaseActivity.FileDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskBase;
import com.eleybourn.bookcatalogue.tasks.TaskListener;

/**
 * Object to provide a FileListerFragmentTask specific to archive mFileDetails.
 */
public class FileListerTask
        extends TaskBase<ArrayList<FileDetails>> {

    /**
     * Perform case-insensitive sorting using system locale (i.e. mFileDetails are system objects).
     */
    private static final Comparator<FileDetails> FILE_DETAILS_COMPARATOR =
            (o1, o2) -> o1.getFile().getName().toLowerCase(App.getSystemLocale())
                          .compareTo(o2.getFile().getName()
                                       .toLowerCase(App.getSystemLocale()));

    @NonNull
    private final File mRootDir;

    /**
     * Constructor.
     *
     * @param rootDir      folder to list
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    FileListerTask(@NonNull final File rootDir,
                   @NonNull final TaskListener<ArrayList<FileDetails>> taskListener) {
        super(R.id.TASK_ID_FILE_LISTER, taskListener);
        mRootDir = rootDir;
    }

    @Override
    @NonNull
    @WorkerThread
    protected ArrayList<FileDetails> doInBackground(final Void... params) {
        Thread.currentThread().setName("FileListerTask");

        ArrayList<FileDetails> fileDetails = new ArrayList<>();

        // Filter for directories and our own archives.
        FileFilter fileFilter = file ->
                (file.isDirectory() && file.canWrite())
                        || (file.isFile() && BackupManager.isArchive(file));

        // Get a file list
        File[] files = mRootDir.listFiles(fileFilter);
        if (files == null) {
            return fileDetails;
        }

        for (File file : files) {
            BackupFileDetails fd = new BackupFileDetails(file);
            fileDetails.add(fd);
            if (BackupManager.isArchive(file)) {
                try (BackupReader reader = BackupManager.getReader(file)) {
                    fd.setInfo(reader.getInfo());
                } catch (@NonNull final IOException e) {
                    Logger.error(this, e);
                }
            }
        }

        Collections.sort(fileDetails, FILE_DETAILS_COMPARATOR);
        return fileDetails;
    }
}
