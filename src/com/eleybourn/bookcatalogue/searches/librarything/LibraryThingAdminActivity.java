/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.searches.librarything;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

/**
 * This is the Administration page. It contains details about LibraryThing links
 * and how to register for a developer key. At a later data we could also include
 * the user key for maintaining user-specific LibraryThing data.
 *
 * @author Philip Warner
 */
public class LibraryThingAdminActivity
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_librarything;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.library_thing);

        /* LT Registration Link. */
        findViewById(R.id.register_url).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                                           Uri.parse(LibraryThingManager.getBaseURL() + '/'));
                startActivity(intent);
            }
        });

        /* DevKey Link. */
        findViewById(R.id.dev_key_url).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        LibraryThingManager.getBaseURL() + "/services/keys.php"));
                startActivity(intent);
            }
        });

        EditText devKeyView = findViewById(R.id.dev_key);
        String key = Prefs.getPrefs().getString(LibraryThingManager.PREFS_DEV_KEY, "");
        devKeyView.setText(key);

        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                EditText devKeyView = findViewById(R.id.dev_key);
                String devKey = devKeyView.getText().toString().trim();
                Prefs.getPrefs().edit()
                     .putString(LibraryThingManager.PREFS_DEV_KEY, devKey)
                     .apply();

                if (!devKey.isEmpty()) {
                    FragmentTask task =
                            new TaskWithProgressDialogFragment.FragmentTaskAbstract() {
                        /**
                         * Validate the key by getting a known cover.
                         */
                        @Override
                        public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                                        @NonNull final SimpleTaskContext taskContext) {
                            LibraryThingManager ltm = new LibraryThingManager();
                            File tmpFile = ltm.getCoverImage("0451451783",
                                                             LibraryThingManager.ImageSizes.SMALL);
                            if (tmpFile != null) {
                                tmpFile.deleteOnExit();
                                long length = tmpFile.length();
                                if (length < 100) {
                                    // Queue a message
                                    fragment.showUserMessage(getString(R.string.lt_incorrect_key));
                                } else {
                                    // Queue a message
                                    fragment.showUserMessage(getString(R.string.lt_correct_key));
                                }
                                StorageUtils.deleteFile(tmpFile);
                            }
                        }

                        @Override
                        public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                                             @Nullable final Exception e) {
                        }
                    };

                    // Get the fragment to display task progress
                    TaskWithProgressDialogFragment.newInstance(
                            LibraryThingAdminActivity.this,
                            R.string.progress_msg_connecting_to_web_site, task, true, 0);

                }
            }
        });

        findViewById(R.id.reset_messages).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                SharedPreferences prefs = Prefs.getPrefs();
                SharedPreferences.Editor ed = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    if (key.toLowerCase().startsWith(
                            LibraryThingManager.PREFS_HIDE_ALERT.toLowerCase())) {
                        ed.remove(key);
                    }
                }
                ed.apply();
            }
        });

        // hide soft keyboard at first
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        Tracker.exitOnCreate(this);
    }
}
