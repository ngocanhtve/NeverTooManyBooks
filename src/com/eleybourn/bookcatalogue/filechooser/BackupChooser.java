/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.filechooser;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.Date;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.BookCatalogueApp.PREF_LAST_BACKUP_FILE;

/**
 * FileChooser activity to choose an archive file to open/save
 *
 * @author pjw
 */
public class BackupChooser extends FileChooser implements
        MessageDialogFragment.OnMessageDialogResultListener,
        ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener,
        ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener {

    /** Used when saving state */
    private final static String BKEY_FILENAME = "BackupFileSpec";

    /** saving or opening */
    private static final int IS_ERROR = 0;
    private static final int IS_SAVE = 1;
    private static final int IS_OPEN = 2;

    private static final int DIALOG_OPEN_IMPORT_TYPE = 1;

    /** The backup file that will be created (if saving) */
    private File mBackupFile;

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the correct title
        this.setTitle(isSaveDialog() ? R.string.backup_to_archive : R.string.import_from_archive);

        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_FILENAME)) {
            String fileSpec = savedInstanceState.getString(BKEY_FILENAME);
            Objects.requireNonNull(fileSpec ,"No BKEY_FILENAME passed in ?");
            mBackupFile = new File(fileSpec);
        }
    }

    /**
     * Setup the default file name: blank for 'open', date-based for save
     */
    @NonNull
    private String getDefaultFileName() {
        if (isSaveDialog()) {
            final String sqlDate = DateUtils.toLocalSqlDateOnly(new Date());
            return BackupFileDetails.ARCHIVE_PREFIX + sqlDate.replace(" ", "-").replace(":", "") + BackupFileDetails.ARCHIVE_EXTENSION;
        } else {
            return "";
        }
    }

    /**
     * Create the fragment using the last backup for the path, and the default file name (if saving)
     */
    @NonNull
    @Override
    protected FileChooserFragment getChooserFragment() {
        String lastBackupFile = BookCatalogueApp.Prefs.getString(PREF_LAST_BACKUP_FILE, StorageUtils.getSharedStorage().getAbsolutePath());
        //noinspection ConstantConditions
        return FileChooserFragment.newInstance(new File(lastBackupFile), getDefaultFileName());
    }

    /**
     * Get a task suited to building a list of backup files.
     */
    @NonNull
    @Override
    public FileLister getFileLister(@NonNull File root) {
        return new BackupLister(root);
    }

    /**
     * Save the state
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mBackupFile != null) {
            outState.putString(BKEY_FILENAME, mBackupFile.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * If a file was selected, restore the archive.
     */
    @Override
    public void onOpen(@NonNull final File file) {
        ImportTypeSelectionDialogFragment frag = ImportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
        frag.show(getSupportFragmentManager(), null);
    }

    /**
     * If a file was selected, save the archive.
     */
    @Override
    public void onSave(@NonNull final File file) {
        ExportTypeSelectionDialogFragment frag = ExportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
        frag.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onTaskFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                               final int taskId,
                               final boolean success,
                               final boolean cancelled,
                               @NonNull final FragmentTask task) {

        // Is it a task we care about?
        switch (taskId) {
            case IS_SAVE: {
                if (!success) {
                    String msg = getString(R.string.error_backup_failed)
                            + " " + getString(R.string.please_check_sd_writable)
                            + "\n\n" + getString(R.string.if_the_problem_persists);

                    MessageDialogFragment frag = MessageDialogFragment.newInstance(IS_ERROR,
                            R.string.backup_to_archive, msg);
                    frag.show(getSupportFragmentManager(), null);
                    // Just return; user may want to try again
                    return;
                }
                if (cancelled) {
                    // Just return; user may want to try again
                    return;
                }
                // Show a helpful message
                String msg = getString(R.string.archive_complete_details,
                        mBackupFile.getParent(),
                        mBackupFile.getName(),
                        Utils.formatFileSize(mBackupFile.length()));
                MessageDialogFragment frag = MessageDialogFragment.newInstance(IS_SAVE,
                        R.string.backup_to_archive, msg);
                frag.show(getSupportFragmentManager(), null);
                break;
            }

            case IS_OPEN: {
                if (!success) {
                    String msg = getString(R.string.import_failed)
                            + " " + getString(R.string.please_check_sd_readable)
                            + "\n\n" + getString(R.string.if_the_problem_persists);

                    MessageDialogFragment frag = MessageDialogFragment.newInstance(IS_ERROR,
                            R.string.import_from_archive, msg);
                    frag.show(getSupportFragmentManager(), null);
                    // Just return; user may want to try again
                    return;
                }
                if (cancelled) {
                    // Just return; user may want to try again
                    return;
                }

                MessageDialogFragment frag = MessageDialogFragment.newInstance(IS_OPEN,
                        R.string.import_from_archive,
                        BookCatalogueApp.getResourceString(R.string.import_complete));
                frag.show(getSupportFragmentManager(), null);
                break;
            }
        }
    }

    @Override
    public void onAllTasksFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                                   final int taskId,
                                   final boolean success,
                                   final boolean cancelled) {
        // Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
    }

    @Override
    public void onMessageDialogResult(final int dialogId, final int button) {
        switch (dialogId) {
            case IS_OPEN:
            case IS_SAVE:
                finish();
                break;
            case IS_ERROR:
                break;
        }
    }

    @Override
    public void onImportTypeSelectionDialogResult(final int dialogId,
                                                  @NonNull final DialogFragment dialog,
                                                  @NonNull final ImportTypeSelectionDialogFragment.ImportSettings settings) {
        switch (settings.options) {
            case Importer.IMPORT_ALL:
                BackupManager.restore(this, settings.file, IS_OPEN, Importer.IMPORT_ALL);
                break;
            case Importer.IMPORT_NEW_OR_UPDATED:
                BackupManager.restore(this, settings.file, IS_OPEN, Importer.IMPORT_NEW_OR_UPDATED);
                break;
        }
    }

    @Override
    public void onExportTypeSelectionDialogResult(final int dialogId,
                                                  @NonNull final DialogFragment dialog,
                                                  @NonNull final ExportTypeSelectionDialogFragment.ExportSettings settings) {
        switch (settings.options) {
            case Exporter.EXPORT_ALL:
                mBackupFile = BackupManager.backup(this, settings.file, IS_SAVE, Exporter.EXPORT_ALL, null);
                break;
            case Exporter.EXPORT_NOTHING:
                return;
            default:
                if (settings.dateFrom == null) {
                    String lastBackup = BookCatalogueApp.Prefs.getString(BookCatalogueApp.PREF_LAST_BACKUP_DATE, null);
                    if (lastBackup != null && !lastBackup.isEmpty()) {
                        settings.dateFrom = DateUtils.parseDate(lastBackup);
                    } else {
                        settings.dateFrom = null;
                    }
                }
                mBackupFile = BackupManager.backup(this, settings.file, IS_SAVE, settings.options, settings.dateFrom);
                break;
        }
    }
}
