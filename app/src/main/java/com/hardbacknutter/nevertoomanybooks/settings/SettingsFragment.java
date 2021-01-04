/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesAllListsContract;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Global settings page.
 */
public class SettingsFragment
        extends BasePreferenceFragment {

    /** Fragment manager tag. */
    public static final String TAG = "SettingsFragment";
    /** savedInstanceState key. */
    private static final String SIS_CURRENT_SORT_TITLE_REORDERED = TAG + ":cSTR";
    private final ActivityResultLauncher<Void> mEditSitesLauncher =
            registerForActivityResult(new SearchSitesAllListsContract(),
                                      success -> { /* ignore */ });
    /** Used to be able to reset this pref to what it was when this fragment started. */
    private boolean mCurrentSortTitleReordered;
    /** The Activity results. */
    private SettingsViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                    getActivity().finish();
                }
            };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SettingsViewModel.class);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        final boolean storedSortTitleReordered = prefs
                .getBoolean(Prefs.pk_sort_title_reordered, true);

        if (savedInstanceState == null) {
            mCurrentSortTitleReordered = storedSortTitleReordered;
        } else {
            mCurrentSortTitleReordered = savedInstanceState
                    .getBoolean(SIS_CURRENT_SORT_TITLE_REORDERED, storedSortTitleReordered);
        }

        setVisualIndicator(findPreference(Prefs.pk_sort_title_reordered),
                           StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);

        Preference preference;

        preference = findPreference("psk_search_site_order");
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                mEditSitesLauncher.launch(null);
                return true;
            });
        }

        preference = findPreference(Prefs.pk_sort_title_reordered);
        if (preference != null) {
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                final SwitchPreference p = (SwitchPreference) pref;
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setMessage(R.string.confirm_rebuild_orderby_columns)
                        // this dialog is important. Make sure the user pays some attention
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> {
                            p.setChecked(mCurrentSortTitleReordered);
                            StartupViewModel.scheduleOrderByRebuild(getContext(), false);
                            setVisualIndicator(p, StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);
                        })
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            p.setChecked(!p.isChecked());
                            StartupViewModel.scheduleOrderByRebuild(getContext(), true);
                            setVisualIndicator(p, StartupViewModel.PK_REBUILD_ORDERBY_COLUMNS);
                        })
                        .create()
                        .show();
                // Do not let the system update the preference value.
                return false;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setTitle(R.string.lbl_settings);
        actionBar.setSubtitle("");
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_CURRENT_SORT_TITLE_REORDERED, mCurrentSortTitleReordered);
    }

    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        switch (key) {
            case Prefs.pk_ui_locale:
            case Prefs.pk_ui_theme:
            case Prefs.pk_sort_title_reordered:
            case Prefs.pk_show_title_reordered:
                mVm.setRequiresActivityRecreation();
                break;

            default:
                break;
        }

        super.onSharedPreferenceChanged(preferences, key);
    }

    /**
     * Change the icon color depending on the preference being scheduled for change on restart.
     * <p>
     * TODO: this is not ideal as it does not explain to the user WHY the color is changed
     * Check if its's possible to overlay the icon with another icon (showing e.g. a clock)
     *
     * @param preference   to modify
     * @param schedulerKey to reflect
     */
    private void setVisualIndicator(@Nullable final Preference preference,
                                    @SuppressWarnings("SameParameterValue")
                                    @NonNull final String schedulerKey) {
        if (preference != null) {
            @AttrRes
            final int attr;
            if (getPreferenceManager().getSharedPreferences().getBoolean(schedulerKey, false)) {
                attr = R.attr.appPreferenceAlertColor;
            } else {
                attr = R.attr.colorControlNormal;
            }

            final Drawable icon = preference.getIcon().mutate();
            //noinspection ConstantConditions
            icon.setTint(AttrUtils.getColorInt(getContext(), attr));
            preference.setIcon(icon);
        }
    }

}
