/*
 * @Copyright 2019 HardBackNutter
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
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

public abstract class BookSearchBaseFragment
        extends Fragment {

    private static final String TAG = "BookSearchBaseFrag";

    /** hosting activity. */
    FragmentActivity mHostActivity;

    DAO mDb;

    /** the ViewModel. */
    ResultDataModel mResultDataModel;
    SearchCoordinator mSearchCoordinator;

    @Nullable
    private ProgressDialogFragment mProgressDialog;

    @NonNull
    private final SearchCoordinator.SearchCoordinatorListener mSearchCoordinatorListener =
            new SearchCoordinator.SearchCoordinatorListener() {
                @Override
                public void onFinished(@NonNull final Bundle bookData,
                                       @Nullable final String searchErrors) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }

                    if (searchErrors != null) {
                        //noinspection ConstantConditions
                        new AlertDialog.Builder(getContext())
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .setTitle(R.string.title_search_failed)
                                .setMessage(searchErrors)
                                .create()
                                .show();

                    } else if (!bookData.isEmpty()) {
                        onSearchResults(bookData);

                    } else {
                        //noinspection ConstantConditions
                        UserMessage.show(getView(), R.string.warning_no_matching_book_found);
                    }
                }

                @Override
                public void onCancelled() {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                    BookSearchBaseFragment.this.onCancelled();
                }

                @Override
                public void onProgress(@NonNull final TaskListener.ProgressMessage message) {
                    if (mProgressDialog != null) {
                        mProgressDialog.onProgress(message);
                    }
                }
            };

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mHostActivity = (FragmentActivity) context;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchCoordinator = new ViewModelProvider(this).get(SearchCoordinator.class);
        mSearchCoordinator.init(requireArguments(), mSearchCoordinatorListener);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            // reconnect after a fragment restart
            mProgressDialog.setCancellable(mSearchCoordinator);
        }

        // Warn the user, but don't abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
        }
    }

//    @Override
//    @CallSuper
//    public void onResume() {
//        super.onResume();
//        if (getActivity() instanceof BaseActivity) {
//            BaseActivity activity = (BaseActivity) getActivity();
//            if (activity.isGoingToRecreate()) {
//                return;
//            }
//        }
//    }

    /**
     * <strong>Child classes</strong> must call {@code setHasOptionsMenu(true)}
     * from their {@link #onCreate} to enable the option menu.
     * <br><br>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                 R.string.lbl_websites)
            .setIcon(R.drawable.ic_find_in_page)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_TABS_TO_SHOW, SearchAdminModel.TAB_BOOKS)
                        .putExtra(SearchSites.BKEY_DATA_SITES, mSearchCoordinator.getSearchSites());
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    abstract void onSearchResults(@NonNull Bundle bookData);

    @CallSuper
    void onCancelled() {
        //noinspection ConstantConditions
        UserMessage.show(getView(), R.string.progress_end_cancelled);
    }

    @CallSuper
    void clearPreviousSearchCriteria() {
        mSearchCoordinator.clearSearchText();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * This is final; instead override {@link #onSearch()} if needed.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (mSearchCoordinator.isSearchActive()) {
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
            return;
        }

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment
                    .newInstance(R.string.progress_msg_searching, true, 0);
            mProgressDialog.show(fm, TAG);
            // Start the lookup in a background search task.
            if (onSearch()) {
                // we started at least one search.
                mProgressDialog.setCancellable(mSearchCoordinator);
            } else {
                mProgressDialog.dismiss();
                mProgressDialog = null;
                //TODO: not the best error message, but it will do for now.
                //noinspection ConstantConditions
                UserMessage.show(getView(), R.string.error_search_failed_network);
            }
            return;
        }

        Intent resultData = mResultDataModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            mHostActivity.setResult(Activity.RESULT_OK, resultData);
        }
        mHostActivity.finish();
    }

    /**
     * Override to customize which search function is called.
     */
//    protected boolean onSearch() {
//        return mSearchCoordinator.searchByText();
//    }
    protected abstract boolean onSearch();

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Site> sites = data.getParcelableArrayListExtra(
                            SearchSites.BKEY_DATA_SITES);
                    if (sites != null) {
                        mSearchCoordinator.setSearchSites(sites);
                    }
                    // Make sure that the ASIN option (Amazon) is (not) offered.
                    //noinspection ConstantConditions
                    getActivity().invalidateOptionsMenu();
                }
                break;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book? Save the intent
                    if (data != null) {
                        mResultDataModel.putExtras(data);
                    }
                }
                break;
            }
//            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
//                mSearchCoordinator.setSearchSites(sites);
//            }

            default: {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "onActivityResult|NOT HANDLED"
                               + "|requestCode=" + requestCode
                               + "|resultCode=" + resultCode, new Throwable());
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
