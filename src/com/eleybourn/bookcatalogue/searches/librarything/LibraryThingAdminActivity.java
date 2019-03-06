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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * This is the Administration page. It contains details about LibraryThing links
 * and how to register for a developer key. At a later data we could also include
 * the user key for maintaining user-specific LibraryThing data.
 *
 * @author Philip Warner
 */
public class LibraryThingAdminActivity
        extends BaseActivity
        implements ProgressDialogFragment.OnTaskFinishedListener {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_librarything;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.library_thing);

        // LT Registration Link.
        findViewById(R.id.register_url).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                                           Uri.parse(LibraryThingManager.getBaseURL() + '/'));
                startActivity(intent);
            }
        });

        // DevKey Link.
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
                Prefs.getPrefs()
                     .edit()
                     .putString(LibraryThingManager.PREFS_DEV_KEY, devKey)
                     .apply();

                if (!devKey.isEmpty()) {
                    //noinspection unchecked
                    ProgressDialogFragment<Integer> frag = (ProgressDialogFragment)
                            getSupportFragmentManager().findFragmentByTag(ValidateKey.TAG);
                    if (frag == null) {
                        frag = ProgressDialogFragment.newInstance(
                                R.string.progress_msg_connecting_to_web_site, true, 0);
                        frag.show(getSupportFragmentManager(), ValidateKey.TAG);
                    }
                    new ValidateKey(frag).execute();
                }
            }
        });

        findViewById(R.id.reset_messages).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                SharedPreferences prefs = Prefs.getPrefs();
                SharedPreferences.Editor ed = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    if (key.toLowerCase()
                           .startsWith(LibraryThingManager.PREFS_HIDE_ALERT.toLowerCase())) {
                        ed.remove(key);
                    }
                }
                ed.apply();
            }
        });

        // hide soft keyboard at first
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               final Object result) {
        UserMessage.showUserMessage(this, (Integer) result);
    }

    /**
     * Request a known valid ISBN from LT to see if the user key is valid.
     */
    private static class ValidateKey
            extends AsyncTask<Void, Object, Integer> {

        private static final String TAG = ValidateKey.class.getSimpleName();

        /** Generic identifier. */
        private static final int M_TASK_ID = R.id.TASK_ID_LT_VALIDATE_KEY;
        protected final ProgressDialogFragment<Integer> mFragment;
        /**
         * {@link #doInBackground} should catch exceptions, and set this field.
         * {@link #onPostExecute} can then check it.
         */
        @Nullable
        protected Exception mException;

        /**
         * Constructor.
         *
         * @param frag fragment to use for progress updates.
         */
        @UiThread
        ValidateKey(@NonNull final ProgressDialogFragment<Integer> frag) {
            mFragment = frag;
            mFragment.setTask(M_TASK_ID, this);
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            LibraryThingManager ltm = new LibraryThingManager();
            File tmpFile = ltm.getCoverImage("0451451783", SearchSites.ImageSizes.SMALL);
            if (tmpFile != null) {
                tmpFile.deleteOnExit();
                long length = tmpFile.length();
                StorageUtils.deleteFile(tmpFile);

                if (length < 100) {
                    return R.string.lt_incorrect_key;
                } else {
                    // all ok
                    return R.string.lt_correct_key;
                }
            }
            if (isCancelled()) {
                // return value not used as onPostExecute is not called
                return R.string.progress_end_cancelled;
            }
            return R.string.warning_cover_not_found;
        }

        /**
         * If the task was cancelled (by the user cancelling the progress dialog) then
         * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
         *
         * @param result of the task
         */
        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Integer result) {
            mFragment.taskFinished(M_TASK_ID, mException == null, result);
        }
    }
}
