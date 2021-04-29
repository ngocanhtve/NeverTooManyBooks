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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityLibrarythingRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;

/**
 * Contains details about LibraryThing links and how to register for a developer key.
 * At a later data we could also include the user key for maintaining user-specific data.
 */
public class LibraryThingRegistrationActivity
        extends BaseActivity {

    /** View Binding. */
    private ActivityLibrarythingRegisterBinding mVb;

    private LibraryThingRegistrationViewModel mVm;

    @Override
    protected void onSetContentView() {
        mVb = ActivityLibrarythingRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(LibraryThingRegistrationViewModel.class);
        mVm.onFailure().observe(this, message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), getString(R.string.error_network_site_access_failed,
                                                       getString(R.string.site_library_thing)),
                              Snackbar.LENGTH_LONG).show();
            }
        });
        mVm.onCancelled().observe(this, message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mVm.onFinished().observe(this, message -> {
            if (message.isNewEvent()) {
                final Integer result = message.getResult();
                final String msg = result != null
                                   ? getString(result)
                                   : getString(R.string.error_network_site_access_failed,
                                               getString(R.string.site_library_thing));
                Snackbar.make(mVb.getRoot(), msg, Snackbar.LENGTH_LONG).show();
            }
        });

        // for the purist: we should call SearchEngine#getSiteUrl()
        // but it's extremely unlikely that LibraryThing would ever get a configurable url
        final String siteUrl = SearchEngineRegistry.getInstance()
                                                   .getByEngineId(SearchSites.LIBRARY_THING)
                                                   .getSiteUrl();

        mVb.registerUrl.setOnClickListener(v -> {
            final Uri uri = Uri.parse(siteUrl + '/');
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        mVb.devKeyUrl.setOnClickListener(v -> {
            final Uri uri = Uri.parse(siteUrl + "/services/keys.php");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        final String key = PreferenceManager.getDefaultSharedPreferences(this)
                                            .getString(LibraryThingSearchEngine.PK_DEV_KEY, "");
        mVb.devKey.setText(key);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_action_validate, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ACTION_CONFIRM) {
            validateKey();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void validateKey() {
        //noinspection ConstantConditions
        final String devKey = mVb.devKey.getText().toString().trim();
        PreferenceManager.getDefaultSharedPreferences(this)
                         .edit()
                         .putString(LibraryThingSearchEngine.PK_DEV_KEY, devKey)
                         .apply();

        if (devKey.isEmpty()) {
            showError(mVb.lblDevKey, getString(R.string.vldt_non_blank_required));

        } else {
            Snackbar.make(mVb.devKey, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mVm.validateKey();
        }
    }
}
