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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookFromBundleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public abstract class SearchBookBaseFragment
        extends BaseFragment {

    private final ActivityResultLauncher<Bundle> editBookFoundLauncher = registerForActivityResult(
            new EditBookFromBundleContract(), this::onBookEditingDone);
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Bundle output = getResultData();
                    final Intent resultIntent = new Intent().putExtras(output);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    SearchCoordinator coordinator;

    private final ActivityResultLauncher<ArrayList<Site>> editSitesLauncher =
            registerForActivityResult(new SearchSitesSingleListContract(),
                                      sites -> {
                                          if (sites != null) {
                                              coordinator.setSiteList(sites);
                                          }
                                          explainSitesSupport(sites);
                                      });
    @Nullable
    private ProgressDelegate progressDelegate;

    protected void explainSitesSupport(@Nullable final ArrayList<Site> sites) {
        // override as needed, e.g. SearchBookByTextFragment
    }

    @NonNull
    protected abstract Bundle getResultData();

    @CallSuper
    void onBookEditingDone(@Nullable final EditBookOutput data) {
        if (data != null) {
            EditBookOutput.toBundle(data, getResultData());
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        coordinator = new ViewModelProvider(getActivity()).get(SearchCoordinator.class);
        //noinspection ConstantConditions
        coordinator.init(getContext(), requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        coordinator.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        // Handle both Success and Failed searches
        coordinator.onSearchFinished().observe(getViewLifecycleOwner(), this::onSearchFinished);
        coordinator.onSearchCancelled().observe(getViewLifecycleOwner(), message -> {
            closeProgressDialog();
            onSearchCancelled();
        });

        // Warn the user, but don't abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(view, R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onSearchFinished(@NonNull final LiveDataEvent<TaskResult<Bundle>> message) {
        closeProgressDialog();
        message.getData().map(TaskResult::requireResult).ifPresent(result -> {
            final String searchErrors = result.getString(SearchCoordinator.BKEY_SEARCH_ERROR);
            result.remove(SearchCoordinator.BKEY_SEARCH_ERROR);
            final boolean hasBookData = !result.isEmpty();

            if (searchErrors != null) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(hasBookData ? R.string.warning_book_not_always_found
                                              : R.string.warning_book_not_found)
                        .setMessage(searchErrors)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            d.dismiss();
                            if (hasBookData) {
                                onSearchResults(result);
                            }
                        })
                        .create()
                        .show();

            } else if (hasBookData) {
                onSearchResults(result);

            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_no_matching_book_found,
                              Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @CallSuper
    void onSearchCancelled() {
        //noinspection ConstantConditions
        Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection ConstantConditions
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.progress_msg_searching)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> coordinator.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection ConstantConditions
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }


    /**
     * Clear the SearchCoordinator search criteria.
     * <p>
     * Override to clear the input fields for the former.
     * Make sure to always call the super.
     */
    @CallSuper
    void onClearSearchCriteria() {
        coordinator.clearSearchCriteria();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * This is final; override {@link #onPreSearch()} and {@link #onSearch()} as needed.
     * The results come in {@link #onSearchResults(Bundle)}.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (coordinator.isSearchActive()) {
            return;
        }

        // any implementation specific reasons not to start searching ?
        if (!onPreSearch()) {
            return;
        }

        // Warn the user, AND abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // Start the lookup in a background search task.
        if (!onSearch()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_search_could_not_be_started,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Override to prevent or allow a search to start.
     * The default implementation allows a search to start.
     *
     * @return {@code true} if a search is allowed
     */
    boolean onPreSearch() {
        return true;
    }

    /**
     * Override to customize which search function is called.
     * The default implementation starts the generic {@link SearchCoordinator#search()}.
     *
     * @return {@code true} if a search was started
     */
    boolean onSearch() {
        return coordinator.search();
    }

    /**
     * Process the search results.
     * The default implementation starts the book-edit activity.
     *
     * @param bookData Bundle with the results
     */
    void onSearchResults(@NonNull final Bundle bookData) {
        editBookFoundLauncher.launch(bookData);
        onClearSearchCriteria();
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @StringRes final int error) {
        til.setError(getString(error));
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
    }

    class SearchSitesToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            final Resources r = getResources();
            menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                     r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                     R.string.lbl_websites)
                .setIcon(R.drawable.ic_baseline_find_in_page_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_PREFS_SEARCH_SITES) {
                editSitesLauncher.launch(coordinator.getSiteList());
                return true;
            }
            return false;
        }
    }

}
