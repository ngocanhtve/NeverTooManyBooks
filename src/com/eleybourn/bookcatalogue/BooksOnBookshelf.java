/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.backup.ImportOptions;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder.BookRowInfo;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.MenuPicker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StylePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.entities.EditAuthorBaseDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.entities.EditAuthorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.entities.EditPublisherDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.entities.EditSeriesDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.entities.LendBookDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.simplestring.EditFormatDialog;
import com.eleybourn.bookcatalogue.dialogs.simplestring.EditGenreDialog;
import com.eleybourn.bookcatalogue.dialogs.simplestring.EditLanguageDialog;
import com.eleybourn.bookcatalogue.dialogs.simplestring.EditLocationDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.tasks.GoodreadsTasks;
import com.eleybourn.bookcatalogue.searches.SearchSuggestionProvider;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonSearchPage;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.viewmodels.BooksOnBookshelfModel;
import com.eleybourn.bookcatalogue.widgets.FastScrollerOverlay;
import com.eleybourn.bookcatalogue.widgets.cfs.CFSRecyclerView;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 *
 * @author Philip Warner
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /**
     * Views for the current row level-text.
     * These are shown in the header of the list (just below the bookshelf spinner) while scrolling.
     */
    private final TextView[] mHeaderTextView = new TextView[2];
    /** Listener for GoodreadsTasks. */
    private final TaskListener<Object, Integer> mOnGoodreadsTaskListener =
            new TaskListener<Object, Integer>() {
                @Override
                public void onTaskFinished(final int taskId,
                                           final boolean success,
                                           @StringRes final Integer result,
                                           @Nullable final Exception e) {
                    GoodreadsTasks.handleGoodreadsTaskResult(taskId, success, result, e,
                                                             getWindow().getDecorView(), this);
                }
            };

    private final List<String> mBookshelfNameList = new ArrayList<>();
    /** The View for the list. */
    private RecyclerView mListView;
    private LinearLayoutManager mLinearLayoutManager;
    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;
    /** simple indeterminate progress spinner to show while getting the list of books. */
    private ProgressBar mProgressBar;
    /** The dropdown button to select a Bookshelf. */
    private Spinner mBookshelfSpinner;
    /** The adapter used to fill the mBookshelfSpinner. */
    private ArrayAdapter<String> mBookshelfSpinnerAdapter;
    /** The number of books in the current list. */
    private TextView mBookCountView;
    /** The ViewModel. */
    private BooksOnBookshelfModel mModel;
    /** Listener for the Bookshelf Spinner. */
    private final OnItemSelectedListener mOnBookshelfSelectionChanged =
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(@NonNull final AdapterView<?> parent,
                                           @NonNull final View view,
                                           final int position,
                                           final long id) {
                    @Nullable
                    String selected = (String) parent.getItemAtPosition(position);
                    String previous = mModel.getCurrentBookshelf().getName();

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debug(this, "mOnBookshelfSelectionChanged",
                                     "previous=" + previous, "selected=" + selected);
                    }

                    if (selected != null && !selected.equalsIgnoreCase(previous)) {

                        // make the new shelf the current
                        mModel.setCurrentBookshelf(BooksOnBookshelf.this, selected);

                        // new shelf selected, so we need a new list.
                        initBookList(true);
                    }
                }

                @Override
                public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                    // Do Nothing
                }
            };

    /**
     * Sure, this could al be condensed to initBookList,
     * but the intention is to make the rebuild fine grained.
     */
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {

        // changes were made to a single book
        if (bookId > 0) {
            //ENHANCE: update the modified row without a rebuild
            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
                savePosition();
                initBookList(true);

//            } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                // we don't display the lend-status in the list for now.
//                if (data != null) {
//                    data.getString(UniqueId.KEY_LOANEE);

            } else if ((fieldsChanged & BookChangedListener.BOOK_WAS_DELETED) != 0) {
                //ENHANCE: remove the defunct book without a rebuild
                savePosition();
                initBookList(true);
            }
        } else {
            // changes were made to (potentially) the whole list
            if (fieldsChanged != 0) {
                savePosition();
                initBookList(true);
            }
        }
    };

    /**
     * Apply the style that a user has selected.
     */
    private final StylePickerDialogFragment.StyleChangedListener mStyleChangedListener =
            new StylePickerDialogFragment.StyleChangedListener() {
                public void onStyleChanged(@NonNull final BooklistStyle style) {
                    // store the new data
                    mModel.onStyleChanged(style,
                                          mLinearLayoutManager.findFirstVisibleItemPosition(),
                                          mListView);
                    // and do a rebuild
                    initBookList(true);
                }
            };

    /** Define a scroller to update header detail when the top row changes. */
    private RecyclerView.OnScrollListener mOnScrollListener;

    @Override
    protected int getLayoutId() {
        return R.layout.booksonbookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // set the search capability to local (application) search
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        mModel = ViewModelProviders.of(this).get(BooksOnBookshelfModel.class);
        mModel.init(getIntent().getExtras(), savedInstanceState);

        // Restore bookshelf
        mModel.setCurrentBookshelf(Bookshelf.getPreferred(this, mModel.getDb()));

        // listen for the booklist being ready to display.
        mModel.getBuilderResult().observe(this, this::builderResultsAreReadyToDisplay);

        // check & get search text coming from a system search intent
        handleStandardSearchIntent();

        mProgressBar = findViewById(R.id.progressBar);

        mListView = findViewById(android.R.id.list);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLinearLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, mLinearLayoutManager.getOrientation()));

        if (!(mListView instanceof CFSRecyclerView)) {
            mListView.addItemDecoration(
                    new FastScrollerOverlay(this, R.drawable.fast_scroll_overlay));
        }

        // create adapter, but do not initialise the list, we'l populate it in onResume
        mAdapter = new BooklistAdapter(getLayoutInflater(),
                                       mModel.getCurrentStyle(),
                                       mModel.getDb(),
                                       null);
        mAdapter.setOnItemClickListener(this::onItemClick);
        mAdapter.setOnItemLongClickListener(this::onItemLongClick);
        mListView.setAdapter(mAdapter);

        // details for the header of the list.
        mBookCountView = findViewById(R.id.book_count);
        mHeaderTextView[0] = findViewById(R.id.level_1_text);
        mHeaderTextView[1] = findViewById(R.id.level_2_text);

        // Setup the bookshelf spinner and adapter.
        mBookshelfSpinner = findViewById(R.id.bookshelf_name);
        // note that the list of names is empty right now, we'l populate it in onResume
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(this,
                                                      R.layout.booksonbookshelf_bookshelf_spinner,
                                                      mBookshelfNameList);
        mBookshelfSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);

        if (savedInstanceState == null) {
            initHints();
        }

        // populating the spinner and loading the list is done in onResume.
        Tracker.exitOnCreate(this);
    }

    /**
     * @return the position that reflects the current bookshelf.
     */
    public int initBookshelfNameList() {
        mBookshelfNameList.clear();
        mBookshelfNameList.add(getString(R.string.bookshelf_all_books));
        // default to 'All Books'
        int currentPos = 0;
        // start at 1, as position 0 is 'All Books'
        int position = 1;

        for (Bookshelf bookshelf : mModel.getDb().getBookshelves()) {
            if (bookshelf.getId() == mModel.getCurrentBookshelf().getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfNameList.add(bookshelf.getName());
        }

        return currentPos;
    }

    /**
     * Populate the BookShelf list in the Spinner and set the current bookshelf/style.
     * <p>
     * Note: no longer triggers a rebuild, as it was getting messy who/when/where.
     * Caller takes care now.
     *
     * @return {@code true} if the selected shelf was changed (or set for the first time).
     */
    private boolean populateBookShelfSpinner() {

        @Nullable
        String previous = (String) mBookshelfSpinner.getSelectedItem();

        // disable the listener while we add the names.
        mBookshelfSpinner.setOnItemSelectedListener(null);
        // (re)load the list of names
        int currentPos = initBookshelfNameList();
        // and tell the adapter about it.
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // Set the current bookshelf.
        mBookshelfSpinner.setSelection(currentPos);
        // (re-)enable the listener
        mBookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);

        String selected = mModel.getCurrentBookshelf().getName();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debug(this, "populateBookShelfSpinner",
                         "previous=" + previous, "selected=" + selected);
        }

        // Flag up if the selection was different.
        return previous == null || !previous.equalsIgnoreCase(selected);
    }

    /**
     * Queue a rebuild of the underlying cursor and data. This is a wrapper for calling
     * {@link BooksOnBookshelfModel#initBookList}
     *
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    public void initBookList(final boolean isFullRebuild) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            // with stack trace, so we know who called us.
            Logger.debugWithStackTrace(this, "initBookList",
                                       "isFullRebuild=" + isFullRebuild);
        }

        // go create
        mModel.initBookList(isFullRebuild, this);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the booklist builder was done building the cursor.
     * <p>
     * Does some elementary checking, then hands of to {@link #displayList}
     * <p>
     * The incoming holder object is in fact NonNull. But let's keep the check here for sanity.
     *
     * @param holder the results to display.
     */
    private void builderResultsAreReadyToDisplay(@Nullable final BooksOnBookshelfModel
            .BuilderHolder holder) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugEnter(this, "builderResultsAreReadyToDisplay",
                              "holder=" + holder);
        }

        // *always* ...
        mProgressBar.setVisibility(View.GONE);

        if (holder != null) {
            // check if we have a valid cursor before using it.
            BooklistPseudoCursor resultListCursor = holder.getResultListCursor();
            if (resultListCursor != null && !resultListCursor.isClosed()) {
                displayList(resultListCursor, holder.getResultTargetRows());
                return;
            }
        }

        // no new list; restore the adapter to use the old list. See #onResume
        mAdapter.setCursor(mModel.getListCursor());
    }

    /**
     * Display the passed cursor in the ListView.
     * Called from two places:
     * <ul>
     * <li>After the builder is done.</li>
     * <li>When the user clicked expand/collapse.</li>
     * </ul>
     *
     * @param newListCursor New cursor to use
     * @param targetRows    if set, change the position to targetRow.
     */
    private void displayList(@NonNull final BooklistPseudoCursor newListCursor,
                             @Nullable final ArrayList<BookRowInfo> targetRows) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugEnter(this, "displayList",
                              "newListCursor=" + newListCursor);
        }

        // Update the activity title using the current style name.
        setTitle(mModel.getCurrentStyle().getLabel(this));

        mProgressBar.setVisibility(View.GONE);

        long t0;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            t0 = System.nanoTime();
        }

        // Save the old list so we can close it later
        BooklistPseudoCursor oldList = mModel.getListCursor();

        // and set the new list
        mModel.setListCursor(newListCursor);
        // make sure any old views with potentially incorrect layout are removed
        mListView.getRecycledViewPool().clear();
        // (re)set the adapter with the current style
        mAdapter.setStyle(mModel.getCurrentStyle());
        // set the list, this will trigger the adapter to refresh.
        mAdapter.setCursor(mModel.getListCursor());

        // Restore saved position
        //noinspection ConstantConditions
        final int count = mModel.getListCursor().getCount();
        if (mModel.getTopRow() >= count) {
            // the list is shorter then it used to be, just scroll to the end
            mModel.setTopRow(count - 1);
            mLinearLayoutManager.scrollToPosition(mModel.getTopRow());
        } else {
            mLinearLayoutManager.scrollToPositionWithOffset(mModel.getTopRow(),
                                                            mModel.getTopRowOffset());
        }

        // If a target position array is set, then queue a runnable to set the position
        // once we know how many items appear in a typical view and we can tell
        // if it is already in the view.
        if (targetRows != null) {
            mListView.post(() -> fixPositionWhenDrawn(targetRows));
        }

        // setup the list header
        final boolean showHeaderTexts = setupListHeader(count > 0);

        // Define a scroller to update header detail when the top row changes
        mListView.removeOnScrollListener(mOnScrollListener);
        mOnScrollListener = new RecyclerView.OnScrollListener() {
            public void onScrolled(@NonNull final RecyclerView recyclerView,
                                   final int dx,
                                   final int dy) {
                int firstVisibleItem = mLinearLayoutManager.findFirstVisibleItemPosition();
                // Need to check isDestroyed() because BooklistPseudoCursor misbehaves when
                // activity terminates and closes cursor
                if (mModel.getLastTopRow() != firstVisibleItem
                        && !isDestroyed()
                        && showHeaderTexts) {
                    setHeaderText(firstVisibleItem);
                }
            }

        };
        mListView.addOnScrollListener(mOnScrollListener);

        // all set, we can close the old list
        if (oldList != null) {
            if (mModel.getListCursor().getBuilder() != oldList.getBuilder()) {
                oldList.getBuilder().close();
            }
            oldList.close();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debugExit(this, "displayList",
                             (System.nanoTime() - t0) + "nano");

        } else if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugExit(this, "displayList");
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        // don't build the list needlessly
        if (App.isRecreating()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit("onResume", "isRecreating",
                                 LocaleUtils.toDebugString(this));
            }
            return;
        }
        // don't build the list needlessly
        if (isFinishing() || isDestroyed()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "onResume",
                                 "isFinishing=" + isFinishing(),
                                 "isDestroyed=" + isDestroyed());
            }
            return;
        }

        // Update the list of bookshelves + set the current bookshelf.
        boolean bookshelfChanged = populateBookShelfSpinner();

        // start the task that will provide the cursor/adapter/list.
        initBookList(true);

        // this commented section was/is an attempt to prevent a full rebuild...
        // ... but is it worth it?

