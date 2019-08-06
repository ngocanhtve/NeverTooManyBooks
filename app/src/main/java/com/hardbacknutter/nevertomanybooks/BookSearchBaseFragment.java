/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import java.util.Objects;

import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.debug.Tracker;
import com.hardbacknutter.nevertomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertomanybooks.viewmodels.BookSearchBaseModel;

/**
 * Optionally limit the sites to search on by setting {@link UniqueId#BKEY_SEARCH_SITES}.
 * By default uses {@link SearchSites#SEARCH_ALL}.
 */
public abstract class BookSearchBaseFragment
        extends Fragment {

    /** Fragment manager tag. */
    private static final String TAG = "BookSearchBaseFragment";

    /** stores an active search id, or 0 when none active. */
    public static final String BKEY_SEARCH_COORDINATOR_ID = TAG + ":SearchCoordinatorId";
    /** the last book data (intent) we got from a successful EditBook. */
    private static final String BKEY_LAST_BOOK_INTENT = TAG + ":LastBookIntent";

    /** hosting activity. */
    AppCompatActivity mActivity;
    TaskManager mTaskManager;

    /** the ViewModel. */
    BookSearchBaseModel mBookSearchBaseModel;

    abstract SearchCoordinator.SearchFinishedListener getSearchFinishedListener();

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * The results will arrive in
     * {@link SearchCoordinator.SearchFinishedListener#onSearchFinished(boolean, Bundle)}
     *
     * @return {@code true} if search was started.
     */
    boolean startSearch() {

        // check if we have an active search, if so, quit silently.
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            return false;
        }

        //sanity check
        if (!mBookSearchBaseModel.hasSearchData()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.warning_required_at_least_one);
            return false;
        }
        // Don't start search if we have no approved network... FAIL.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
            return false;
        }

        try {
            // Start the lookup in a background search task.
            final SearchCoordinator searchCoordinator =
                    new SearchCoordinator(mTaskManager, getSearchFinishedListener());
            mBookSearchBaseModel.setSearchCoordinator(searchCoordinator.getId());

            mTaskManager.sendHeaderUpdate(R.string.progress_msg_searching);
            // kick of the searches
            searchCoordinator.search(mBookSearchBaseModel.getSearchSites(),
                                     mBookSearchBaseModel.getIsbnSearchText(),
                                     mBookSearchBaseModel.getAuthorSearchText(),
                                     mBookSearchBaseModel.getTitleSearchText(),
                                     mBookSearchBaseModel.getPublisherSearchText(),
                                     true);

            // reset the details so we don't restart the search unnecessarily
            mBookSearchBaseModel.clearSearchText();

            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_search_failed);

        }
        mActivity.setResult(Activity.RESULT_CANCELED);
        mActivity.finish();
        return false;
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mBookSearchBaseModel.setSearchSites(
                            data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                             mBookSearchBaseModel.getSearchSites()));
                }
                break;

            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book; save the intent
                    mBookSearchBaseModel.setLastBookData(data);
                    // and set it as the default result
                    mActivity.setResult(resultCode, mBookSearchBaseModel.getLastBookData());

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // if the edit was cancelled, set that as the default result code
                    mActivity.setResult(Activity.RESULT_CANCELED);
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.warnWithStackTrace(this, "BookSearchBaseFragment.onActivityResult",
                                              "NOT HANDLED:",
                                              "requestCode=" + requestCode,
                                              "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
        mTaskManager = ((BookSearchActivity) mActivity).getTaskManager();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBookSearchBaseModel = ViewModelProviders.of(this).get(BookSearchBaseModel.class);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mBookSearchBaseModel.init(args);

        if ((mBookSearchBaseModel.getSearchSites() & SearchSites.GOODREADS) != 0) {
            //noinspection ConstantConditions
            GoodreadsManager.alertRegistrationBeneficial(getContext(), false, "search");
        }

        if ((mBookSearchBaseModel.getSearchSites() & SearchSites.LIBRARY_THING) != 0) {
            //noinspection ConstantConditions
            LibraryThingManager.alertRegistrationBeneficial(getContext(), false, "search");
        }

        // Check general network connectivity. If none, WARN the user.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
        }
    }

    /**
     * (re)connect with the {@link SearchCoordinator} by starting to listen to its messages.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            SearchCoordinator.MESSAGE_SWITCH
                    .addListener(mBookSearchBaseModel.getSearchCoordinatorId(), true,
                                 getSearchFinishedListener());
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BKEY_SEARCH_COORDINATOR_ID, mBookSearchBaseModel.getSearchCoordinatorId());
        outState.putParcelable(BKEY_LAST_BOOK_INTENT, mBookSearchBaseModel.getLastBookData());
    }

    /**
     * Cut us loose from the {@link SearchCoordinator} by stopping listening to its messages.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            SearchCoordinator.MESSAGE_SWITCH.removeListener(
                    mBookSearchBaseModel.getSearchCoordinatorId(),
                    getSearchFinishedListener());
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD,
                 MenuHandler.ORDER_HIDE_KEYBOARD, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 MenuHandler.ORDER_SEARCH_SITES, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_HIDE_KEYBOARD:
                //noinspection ConstantConditions
                App.hideKeyboard(getView());
                return true;

            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
