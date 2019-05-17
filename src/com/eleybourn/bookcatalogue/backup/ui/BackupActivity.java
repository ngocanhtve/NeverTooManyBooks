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
package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupTask;
import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Lets the user choose an archive file to backup to.
 *
 * @author pjw
 */
public class BackupActivity
        extends BRBaseActivity {

    private final TaskListener<Object, ExportOptions> mTaskListener =
            new TaskListener<Object, ExportOptions>() {
                /**
                 * Listener for tasks.
                 *
                 * @param taskId  a task identifier
                 * @param success {@code true} for success.
                 * @param result  {@link ExportOptions}
                 */
                @Override
                public void onTaskFinished(final int taskId,
                                           final boolean success,
                                           @NonNull final ExportOptions result,
                                           @Nullable final Exception e) {

                    //noinspection SwitchStatementWithTooFewBranches
                    switch (taskId) {
                        case R.id.TASK_ID_WRITE_TO_ARCHIVE:
                            if (success) {
                                //noinspection ConstantConditions
                                String msg = getString(R.string.export_info_success_archive_details,
                                                       result.file.getParent(),
                                                       result.file.getName(),
                                                       Utils.formatFileSize(getResources(),
                                                                            result.file.length()));

                                new AlertDialog.Builder(BackupActivity.this)
                                        .setTitle(R.string.lbl_backup)
                                        .setMessage(msg)
                                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                                            d.dismiss();
                                            Intent data = new Intent().putExtra(
                                                    UniqueId.BKEY_EXPORT_RESULT,
                                                    result.what);
                                            setResult(Activity.RESULT_OK, data);
                                            finish();
                                        })
                                        .create()
                                        .show();
                            } else {
                                String msg = getString(R.string.error_backup_failed)
                                        + ' ' + getString(R.string.error_storage_not_writable)
                                        + "\n\n" + getString(
                                        R.string.error_if_the_problem_persists);

                                new AlertDialog.Builder(BackupActivity.this)
                                        .setTitle(R.string.lbl_backup)
                                        .setMessage(msg)
                                        .setPositiveButton(android.R.string.ok,
                                                           (d, which) -> d.dismiss())
                                        .create()
                                        .show();
                            }
                            break;

                        default:
                            Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                            break;
                    }
                }
            };

    private final ExportOptionsDialogFragment.OptionsListener mOptionsListener =
            BackupActivity.this::onOptionsSet;

    private ProgressDialogFragment<Object, ExportOptions> mProgressDialog;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();

        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ExportOptions>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTaskListener(mTaskListener);
//            mProgressDialog.setOnUserCancelledListener(this);
        }

        if (fm.findFragmentByTag(FileChooserFragment.TAG) == null) {
            String defaultFilename = getString(R.string.app_name) + '-'
                    + DateUtils.localSqlDateForToday()
                               .replace(" ", "-")
                               .replace(":", "")
                    + BackupManager.ARCHIVE_EXTENSION;

            createFileBrowser(defaultFilename);
        }

        Button confirm = findViewById(R.id.confirm);
        confirm.setText(R.string.btn_confirm_save);
        confirm.setOnClickListener(v -> doBackup());

        setTitle(R.string.lbl_backup);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (ExportOptionsDialogFragment.TAG.equals(fragment.getTag())) {
            ((ExportOptionsDialogFragment)fragment).setListener(mOptionsListener);
        }
    }

    /**
     * Local handler for 'Save'. Perform basic validation, and pass on.
     */
    private void doBackup() {
        FileChooserFragment frag = (FileChooserFragment)
                getSupportFragmentManager().findFragmentByTag(FileChooserFragment.TAG);
        if (frag != null) {
            File file = frag.getSelectedFile();
            if (file.exists() && !file.isFile()) {
                //noinspection ConstantConditions
                UserMessage.showUserMessage(frag.getView(),
                                            R.string.warning_select_a_non_directory);
                return;
            }

            final ExportOptions options = new ExportOptions();
            options.file = file;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup)
                    .setMessage(R.string.export_info_backup_all)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.btn_options, (d, which) -> {
                        // ask user what options they want
                        FragmentManager fm = getSupportFragmentManager();
                        if (fm.findFragmentByTag(ExportOptionsDialogFragment.TAG) == null) {
                            ExportOptionsDialogFragment.newInstance(options).show(fm, ExportOptionsDialogFragment.TAG);
                        }
                    })
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        // User wants to backup all.
                        options.what = ExportOptions.ALL;
                        onOptionsSet(options);
                    })
                    .create()
                    .show();
        }
    }

    /**
     * kick of the backup task.
     */
    public void onOptionsSet(@NonNull final ExportOptions options) {
        // sanity check
        if (options.what == ExportOptions.NOTHING) {
            return;
        }

        // backup 'since'
        if ((options.what & ExportOptions.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (options.dateFrom == null) {
                String lastBackup = App.getPrefs()
                                       .getString(BackupManager.PREF_LAST_BACKUP_DATE, null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    options.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // cannot have a dateFrom when not asking for a time limited export
            options.dateFrom = null;
        }

        FragmentManager fm = getSupportFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, ExportOptions>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_backing_up, false, 0);
            BackupTask task = new BackupTask(mProgressDialog, options);
            mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            task.execute();
        }
        mProgressDialog.setTaskListener(mTaskListener);
//        mProgressDialog.setOnUserCancelledListener(this);
    }
}
