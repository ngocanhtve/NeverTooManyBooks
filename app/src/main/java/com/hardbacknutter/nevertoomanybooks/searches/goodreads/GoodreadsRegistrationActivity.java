/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Activity to allow the user to authorize the application to access their
 * Goodreads account and to explain Goodreads.
 */
public class GoodreadsRegistrationActivity
        extends BaseActivity {

    private View mAuthButton;

    private final TaskListener<Integer> mTaskListener = new TaskListener<Integer>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Integer> message) {
            final Context context = GoodreadsRegistrationActivity.this;
            if (GoodreadsHandler.authNeeded(message)) {
                RequestAuthTask.prompt(context, mTaskListener);
            } else {
                Snackbar.make(mAuthButton, GoodreadsHandler.digest(context, message),
                              Snackbar.LENGTH_LONG).show();
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            if (message.text != null) {
                Snackbar.make(mAuthButton, message.text, Snackbar.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_goodreads_register);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.site_goodreads);

        // Goodreads Reg Link
        TextView register = findViewById(R.id.goodreads_url);
        register.setText(GoodreadsHandler.BASE_URL);
        register.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GoodreadsHandler.BASE_URL))));

        // Auth button
        mAuthButton = findViewById(R.id.authorize);
        mAuthButton.setOnClickListener(v -> {
            Snackbar.make(mAuthButton, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            new RequestAuthTask(mTaskListener).execute();
        });

        // Forget credentials
        View blurb = findViewById(R.id.forget_blurb);
        View removeButton = findViewById(R.id.btn_delete_credentials);
        GoodreadsAuth auth = new GoodreadsAuth(this);
        if (auth.hasCredentials(this)) {
            blurb.setVisibility(View.VISIBLE);
            removeButton.setVisibility(View.VISIBLE);
            removeButton.setOnClickListener(v -> GoodreadsAuth.clearAll(this));
        } else {
            blurb.setVisibility(View.GONE);
            removeButton.setVisibility(View.GONE);
        }
    }
}