//        // If we got here after onActivityResult, see if we need to force a rebuild.
//        Boolean fullOrPartialRebuild = mModel.isForceFullRebuild();
//
//        if (bookshelfChanged) {
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
//                Logger.debug(this, "onResume",
//                             "bookshelf changed, fullRebuild: true");
//            }
//            initBookList(true);
//
//        } else if (fullOrPartialRebuild != null) {
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
//                Logger.debug(this, "onResume",
//                             "fullOrPartialRebuild=" + fullOrPartialRebuild);
//            }
//            // Build the book list according to onActivityResult outcome
//            initBookList(fullOrPartialRebuild);
//
//        } else if (mModel.hasListBeenLoaded()) {
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
//                Logger.debug(this, "onResume",
//                             "reusing existing list");
//            }
//            // a list has been build previously and we should re-use it.
////            BooklistBuilder booklistBuilder = mModel.getListCursor().getBuilder();
////            displayList(booklistBuilder.getNewListCursor(), null);
//            initBookList(true);
//
//        } else {
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
//                Logger.debug(this, "onResume",
//                             "rebuild full for other reason.");
//            }
//            initBookList(true);
//        }


        // clear the cursor; we'll prepare a new one and meanwhile the view/adapter should
        // obviously NOT try to display the old list.
        // Note we do not clear the cursor on the model here, so we have the option of re-using it.
        mAdapter.setCursor(null);
        // always reset for next iteration.
        mModel.setFullRebuild(null);

        Tracker.exitOnResume(this);
    }

    /**
     * Save position when paused.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        if (mModel.getSearchCriteria().isEmpty()) {
            savePosition();
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * Save current position information in the preferences, including view nodes that are expanded.
     * We do this to preserve this data across application shutdown/startup.
     *
     * <p>
     * ENHANCE: Handle positions a little better when books are deleted.
     * <p>
     * Deleting a book by 'n' authors from the last author in list results in the list decreasing
     * in length by, potentially, n*2 items. The current code will return to the old position
     * in the list after such an operation...which will be too far down.
     */
    private void savePosition() {
        if (isDestroyed()) {
            return;
        }

        mModel.savePosition(mLinearLayoutManager.findFirstVisibleItemPosition(), mListView);
    }

    /**
     * android.intent.action.SEARCH.
     */
    private void handleStandardSearchIntent() {
        String searchText = "";
        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            searchText = getIntent().getStringExtra(SearchManager.QUERY).trim();

        } else if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            searchText = getIntent().getDataString();
        }
        mModel.getSearchCriteria().setKeywords(searchText);
        initSearchField(mModel.getSearchCriteria().getKeywords());
    }

    /**
     * There was a search requested by the user.
     *
     * @return Returns {@code true} if search launched, and {@code false} if the activity does
     * not respond to search.
     * <p>
     * Note: uses the 'advanced' FTS search activity. To use the standard search,
     * comment this method out. The system will use {@link SearchSuggestionProvider}
     * as configured in res/xml/searchable.xml
     * <p>
     * TOMF: https://developer.android.com/guide/topics/search/search-dialog
     * the way this is implemented is a bit of a shoehorn... to be revisited.
     */
    @Override
    public boolean onSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        mModel.getSearchCriteria().to(intent);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        // add the 'add book' submenu
        MenuHandler.addCreateBookSubMenu(menu);

        menu.add(Menu.NONE, R.id.MENU_SORT, 0, R.string.menu_sort_and_style_ellipsis)
            .setIcon(R.drawable.ic_sort_by_alpha)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_EXPAND, 0, R.string.menu_expand_all)
            .setIcon(R.drawable.ic_unfold_more);

        menu.add(Menu.NONE, R.id.MENU_COLLAPSE, 0, R.string.menu_collapse_all)
            .setIcon(R.drawable.ic_unfold_less);

        menu.add(Menu.NONE, R.id.MENU_CLEAR_FILTERS, 0, R.string.menu_clear_filters)
            .setIcon(R.drawable.ic_undo);

        if (BuildConfig.DEBUG /* always */) {
            SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_DEBUG, R.id.SUBMENU_DEBUG,
                                              0, R.string.debug);

            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_RUN_TEST, 0, R.string.debug_test);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_PREFS, 0, R.string.lbl_settings);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_STYLE, 0, R.string.lbl_style);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_TRACKER, 0, R.string.debug_history);
            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_DUMP_BOB_TABLES, 0,
                        R.string.debug_bob_tables);

            subMenu.add(Menu.NONE, R.id.MENU_DEBUG_EXPORT_DATABASE, 0,
                        R.string.lbl_copy_database);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {

            case R.id.MENU_SORT:
                HintManager.displayHint(getLayoutInflater(), R.string.hint_booklist_style_menu,
                                        this::showStylePicker);
                return true;

            case R.id.MENU_EXPAND:
                expandOrCollapseAllNodes(true);
                return true;

            case R.id.MENU_COLLAPSE:
                expandOrCollapseAllNodes(false);
                return true;

            case R.id.MENU_CLEAR_FILTERS:
                mModel.getSearchCriteria().clear();
                initBookList(true);
                return true;

            default:
                if (BuildConfig.DEBUG  /* always */) {
                    switch (item.getItemId()) {
                        case R.id.MENU_DEBUG_RUN_TEST:
                            // manually swapped for whatever test I want to run...
                            // crude? yup! nasty? absolutely!
                            // doSomething();
                            return true;

                        case R.id.MENU_DEBUG_DUMP_PREFS:
                            Prefs.dumpPreferences(null);
                            return true;

                        case R.id.MENU_DEBUG_DUMP_STYLE:
                            Logger.debug(this, "onOptionsItemSelected",
                                         mModel.getCurrentStyle());
                            return true;

                        case R.id.MENU_DEBUG_DUMP_TRACKER:
                            Logger.debug(this, "onOptionsItemSelected",
                                         Tracker.getEventsInfo());
                            return true;

                        case R.id.MENU_DEBUG_DUMP_BOB_TABLES:
                            Logger.debug(this, "onOptionsItemSelected",
                                         mModel.debugBuilderTables());
                            return true;

                        case R.id.MENU_DEBUG_EXPORT_DATABASE:
                            StorageUtils.exportDatabaseFiles();
                            UserMessage.showUserMessage(mListView,
                                                        R.string.progress_end_backup_success);
                            return true;
                    }
                }

                return MenuHandler.handleBookSubMenu(this, item)
                        || super.onOptionsItemSelected(item);
        }
    }

    private void showStylePicker() {
        StylePickerDialogFragment.newInstance(getSupportFragmentManager(),
                                              mModel.getCurrentStyle(), false);
    }

    /**
     * Expand/Collapse the current position in the list.
     *
     * @param expand {@code true} to expand, {@code false} to collapse
     */
    private void expandOrCollapseAllNodes(final boolean expand) {

        int layoutPosition = mLinearLayoutManager.findFirstCompletelyVisibleItemPosition();
        // It is possible that the list will be empty, if so, ignore
        if (layoutPosition != RecyclerView.NO_POSITION) {
            BooklistAdapter.RowViewHolder holder = (BooklistAdapter.RowViewHolder)
                    mListView.findViewHolderForLayoutPosition(layoutPosition);

            @SuppressWarnings("ConstantConditions")
            int oldAbsPos = holder.absolutePosition;

            savePosition();

            // get the builder from the current cursor.
            @SuppressWarnings("ConstantConditions")
            BooklistBuilder booklistBuilder = mModel.getListCursor().getBuilder();
            // do the work, and re-position.
            booklistBuilder.expandAll(expand);
            mModel.setTopRow(booklistBuilder.getPosition(oldAbsPos));

            // pass in a new cursor and display the list.
            // the old cursor will get closed afterwards.
            displayList(booklistBuilder.getNewListCursor(), null);
        }
    }

    /**
     * The user clicked on a row:
     * <ul>
     * <li>Book: open the details screen.</li>
     * <li>Not a book: expand/collapse the section as appropriate.</li>
     * </ul>
     */
    public void onItemClick(@NonNull final View view) {
        int position = (int) view.getTag(R.id.TAG_POSITION);

        BooklistPseudoCursor listCursor = mModel.getListCursor();
        //noinspection ConstantConditions
        listCursor.moveToPosition(position);
        BooklistCursorRow row = listCursor.getCursorRow();

        //noinspection SwitchStatementWithTooFewBranches
        switch (row.getRowKind()) {
            // If it's a book, view or edit it.
            case BooklistGroup.RowKind.BOOK:
                long bookId = row.getBookId();
                boolean openInReadOnly =
                        App.getPrefs().getBoolean(Prefs.pk_bob_open_book_read_only, true);

                if (openInReadOnly) {
                    String listTableName = listCursor.getBuilder().createFlattenedBooklist();

                    Intent intent = new Intent(this, BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, bookId)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST, listTableName)
                            .putExtra(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST_POSITION, position);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_VIEW);

                } else {
                    Intent intent = new Intent(this, EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_ID, bookId)
                            .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                }
                break;

            // if's not a book, expand/collapse as needed
            default:
                // If it's a level, expand/collapse. Technically, we could expand/collapse any level
                // but storing and recovering the view becomes unmanageable.
                // ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/542
                if (row.getLevel() == 1) {
                    listCursor.getBuilder().toggleExpandNode(row.getAbsolutePosition());
                    listCursor.requery();
                    mAdapter.notifyDataSetChanged();
                }
                break;
        }
    }

    /**
     * User long-clicked on a row. Bring up a context menu as appropriate.
     */
    private boolean onItemLongClick(@NonNull final View view) {
        int position = (int) view.getTag(R.id.TAG_POSITION);

        final BooklistPseudoCursor listCursor = mModel.getListCursor();
        //noinspection ConstantConditions
        listCursor.moveToPosition(position);

        BooklistCursorRow cursorRow = listCursor.getCursorRow();
        Menu menu = MenuPicker.createMenu(this);
        // build/check the menu for this row
        if (prepareListViewContextMenu(menu, cursorRow)) {
            // we have a menu to show
            String menuTitle;
            if (cursorRow.getRowKind() == BooklistGroup.RowKind.BOOK) {
                menuTitle = cursorRow.getTitle();
            } else {
                menuTitle = cursorRow.getLevelText(this, cursorRow.getLevel());
            }
            // bring up the context menu
            new MenuPicker<>(this, menuTitle, menu, position, (menuItem, pos) -> {
                listCursor.moveToPosition(pos);
                return onContextItemSelected(menuItem, listCursor.getCursorRow());
            }).show();
        }
        return true;
    }

    /**
     * Adds 'standard' menu options based on row type.
     *
     * @param row Row view pointing to current row for this context menu
     *
     * @return {@code true} if there actually is a menu to show.
     * {@code false} if not OR if the only menus would be the 'search Amazon' set.
     */
    boolean prepareListViewContextMenu(@NonNull final Menu /* in/out */ menu,
                                       @NonNull final BooklistCursorRow row) {
        menu.clear();

        int rowKind = row.getRowKind();
        switch (rowKind) {
            case BooklistGroup.RowKind.BOOK:
                if (row.isRead()) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read)
                        .setIcon(R.drawable.ic_check_box);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete)
                    .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_NOTES, 0, R.string.menu_edit_book_notes)
                    .setIcon(R.drawable.ic_note);

                if (App.isUsed(DBDefinitions.KEY_LOANEE)) {
                    boolean isAvailable = null == mModel.getDb().getLoaneeByBookId(row.getBookId());
                    if (isAvailable) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_ADD,
                                 MenuHandler.MENU_ORDER_LENDING, R.string.menu_loan_lend_book)
                            .setIcon(R.drawable.ic_people);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                                 MenuHandler.MENU_ORDER_LENDING, R.string.menu_loan_return_book)
                            .setIcon(R.drawable.ic_people);
                    }
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE, MenuHandler.MENU_ORDER_SHARE,
                         R.string.menu_share_this)
                    .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS, 0,
                         R.string.gr_menu_send_to_goodreads)
                    .setIcon(R.drawable.ic_goodreads);
                break;

            case BooklistGroup.RowKind.AUTHOR:
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_DETAILS, 0, R.string.menu_author_details)
                    .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, 0, R.string.menu_edit)
                    .setIcon(R.drawable.ic_edit);
                if (row.isAuthorComplete()) {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_incomplete)
                        .setIcon(R.drawable.ic_check_box);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_complete)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                }
                break;

            case BooklistGroup.RowKind.SERIES:
                if (row.getSeriesId() != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, 0, R.string.menu_delete)
                        .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                    if (row.isSeriesComplete()) {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_incomplete)
                            .setIcon(R.drawable.ic_check_box);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_complete)
                            .setIcon(R.drawable.ic_check_box_outline_blank);
                    }
                }
                break;

            case BooklistGroup.RowKind.PUBLISHER:
                if (!row.getPublisherName().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.LANGUAGE:
                if (!row.getLanguageCode().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.LOCATION:
                if (!row.getLocation().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.GENRE:
                if (!row.getGenre().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case BooklistGroup.RowKind.FORMAT:
                if (!row.getFormat().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "Unexpected rowKind=" + rowKind);
                break;
        }

        // if there are no specific menus for the current row.
        if (menu.size() == 0) {
            return false;
        }

        // at least one other menu item; now add Amazon menus ?
        boolean hasAuthor = row.hasAuthorId() && row.getAuthorId() > 0;
        boolean hasSeries = row.hasSeriesId() && row.getSeriesId() > 0;
        if (hasAuthor || hasSeries) {
            SubMenu subMenu = MenuHandler.addAmazonSearchSubMenu(menu);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, hasAuthor);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                                    hasAuthor && hasSeries);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_IN_SERIES, hasSeries);
        }

        return true;
    }

    /**
     * @param menuItem Related MenuItem
     * @param row      Row view for affected cursor row
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                  @NonNull final BooklistCursorRow row) {

        final long bookId = row.getBookId();

        FragmentManager fm = getSupportFragmentManager();

        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_DELETE:
                String title = row.getTitle();
                List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBookAlert(this, title, authors, () -> {
                    mModel.getDb().deleteBook(bookId);
                    mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_WAS_DELETED,
                                                       null);
                });

                return true;

            case R.id.MENU_BOOK_READ:
                // toggle the read status
                if (mModel.getDb().setBookRead(bookId, !row.isRead())) {
//                    Bundle data = new Bundle();
//                    data.putBoolean(KEY_READ, !row.isRead());
//                    data.putInt(UniqueId.POSITION, row.getPosition());
                    mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;

            case R.id.MENU_BOOK_EDIT: {
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                  EditBookFragment.TAB_EDIT_NOTES);
                startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD:
                if (fm.findFragmentByTag(LendBookDialogFragment.TAG) == null) {
                    LendBookDialogFragment.newInstance(bookId, row.getAuthorId(), row.getTitle())
                                          .show(fm, LendBookDialogFragment.TAG);
                }
                return true;

            case R.id.MENU_BOOK_LOAN_DELETE:
                mModel.getDb().deleteLoan(bookId);
                mBookChangedListener.onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SHARE:
                Book book = new Book(bookId, mModel.getDb());
                startActivity(Intent.createChooser(book.getShareBookIntent(this),
                                                   getString(R.string.menu_share_this)));
                return true;

            case R.id.MENU_BOOK_SEND_TO_GOODREADS:
                //TEST sendOneBook
                new GoodreadsTasks.SendOneBookTask(this, bookId, mOnGoodreadsTaskListener)
                        .execute();
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT:
                if (fm.findFragmentByTag(EditSeriesDialogFragment.TAG) == null) {
                    //noinspection ConstantConditions
                    EditSeriesDialogFragment.newInstance(
                            mModel.getDb().getSeries(row.getSeriesId()))
                                            .show(fm, EditSeriesDialogFragment.TAG);
                }
                return true;

            case R.id.MENU_SERIES_COMPLETE:
                // toggle the complete status
                if (mModel.getDb().setSeriesComplete(row.getSeriesId(), !row.isSeriesComplete())) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES, null);
                }
                return true;

            case R.id.MENU_SERIES_DELETE: {
                Series series = mModel.getDb().getSeries(row.getSeriesId());
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(
                            this, series, () -> {
                                mModel.getDb().deleteSeries(series.getId());
                                mBookChangedListener.onBookChanged(0, BookChangedListener.SERIES,
                                                                   null);
                            });
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_DETAILS: {
                Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, row.getAuthorId());
                startActivity(intent);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT:
                if (fm.findFragmentByTag(EditAuthorDialogFragment.TAG) == null) {
                    //noinspection ConstantConditions
                    EditAuthorDialogFragment.newInstance(
                            mModel.getDb().getAuthor(row.getAuthorId()))
                                            .show(fm, EditAuthorDialogFragment.TAG);
                }
                return true;

            case R.id.MENU_AUTHOR_COMPLETE:
                // toggle the complete status
                if (mModel.getDb().setAuthorComplete(row.getAuthorId(), !row.isAuthorComplete())) {
                    mBookChangedListener.onBookChanged(0, BookChangedListener.AUTHOR, null);
                }
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT:
                if (fm.findFragmentByTag(EditPublisherDialogFragment.TAG) == null) {
                    EditPublisherDialogFragment.newInstance(new Publisher(row.getPublisherName()))
                                               .show(fm, EditPublisherDialogFragment.TAG);
                }
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT:
                new EditFormatDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getFormat());
                return true;

            case R.id.MENU_GENRE_EDIT:
                new EditGenreDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getGenre());
                return true;

            case R.id.MENU_LANGUAGE_EDIT:
                new EditLanguageDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getLanguageCode());
                return true;

            case R.id.MENU_LOCATION_EDIT:
                new EditLocationDialog(this, mModel.getDb(), mBookChangedListener)
                        .edit(row.getLocation());
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonSearchPage.open(this, mListView,
                                      mModel.getAuthorFromRow(row), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonSearchPage.open(this, mListView,
                                      null, mModel.getSeriesFromRow(row));
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonSearchPage.open(this, mListView,
                                      mModel.getAuthorFromRow(row), mModel.getSeriesFromRow(row));
                return true;

            default:
                return false;
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (StylePickerDialogFragment.TAG.equals(fragment.getTag())) {
            ((StylePickerDialogFragment) fragment).setListener(mStyleChangedListener);

        } else if (EditAuthorDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditAuthorBaseDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (EditPublisherDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditPublisherDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (EditSeriesDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditSeriesDialogFragment) fragment).setListener(mBookChangedListener);

        } else if (LendBookDialogFragment.TAG.equals(fragment.getTag())) {
            ((LendBookDialogFragment) fragment).setListener(mBookChangedListener);
        }
    }

    /**
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        mModel.setCurrentPositionedBookId(0);

        switch (requestCode) {
            case UniqueId.REQ_BOOK_VIEW:
            case UniqueId.REQ_BOOK_EDIT:
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                        // handle re-positioning better
                        //mCurrentPositionedBookId = [somehow get the ID 'above' the deleted one];
                        mModel.setFullRebuild(false);
                        break;

                    case Activity.RESULT_OK:
                        Objects.requireNonNull(data);
                        long newId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                        mModel.setFullRebuild(false);
                        break;


                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.warnWithStackTrace(this, "unknown resultCode=" + resultCode);
                        }
                        break;
                }
                break;

            case UniqueId.REQ_BOOK_SEARCH:
                if (resultCode == Activity.RESULT_OK) {
                    // don't enforce having an id. We might not have found or added anything.
                    // but if we do, the data will be what EditBookActivity returns.
                    if (data != null) {
                        long newId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                        if (newId != 0) {
                            mModel.setCurrentPositionedBookId(newId);
                        }
                    }
                    // regardless, do a rebuild just in case
                    mModel.setFullRebuild(false);
                }
                break;

            case UniqueId.REQ_ADVANCED_LOCAL_SEARCH:
                // no changes made, but we might have data to act on.
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Bundle extras = data.getExtras();
                        if (extras != null) {
                            mModel.getSearchCriteria().from(extras, true);
                            initSearchField(mModel.getSearchCriteria().getKeywords());
                        }
                        mModel.setFullRebuild(true);
                    }
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    // the last edited/inserted shelf
                    long bookshelfId = data.getLongExtra(DBDefinitions.KEY_ID,
                                                         Bookshelf.DEFAULT_ID);
                    mModel.setCurrentBookshelf(bookshelfId);
                    mModel.setFullRebuild(true);
                }
                break;

            // from BaseActivity Nav Panel
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (resultCode == Activity.RESULT_OK) {
                    if ((data != null) && data.hasExtra(UniqueId.BKEY_IMPORT_RESULT)) {
                        // RestoreActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                       ImportOptions.NOTHING);

                        if ((options & ImportOptions.PREFERENCES) != 0) {
                            // the imported prefs could have a different preferred bookshelf.
                            Bookshelf newBookshelf = Bookshelf.getPreferred(this,
                                                                            mModel.getDb());
                            if (!mModel.getCurrentBookshelf().equals(newBookshelf)) {
                                // if it was.. switch to it.
                                mModel.setCurrentBookshelf(newBookshelf);
                                mModel.setFullRebuild(true);
                            }
                        }
                    } else if ((data != null) && data.hasExtra(UniqueId.BKEY_EXPORT_RESULT)) {
                        // BackupActivity:
                        int options = data.getIntExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                       ExportOptions.NOTHING);
                    }

//                    if ((data != null) && data.hasExtra(UniqueId.ZZZZ)) {
//                        // AdminActivity has results of it's own,, but no action needed for them.
//                        // child-activities results:
//                        // SearchAdminActivity:
//                    }
                }
                break;

            // from BaseActivity Nav Panel or from sort menu dialog
            // TODO: more complicated then it should be....
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES: {
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING:
                    case UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES:
                        // no data
                        mModel.setFullRebuild(true);
                        break;

                    case UniqueId.ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE:
                        Objects.requireNonNull(data);
                        BooklistStyle style = data.getParcelableExtra(UniqueId.BKEY_STYLE);
                        if (style != null) {
                            // save the new bookshelf/style combination
                            mModel.getCurrentBookshelf().setAsPreferred();
                            mModel.setCurrentStyle(style);
                        }
                        mModel.setFullRebuild(true);
                        break;

                    default:
                        if (resultCode != Activity.RESULT_CANCELED) {
                            Logger.warnWithStackTrace(this, "unknown resultCode=" + resultCode);
                        }
                        break;
                }
                break;
            }

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #displayList}
     */
    private void fixPositionWhenDrawn(@NonNull final ArrayList<BookRowInfo> targetRows) {
        // Find the actual extend of the current view and get centre.
        int first = mLinearLayoutManager.findFirstVisibleItemPosition();
        int last = mLinearLayoutManager.findLastVisibleItemPosition();
        int centre = (last + first) / 2;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
            Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                         " New List: (" + first + ", " + last + ")<-" + centre);
        }
        // Get the first 'target' and make it 'best candidate'
        BookRowInfo best = targetRows.get(0);
        int dist = Math.abs(best.listPosition - centre);
        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetRows.size(); i++) {
            BookRowInfo ri = targetRows.get(i);
            int newDist = Math.abs(ri.listPosition - centre);
            if (newDist < dist) {
                dist = newDist;
                best = ri;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
            Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                         " Best listPosition @" + best.listPosition);
        }
        // Try to put at top if not already visible, or only partially visible
        if (first >= best.listPosition || last <= best.listPosition) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_FIX_POSITION) {
                Logger.debug(BooksOnBookshelf.class, "fixPositionWhenDrawn",
                             " Adjusting position");
            }
            // setSelectionFromTop does not seem to always do what is expected.
            // But adding smoothScrollToPosition seems to get the job done reasonably well.
            //
            // Specific problem occurs if:
            // - put phone in portrait mode
            // - edit a book near bottom of list
            // - turn phone to landscape
            // - save the book (don't cancel)
            // Book will be off bottom of screen without the smoothScroll in the second Runnable.
            //
            mLinearLayoutManager.scrollToPositionWithOffset(best.listPosition, 0);
            // Code below does not behave as expected.
            // Results in items often being near bottom.
            //lv.setSelectionFromTop(best.listPosition, lv.getHeight() / 2);

            // Without this call some positioning may be off by one row (see above).
            final int newPos = best.listPosition;
            mListView.post(() -> mListView.smoothScrollToPosition(newPos));

            //int newTop = best.listPosition - (last-first)/2;
            // if (BuildConfig.DEBUG && BOB_FIX_POSITION) {
            //Logger.info(this, "fixPositionWhenDrawn", "New Top @" + newTop );
            //}
            //lv.setSelection(newTop);
        }
    }

    /**
     * display or hide the search text field in the header.
     *
     * @param searchText the text which was used for the search (if any).
     */
    void initSearchField(@Nullable final String searchText) {
        TextView searchTextView = findViewById(R.id.search_text);
        if (searchText == null || searchText.isEmpty()) {
            searchTextView.setVisibility(View.GONE);
        } else {
            searchTextView.setVisibility(View.VISIBLE);
            searchTextView.setText(getString(R.string.search_with_text, searchText));
        }
    }

    /**
     * Show the hints used in this class.
     */
    private void initHints() {
        HintManager.displayHint(getLayoutInflater(),
                                R.string.hint_view_only_book_details, null);
        HintManager.displayHint(getLayoutInflater(),
                                R.string.hint_book_list, null);
        if (StartupActivity.showAmazonHint()) {
            HintManager.showAmazonHint(getLayoutInflater());
        }
    }

    /**
     * Convenience wrapper method that handles the 3 steps of preparing the list header.
     *
     * @param listHasItems Flag to indicate there are in fact items in the list
     *
     * @return {@code true} if the header should display the 'level' texts.
     */
    private boolean setupListHeader(final boolean listHasItems) {
        populateBookCountField();
        final boolean showHeaderTexts = setHeaderTextVisibility();
        // Set the initial details to the current first visible row.
        if (listHasItems && showHeaderTexts) {
            setHeaderText(mModel.getTopRow());
        }
        return showHeaderTexts;
    }

    /**
     * Display the number of books in the current list.
     */
    private void populateBookCountField() {
        int showHeaderFlags = mModel.getCurrentStyle().getShowHeaderInfo();
        if ((showHeaderFlags & BooklistStyle.SUMMARY_SHOW_COUNT) != 0) {
            int totalBooks = mModel.getTotalBooks();
            int uniqueBooks = mModel.getUniqueBooks();
            String stringArgs;
            if (uniqueBooks != totalBooks) {
                stringArgs = getString(R.string.info_displaying_n_books_in_m_entries,
                                       uniqueBooks, totalBooks);
            } else {
                stringArgs = getResources().getQuantityString(R.plurals.displaying_n_books,
                                                              uniqueBooks, uniqueBooks);
            }
            mBookCountView.setText(getString(R.string.brackets, stringArgs));
            mBookCountView.setVisibility(View.VISIBLE);
        } else {
            mBookCountView.setVisibility(View.GONE);
        }
    }

    /**
     * @return {@code true} if the style supports any level at all.
     */
    private boolean setHeaderTextVisibility() {

        BooklistStyle style = mModel.getCurrentStyle();
        // for level, set the visibility of the views.
        for (int level = 1; level <= 2; level++) {
            int index = level - 1;

            // a header is visible if
            // 1. the cursor provides the data for this level, and
            // 2. the style defined the level.
            //noinspection ConstantConditions
            if (mModel.getListCursor().levels() > level && style.hasHeaderForLevel(level)) {
                mHeaderTextView[index].setVisibility(View.VISIBLE);
                mHeaderTextView[index].setText("");
            } else {
                mHeaderTextView[index].setVisibility(View.GONE);
            }
        }
        // do we show any levels?
        return style.hasHeaderForLevel(1) || style.hasHeaderForLevel(2);
    }

    /**
     * Update the list header to match the current top item.
     *
     * @param firstVisibleItem Top row which is visible
     */
    private void setHeaderText(@IntRange(from = 0) final int firstVisibleItem) {
        if (firstVisibleItem >= 0) {
            mModel.setLastTopRow(firstVisibleItem);
        } else {
            mModel.setLastTopRow(0);
        }

        if (mHeaderTextView[0].getVisibility() == View.VISIBLE) {
            BooklistPseudoCursor listCursor = mModel.getListCursor();
            @SuppressWarnings("ConstantConditions")
            BooklistCursorRow row = listCursor.getCursorRow();
            if (listCursor.moveToPosition(mModel.getLastTopRow())) {
                mHeaderTextView[0].setText(row.getLevelText(this, 1));
                if (mHeaderTextView[1].getVisibility() == View.VISIBLE) {
                    mHeaderTextView[1].setText(row.getLevelText(this, 2));
                }
            }
        }
    }
}
