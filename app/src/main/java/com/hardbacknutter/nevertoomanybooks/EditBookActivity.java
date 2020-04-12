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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The hosting activity for editing a book.
 */
public class EditBookActivity
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "EditBookActivity";

    public static boolean showTabNativeId(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_edit_book_tabs_native_id, false);
    }

    public static boolean showAuthSeriesOnTabs(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_edit_book_tabs_authSer, false);
    }

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_edit_book);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);

        replaceFragment(R.id.main_fragment, EditBookFragment.class, EditBookFragment.TAG);
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        // Settings initiated from the navigation panel.
        if (requestCode == RequestCode.NAV_PANEL_SETTINGS) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // update the search sites list.
                // SiteList siteList = data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
                // if (siteList != null) {
                //     SearchCoordinator model =
                //             new ViewModelProvider(this).get(SearchCoordinator.class);
                //     model.setSiteList(siteList);
                // }

                // Reset the scanner if it was changed.
                // Note this creates the scanner model even if it did not exist before.
                // Other then using memory, this is fine.
                // We assume if the user explicitly went to settings to change the scanner
                // they want to use it.
                if (data.getBooleanExtra(BarcodePreferenceFragment.BKEY_SCANNER_MODIFIED, false)) {
                    final ScannerViewModel model =
                            new ViewModelProvider(this).get(ScannerViewModel.class);
                    model.resetScanner();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {

        final BookViewModel model = new ViewModelProvider(this).get(BookViewModel.class);

        final FragmentManager fm = getSupportFragmentManager();
        final int backStackEntryCount = fm.getBackStackEntryCount();

        // 1. Check for the current (i.e. in resumed state) fragment having unfinished edits
        if (backStackEntryCount > 0) {
            final String tag = fm.getBackStackEntryAt(backStackEntryCount - 1).getName();
            final Fragment frag = fm.findFragmentByTag(tag);
            if (frag instanceof DataEditor && frag.isResumed()) {
                //noinspection unchecked
                final DataEditor<Book> dataEditor = (DataEditor<Book>) frag;
                if (dataEditor.hasUnfinishedEdits()) {
                    StandardDialogs.unsavedEdits(this, null, super::onBackPressed);
                    return;
                }
            }
        }

        // 2. If we're at the top level, check if the book was changed.
        if (backStackEntryCount == 0 && model.isDirty()) {
            StandardDialogs.unsavedEdits(this, null,
                                         () -> cleanupAndSetResults(model, true));
            return;
        }

        // Once here, we have no unfinished edits; and if we're on the top level,
        // the book data was saved (or never changed)
        if (backStackEntryCount == 0) {
            cleanupAndSetResults(model, false);
        }

        super.onBackPressed();
    }

    void cleanupAndSetResults(@NonNull final ActivityResultDataModel model,
                              final boolean doFinish) {
        // we're really leaving, clean up
        CoverHandler.deleteOrphanedCoverFiles(this);
        // The result data will contain the re-position book id.
        setResult(Activity.RESULT_OK, model.getResultData());
        if (doFinish) {
            finish();
        }
    }
}
