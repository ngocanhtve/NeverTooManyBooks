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
package com.hardbacknutter.nevertoomanybooks.settings.sites;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

public class GoodreadsPreferencesFragment
        extends BasePreferenceFragment {

    public static final String TAG = "GoodreadsPrefsFrag";

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        setPreferencesFromResource(R.xml.preferences_site_goodreads, rootKey);
    }

    /**
     * Hook up specific listeners/preferences.
     */
    @Override
    public void onStart() {
        super.onStart();

        //noinspection ConstantConditions
        findPreference("pa_credentials")
                .setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(getContext(), GoodreadsRegistrationActivity.class));
                    return true;
                });
    }
}
