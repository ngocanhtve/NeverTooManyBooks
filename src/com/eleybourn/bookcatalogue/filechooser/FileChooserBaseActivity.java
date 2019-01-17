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

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.OnPathChangedListener;
import com.eleybourn.bookcatalogue.filechooser.FileListerFragmentTask.FileListerListener;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;

import java.io.File;
import java.util.ArrayList;

/**
 * Base class for an Activity to perform file browsing functions consistent with
 * an Open/Save-As chooser.
 *
 * @author pjw
 */
public abstract class FileChooserBaseActivity
        extends BaseActivity
        implements
        TaskWithProgressDialogFragment.OnTaskFinishedListener,
        TaskWithProgressDialogFragment.OnAllTasksFinishedListener,
        FileListerFragmentTask.FileListerListener,
        OnPathChangedListener {

    /** Key for member of EXTRAS that specifies the mode of operation of this dialog. */
    public static final String BKEY_MODE = "mode";
    public static final String BVAL_MODE_SAVE = "saveAs";
    public static final String BVAL_MODE_OPEN = "open";

    /** Flag indicating nature of this activity. */
    private boolean mIsSave;

    public boolean isSave() {
        return mIsSave;
    }

    /**
     * Create the fragment we display.
     */
    @NonNull
    protected abstract FileChooserFragment getChooserFragment();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_file_chooser_base;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // Determine the dialog type
        Bundle extras = getIntent().getExtras();
        mIsSave = extras != null && BVAL_MODE_SAVE.equals(extras.getString(BKEY_MODE));

        // Get and display the fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (findViewById(R.id.browser_fragment) != null
                && fragmentManager.findFragmentById(R.id.browser_fragment) == null) {
            // Create the browser
            FileChooserFragment frag = getChooserFragment();
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.browser_fragment, frag, FileChooserFragment.TAG)
                    .commit();
        }

        // Handle 'Cancel' button
        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        // Handle Open/Save button
        Button confirm = findViewById(R.id.confirm);

        if (mIsSave) {
            confirm.setText(R.string.btn_confirm_save);
            confirm.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    handleSave();
                }
            });

        } else {
            confirm.setText(R.string.btn_confirm_open);
            confirm.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    handleOpen();
                }
            });
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Implemented by subclass to handle a click on the 'Open' button.
     *
     * @param file Selected file
     */
    protected abstract void onOpen(@NonNull File file);

    /**
     * Implemented by subclass to handle a click on the 'Save' button.
     *
     * @param file Selected file
     */
    protected abstract void onSave(@NonNull File file);

    /**
     * Local handler for 'Open'. Perform basic validation, and pass on.
     */
    private void handleOpen() {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
        if (frag instanceof FileChooserFragment) {
            FileChooserFragment bf = (FileChooserFragment) frag;
            File file = bf.getSelectedFile();
            if (!file.exists() || !file.isFile()) {
                StandardDialogs.showUserMessage(this, R.string.warning_select_an_existing_file);
                return;
            }
            onOpen(file);
        }
    }

    /**
     * Local handler for 'Save'. Perform basic validation, and pass on.
     */
    private void handleSave() {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
        if (frag instanceof FileChooserFragment) {
            FileChooserFragment bf = (FileChooserFragment) frag;
            File file = bf.getSelectedFile();
            if (file.exists() && !file.isFile()) {
                StandardDialogs.showUserMessage(this, R.string.warning_select_a_non_directory);
                return;
            }
            onSave(file);
        }
    }

    /**
     * Called by lister fragment to pass on the list of files.
     */
    @Override
    public void onGotFileList(@NonNull final File root,
                              @NonNull final ArrayList<FileDetails> list) {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
        if (frag instanceof FileListerListener) {
            ((FileListerListener) frag).onGotFileList(root, list);
        }
    }

    /**
     * @return an object for building an list of files in background.
     */
    @NonNull
    protected abstract FileListerFragmentTask getFileLister(@NonNull final File root);

    /**
     * Rebuild the file list in background; gather whatever data is necessary to
     * ensure fast building of views in the UI thread.
     */
    @Override
    public void onPathChanged(@Nullable final File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }

        // Create the background task
        FileListerFragmentTask lister = getFileLister(root);

        // Start the task
        TaskWithProgressDialogFragment
                .newInstance(this, R.string.progress_msg_searching_directory, lister, true, 0);

    }

    /**
     * Empty implementation. Override if you need to.
     */
    @Override
    public void onAllTasksFinished(@NonNull final TaskWithProgressDialogFragment fragment,
                                   final int taskId,
                                   final boolean success,
                                   final boolean cancelled) {
    }
}
