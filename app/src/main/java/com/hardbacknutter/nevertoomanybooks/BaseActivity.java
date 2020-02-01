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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Base class for all Activity's (except the startup activity).
 * <p>
 * Fragments should implement:
 * <pre>
 *     {@code
 *          @Override
 *          @CallSuper
 *          public void onResume() {
 *              super.onResume();
 *              if (getActivity() instanceof BaseActivity) {
 *                  BaseActivity activity = (BaseActivity) getActivity();
 *                  if (activity.isGoingToRecreate()) {
 *                      return;
 *                  }
 *              }
 *
 *              // do stuff here
 *          }
 *     }
 * </pre>
 */
public abstract class BaseActivity
        extends AppCompatActivity {


    /** Log tag. */
    private static final String TAG = "BaseActivity";
    /**
     * Something changed (or not) that warrants a recreation of the caller to be needed.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_RECREATE = TAG + ":recreate";

    /**
     * internal; Stage of Activity  doing/needing setIsRecreating() action.
     * See {@link #onResume()}.
     * <p>
     * Note this is a static!
     */
    private static ActivityStatus sActivityRecreateStatus;

    /** Locale at {@link #onCreate} time. */
    protected String mInitialLocaleSpec;
    /** Theme at {@link #onCreate} time. */
    @App.ThemeId
    protected int mInitialThemeId;

    /** Optional - The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView mNavigationView;

    public void setIsRecreating() {
        sActivityRecreateStatus = ActivityStatus.isRecreating;
    }

    protected boolean isRecreating() {
        boolean isRecreating = sActivityRecreateStatus == ActivityStatus.isRecreating;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Log.d(TAG, "EXIT"
                       + "|isRecreating=" + isRecreating
                       + "|LanguageUtils=" + LanguageUtils.toDebugString(this));
        }
        return isRecreating;
    }

    private void setNeedsRecreating() {
        sActivityRecreateStatus = ActivityStatus.NeedsRecreating;
    }

    /**
     * Override this and return the id you need.
     *
     * @return the layout id for this activity, or 0 for none (i.e. no UI View).
     */
    protected int getLayoutId() {
        return 0;
    }

    /**
     * apply the user-preferred Locale before onCreate is called.
     */
    protected void attachBaseContext(@NonNull final Context base) {
        Context localizedContext = LocaleUtils.applyLocale(base);
        super.attachBaseContext(localizedContext);
        // preserve, so we can check for changes in onResume.
        mInitialLocaleSpec = LocaleUtils.getPersistedLocaleSpec(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        // We preserve it, so we can check for changes in onResume.
        mInitialThemeId = App.applyTheme(this);

        super.onCreate(savedInstanceState);

        int layoutId = getLayoutId();
        if (layoutId != 0) {
            setContentView(layoutId);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        // Normal setup of the action bar now
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            // default on all activities is to show the "up" (back) button
            bar.setDisplayHomeAsUpEnabled(true);
            // but if we are at the top activity
            if (isTaskRoot()) {
                // then we want the hamburger menu.
                bar.setHomeAsUpIndicator(R.drawable.ic_menu);
            }
        }

        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mNavigationView = findViewById(R.id.nav_view);
            mNavigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
            if (BuildConfig.DEBUG /* always */) {
                setNavigationItemVisibility(R.id.SUBMENU_DEBUG, true);
            }
        }
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();

        isGoingToRecreate();
    }

    /**
     * Check if the Locale/Theme was changed, which will trigger the Activity to be recreated.
     *
     * @return {@code true} if a recreate was triggered.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean isGoingToRecreate() {
        boolean localeChanged = LocaleUtils.isChanged(this, mInitialLocaleSpec);
        if (localeChanged) {
            LocaleUtils.onLocaleChanged();
        }

        if (sActivityRecreateStatus == ActivityStatus.NeedsRecreating
            || App.isThemeChanged(mInitialThemeId) || localeChanged) {
            setIsRecreating();
            recreate();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Log.d(TAG, "EXIT|BaseActivity.isGoingToRecreate|Recreate!");
            }

            return true;

        } else {
            // this is the second time we got here, so we have been re-created.
            sActivityRecreateStatus = ActivityStatus.Running;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Log.d(TAG, "EXIT|BaseActivity.isGoingToRecreate|Resuming");
            }
        }

        return false;
    }

    /**
     * Manually load a fragment into the given container using add.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    protected void addFragment(@IdRes final int containerViewId,
                               @NonNull final Class fragmentClass,
                               @Nullable final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, true);
    }

    /**
     * Manually load a fragment into the given container using replace.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    protected void replaceFragment(@IdRes final int containerViewId,
                                   @NonNull final Class fragmentClass,
                                   @Nullable final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, false);
    }

    /**
     * Manually load a fragment into the given container.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
     * <p>
     * TODO: look into {@link androidx.fragment.app.FragmentFactory}
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     * @param isAdd           whether to use add or replace
     */
    private void loadFragment(@IdRes final int containerViewId,
                              @NonNull final Class fragmentClass,
                              @Nullable final String fragmentTag,
                              final boolean isAdd) {
        String tag;
        if (fragmentTag == null) {
            tag = fragmentClass.getName();
        } else {
            tag = fragmentTag;
        }

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag;
            try {
                frag = (Fragment) fragmentClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("not a fragment class: " + fragmentClass.getName());
            }
            frag.setArguments(getIntent().getExtras());
            FragmentTransaction ft = fm.beginTransaction()
                                       .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            if (isAdd) {
                ft.add(containerViewId, frag, tag);
            } else {
                ft.replace(containerViewId, frag, tag);
            }
            ft.commit();
        }
    }

    /**
     * If the drawer is open and the user click the back-button, close the drawer
     * and ignore the back-press.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Set the visibility of a NavigationView menu item.
     *
     * @param itemId  menu item resource id
     * @param visible flag
     */
    protected void setNavigationItemVisibility(@IdRes final int itemId,
                                               final boolean visible) {
        if (mNavigationView != null) {
            mNavigationView.getMenu().findItem(itemId).setVisible(visible);
        }
    }

    @CallSuper
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        switch (item.getItemId()) {
            case R.id.nav_search: {
                boolean advanced = PreferenceManager.getDefaultSharedPreferences(this)
                                                    .getBoolean(Prefs.pk_search_form_advanced,
                                                                false);
                if (advanced) {
                    return onAdvancedSearchRequested();
                } else {
                    // standard system call.
                    return onSearchRequested();
                }
            }

            case R.id.nav_manage_bookshelves: {
                Intent intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES);
                return true;
            }
            case R.id.nav_manage_list_styles: {
                Intent intent = new Intent(this, PreferredStylesActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                return true;
            }
            case R.id.nav_import_export: {
                Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, ImportExportFragment.TAG);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_IMP_EXP);
                return true;
            }
            case R.id.nav_goodreads: {
                Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, GoodreadsAdminFragment.TAG);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_GOODREADS);
                return true;
            }
            case R.id.nav_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                return true;
            }
            case R.id.nav_about: {
                startActivity(new Intent(this, About.class));
                return true;
            }

            case R.id.SUBMENU_DEBUG: {
                onDebugMenu();
                return true;
            }
            default:
                return false;
        }
    }

    protected void closeNavigationDrawer() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * There was a search requested by the user; bring up the advanced form (activity).
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean onAdvancedSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }

    /**
     * TODO:  https://developer.android.com/training/appbar/up-action
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            // Default handler for home icon
            case android.R.id.home:
                // the home icon is only == hamburger menu, at the top level
                if (isTaskRoot()) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                        return true;
                    }
                }
                // otherwise, home is an 'up' event. Simulate the user pressing the 'back' key.
                onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        // generic actions & logging. Anything specific should be done in a child class.
        switch (requestCode) {
            case UniqueId.REQ_NAV_PANEL_SETTINGS:
                if (BuildConfig.DEBUG && (DEBUG_SWITCHES.ON_ACTIVITY_RESULT
                                          || DEBUG_SWITCHES.RECREATE_ACTIVITY)) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_SETTINGS");
                }
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    if (data.getBooleanExtra(BKEY_RECREATE, false)) {
                        setNeedsRecreating();
                    }
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_STYLES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_IMP_EXP:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_IMP_EXP");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_GOODREADS:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_GOODREADS");
                }
                return;

            // logging only
            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    // codes for fragments have upper 16 bits in use, don't log those.
                    // the super call will redirect those.
                    if ((requestCode & 0xFF) != 0) {
                        Logger.warn(this, TAG,
                                    "BaseActivity.onActivityResult"
                                    + "|NOT HANDLED"
                                    + "|requestCode=" + requestCode
                                    + "|resultCode=" + resultCode);
                    }
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    /**
     * DEBUG only.
     * Bring up a debug popup menu.
     */
    private void onDebugMenu() {
        View v = findViewById(R.id.toolbar);
        if (v == null) {
            Toast.makeText(this, "debug popup nok", Toast.LENGTH_SHORT).show();
            return;
        }
        PopupMenu debugMenu = new PopupMenu(this, v);
        Menu menu = debugMenu.getMenu();
        menu.add(Menu.NONE, R.id.MENU_DEBUG_PREFS, 0, R.string.lbl_settings);
        menu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_TEMP_TABLES, 0, "DUMP_TEMP_TABLES");
        menu.add(Menu.NONE, R.id.MENU_DEBUG_PURGE_TBL_BOOK_LIST_NODE_STATE, 0,
                 R.string.lbl_purge_blns);
        debugMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.MENU_DEBUG_PREFS:
                    Prefs.dumpPreferences(BaseActivity.this, null);
                    return true;

                case R.id.MENU_DEBUG_DUMP_TEMP_TABLES:
                    try (DAO db = new DAO(TAG)) {
                        DBHelper.dumpTempTableNames(db.getUnderlyingDatabase());
                    }
                    break;

                case R.id.MENU_DEBUG_PURGE_TBL_BOOK_LIST_NODE_STATE:
                    try (DAO db = new DAO(TAG)) {
                        db.purgeNodeStates();
                    }
                    break;

                default:
                    break;
            }
            return false;
        });

        debugMenu.show();
    }

    private enum ActivityStatus {
        /** Situation normal. */
        Running,
        /** Activity is in need of recreating. */
        NeedsRecreating,
        /** A {@link #recreate()} action has been triggered. */
        isRecreating
    }
}
