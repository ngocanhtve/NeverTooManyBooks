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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAdvancedSearchBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * FIXME: open screen, click in field -> keyboard up * now rotate screen... logcat msg
 * https://stackoverflow.com/questions/8122625#15732554
 * <p>
 * Search based on the SQLite FTS engine. Due to the speed of FTS it updates the
 * number of hits more or less in real time. The user can choose to see a full list at any time.
 * ENHANCE: SHOW the list, just like the system search does?
 * <p>
 * The form allows entering free text, author, title, series,...
 * <p>
 * The search gets the ID's of matching books, and returns this list when the 'show' button
 * is tapped. <strong>Only this list is returned</strong>; the original fields are not.
 *
 * <strong>Note:</strong> when the fab is clicked, we <strong>RETURN</strong>
 * to the {@link BooksOnBookshelf} Activity.
 * This is intentionally different from the behaviour of {@link AuthorWorksFragment}.
 */
public class SearchFtsFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SearchFtsFragment";

    /** create timer to tick every 250ms. */
    private static final int TIMER_TICK_MS = 250;
    /** 1 second idle trigger. */
    private static final int NANO_TO_SECONDS = 1_000_000_000;

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;

    /** Detect text changes and call userIsActive(...). */
    private final TextWatcher mTextWatcher = (ExtTextWatcher) editable -> {
        // we're not changing the Editable, no need to toggle this listener
        userIsActive(true);
    };

    /** Indicates user has changed something since the last search. */
    private boolean mSearchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long mIdleStart;
    /** Timer object for background idle searches. */
    @Nullable
    private Timer mTimer;
    private SearchFtsViewModel mVm;

    /** View Binding. */
    private FragmentAdvancedSearchBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(SearchFtsViewModel.class);
        mVm.init(getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentAdvancedSearchBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_local_search);

        mVm.onSearchCriteriaUpdate().observe(getViewLifecycleOwner(), this::onSearchCriteriaUpdate);
        mVm.onBooklistUpdate().observe(getViewLifecycleOwner(), this::onBooklistUpdate);

        // Detect when user touches something.
        mVb.content.setOnTouchListener((v, event) -> {
            userIsActive(false);
            return false;
        });

        // Detect when user types something.
        mVb.title.addTextChangedListener(mTextWatcher);
        mVb.seriesTitle.addTextChangedListener(mTextWatcher);
        mVb.author.addTextChangedListener(mTextWatcher);
        mVb.publisher.addTextChangedListener(mTextWatcher);
        mVb.keywords.addTextChangedListener(mTextWatcher);

        // Timer will be started in OnResume().
    }

    private void onSearchCriteriaUpdate(@NonNull final SearchCriteria criteria) {
        mVb.title.setText(criteria.getFtsBookTitle());
        mVb.seriesTitle.setText(criteria.getFtsSeriesTitle());
        mVb.author.setText(criteria.getFtsAuthor());
        mVb.publisher.setText(criteria.getFtsPublisher());
        mVb.keywords.setText(criteria.getFtsKeywords());
        onBooklistUpdate(criteria.getBookIdList());
    }

    private void onBooklistUpdate(final List<Long> idList) {
        final int count = idList.size();
        final String s = getResources().getQuantityString(R.plurals.n_books_found, count, count);
        getToolbar().setSubtitle(s);
    }

    /**
     * When the show results buttons is tapped, return and show the resulting booklist.
     */
    private void showFullResults() {
        final Intent resultIntent = new Intent().putExtra(SearchCriteria.BKEY, mVm.getCriteria());
        //noinspection ConstantConditions
        getActivity().setResult(Activity.RESULT_OK, resultIntent);
        getActivity().finish();
    }

    /**
     * When activity resumes, set search as dirty + start the timer.
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        userIsActive(true);
    }

    /**
     * When activity pauses, stop timer and get the search fields.
     */
    @Override
    @CallSuper
    public void onPause() {
        stopIdleTimer();
        viewToModel();

        super.onPause();
    }

    private void viewToModel() {
        final SearchCriteria criteria = mVm.getCriteria();
        //noinspection ConstantConditions
        criteria.setFtsBookTitle(mVb.title.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsSeriesTitle(mVb.seriesTitle.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsAuthor(mVb.author.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsPublisher(mVb.publisher.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsKeywords(mVb.keywords.getText().toString().trim());
    }

    /**
     * Called when a UI element detects the user doing something.
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    private void userIsActive(final boolean dirty) {
        synchronized (this) {
            // Mark search dirty if necessary
            mSearchIsDirty = mSearchIsDirty || dirty;
            // Reset the idle timer since the user did something
            mIdleStart = System.nanoTime();
            // If the search is dirty, make sure idle timer is running and update UI
            if (mSearchIsDirty) {
                startIdleTimer();
            }
        }
    }

    @Override
    @CallSuper
    public void onDestroy() {
        stopIdleTimer();
        super.onDestroy();
    }

    /**
     * start the idle timer.
     */
    private void startIdleTimer() {
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            if (mTimer != null) {
                return;
            }
            mTimer = new Timer();
            mIdleStart = System.nanoTime();
        }

        mTimer.schedule(new SearchUpdateTimer(), 0, TIMER_TICK_MS);
    }

    /**
     * Stop the timer.
     */
    private void stopIdleTimer() {
        final Timer timer;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            timer = mTimer;
            mTimer = null;
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * Implements a timer task (Runnable) and does a search when the user is idle.
     * <p>
     * If a search happens, we stop the idle timer.
     */
    class SearchUpdateTimer
            extends TimerTask {

        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize as we might have more than one timer running (but shouldn't)
            synchronized (this) {
                final boolean idle = (System.nanoTime() - mIdleStart) > NANO_TO_SECONDS;
                if (idle) {
                    // Stop the timer, it will be restarted when the user changes something
                    stopIdleTimer();
                    if (mSearchIsDirty) {
                        doSearch = true;
                        mSearchIsDirty = false;
                    }
                }
            }

            if (doSearch) {
                // we CAN actually read the Views here ?!
                viewToModel();
                mVm.search();
            }
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.toolbar_action_go, menu);

            final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
            final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
            button.setText(R.string.btn_show_list);
            button.setOnClickListener(v -> onMenuItemSelected(menuItem));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                showFullResults();
                return true;
            }
            return false;
        }
    }
}
