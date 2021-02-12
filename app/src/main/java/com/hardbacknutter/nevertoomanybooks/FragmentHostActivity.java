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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ExportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreAdminFragment;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

/**
 * Hosting activity for generic fragments <strong>without</strong>
 * a DrawerLayout/NavigationView side panel.
 */
public class FragmentHostActivity
        extends BaseActivity {

    private static final String TAG = "FragmentHostActivity";
    public static final String BKEY_FRAGMENT_TAG = TAG + ":fragment";

    private static final Map<String, Class<? extends Fragment>> sMap = new HashMap<>();

    static {
        sMap.put(SearchBookByIsbnFragment.TAG, SearchBookByIsbnFragment.class);
        sMap.put(SearchBookByTextFragment.TAG, SearchBookByTextFragment.class);
        sMap.put(SearchBookByExternalIdFragment.TAG, SearchBookByExternalIdFragment.class);
        sMap.put(SearchBookUpdatesFragment.TAG, SearchBookUpdatesFragment.class);

        sMap.put(SearchFtsFragment.TAG, SearchFtsFragment.class);

        sMap.put(AuthorWorksFragment.TAG, AuthorWorksFragment.class);

        sMap.put(ImportFragment.TAG, ImportFragment.class);
        sMap.put(ExportFragment.TAG, ExportFragment.class);

        sMap.put(PreferredStylesFragment.TAG, PreferredStylesFragment.class);

        sMap.put(CalibreAdminFragment.TAG, CalibreAdminFragment.class);

        sMap.put(EditBookshelvesFragment.TAG, EditBookshelvesFragment.class);
        sMap.put(GoodreadsAdminFragment.TAG, GoodreadsAdminFragment.class);
        sMap.put(AboutFragment.TAG, AboutFragment.class);
    }

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String tag = Objects.requireNonNull(
                getIntent().getStringExtra(BKEY_FRAGMENT_TAG), "tag");

        final Class<? extends Fragment> aClass = sMap.get(tag);
        if (aClass != null) {
            addFirstFragment(R.id.main_fragment, aClass, tag);
        } else {
            throw new IllegalArgumentException(tag);
        }
    }

    @Override
    protected void onDestroy() {
        // This is a good time to cleanup the cache.
        // Out of precaution we only trash jpg files
        AppDir.Cache.purge(App.getTaskContext(), true, file -> file.getName().endsWith(".jpg"));
        super.onDestroy();
    }
}
