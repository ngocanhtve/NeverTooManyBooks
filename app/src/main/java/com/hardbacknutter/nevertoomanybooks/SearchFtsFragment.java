/*
 * @Copyright 2018-2022 HardBackNutter
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

    /** Indicates user has changed something since the last search. */
    private boolean searchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long idleStart;
    /** Timer object for background idle searches. */
    @Nullable
    private Timer timer;
    /** Detect text changes and call userIsActive(...). */
    private final TextWatcher textWatcher = (ExtTextWatcher) editable -> {
        // we're not changing the Editable, no need to toggle this listener
        userIsActive(true);
    };
    private SearchFtsViewModel vm;

    /** View Binding. */
    private FragmentAdvancedSearchBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchFtsViewModel.class);
        vm.init(getArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAdvancedSearchBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_local_search);

        vm.onSearchCriteriaUpdate().observe(getViewLifecycleOwner(), this::onSearchCriteriaUpdate);
        vm.onBooklistUpdate().observe(getViewLifecycleOwner(), this::onBooklistUpdate);

        // Detect when user touches something.
        vb.content.setOnTouchListener((v, event) -> {
            userIsActive(false);
            return false;
        });

        // Detect when user types something.
        vb.title.addTextChangedListener(textWatcher);
        vb.seriesTitle.addTextChangedListener(textWatcher);
        vb.author.addTextChangedListener(textWatcher);
        vb.publisher.addTextChangedListener(textWatcher);
        vb.keywords.addTextChangedListener(textWatcher);

        // Timer will be started in OnResume().
    }

    private void onSearchCriteriaUpdate(@NonNull final SearchCriteria criteria) {
        vb.title.setText(criteria.getFtsBookTitle());
        vb.seriesTitle.setText(criteria.getFtsSeriesTitle());
        vb.author.setText(criteria.getFtsAuthor());
        vb.publisher.setText(criteria.getFtsPublisher());
        vb.keywords.setText(criteria.getFtsKeywords());
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
        final Intent resultIntent = new Intent().putExtra(SearchCriteria.BKEY, vm.getCriteria());
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
        final SearchCriteria criteria = vm.getCriteria();
        //noinspection ConstantConditions
        criteria.setFtsBookTitle(vb.title.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsSeriesTitle(vb.seriesTitle.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsAuthor(vb.author.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsPublisher(vb.publisher.getText().toString().trim());
        //noinspection ConstantConditions
        criteria.setFtsKeywords(vb.keywords.getText().toString().trim());
    }

    /**
     * Called when a UI element detects the user doing something.
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    private void userIsActive(final boolean dirty) {
        synchronized (this) {
            // Mark search dirty if necessary
            searchIsDirty = searchIsDirty || dirty;
            // Reset the idle timer since the user did something
            idleStart = System.nanoTime();
            // If the search is dirty, make sure idle timer is running and update UI
            if (searchIsDirty) {
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
            if (timer != null) {
                return;
            }
            timer = new Timer();
            idleStart = System.nanoTime();
        }

        timer.schedule(new SearchUpdateTimer(), 0, TIMER_TICK_MS);
    }

    /**
     * Stop the timer.
     */
    private void stopIdleTimer() {
        final Timer tmpTimer;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            tmpTimer = this.timer;
            this.timer = null;
        }
        if (tmpTimer != null) {
            tmpTimer.cancel();
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
                final boolean idle = (System.nanoTime() - idleStart) > NANO_TO_SECONDS;
                if (idle) {
                    // Stop the timer, it will be restarted when the user changes something
                    stopIdleTimer();
                    if (searchIsDirty) {
                        doSearch = true;
                        searchIsDirty = false;
                    }
                }
            }

            if (doSearch) {
                // we CAN actually read the Views here ?!
                viewToModel();
                vm.search();
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
            //noinspection ConstantConditions
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
