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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooksOutput;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils;

public class BooksOnBookshelfViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelfViewModel";

    /** collapsed/expanded. */
    static final String BKEY_LIST_STATE = TAG + ":list.state";

    static final String BKEY_PROPOSE_BACKUP = TAG + ":pb";

    /** Allows to set an explicit shelf. */
    static final String BKEY_BOOKSHELF = TAG + ":bs";

    private static final String ERROR_NULL_BOOKLIST = "booklist";
    /** Cache for all bookshelves. */
    private final List<Bookshelf> bookshelfList = new ArrayList<>();
    private final BoBTask boBTask = new BoBTask();
    /** Holder for all search criteria. See {@link SearchCriteria} for more info. */
    @Nullable
    private SearchCriteria searchCriteria;
    /** Database Access. */
    private BookDao bookDao;
    /**
     * Flag (potentially) set when coming back from another Activity.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     */
    private boolean forceRebuildInOnResume;
    /** Flag to indicate that a list has been successfully loaded. */
    private boolean listLoaded;

    /** Flag to prompt the user to make a backup after startup. */
    private boolean proposeBackup;

    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf bookshelf;
    /** The row id we want the new list to display more-or-less in the center. */
    private long currentCenteredBookId;
    /** Preferred booklist state in next rebuild. */
    private RebuildBooklist rebuildMode;
    /** Current displayed list. */
    @Nullable
    private Booklist booklist;

    // Not using a list here as we need separate access to the amazon handler
    @Nullable
    private ViewBookOnWebsiteHandler viewBookHandler;
    @Nullable
    private AmazonHandler amazonHandler;

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return boBTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onCancelled() {
        return boBTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onFailure() {
        return boBTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<BoBTask.Outcome>>> onFinished() {
        return boBTask.onFinished();
    }

    @Override
    protected void onCleared() {
        if (booklist != null) {
            booklist.close();
        }

        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @Nullable final Bundle args) {

        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            // first start of the activity, read from user preference
            rebuildMode = RebuildBooklist.getPreferredMode(context);

            if (args != null) {
                proposeBackup = args.getBoolean(BKEY_PROPOSE_BACKUP, false);

                // extract search criteria if any are present
                searchCriteria = args.getParcelable(SearchCriteria.BKEY);
                if (searchCriteria == null) {
                    searchCriteria = new SearchCriteria();
                }

                final List<Long> bookIdlist = ParcelUtils.unwrap(args, Book.BKEY_BOOK_ID_LIST);
                if (bookIdlist != null) {
                    searchCriteria.setBookIdList(bookIdlist);
                }

                // allow the caller to override the user preference
                if (args.containsKey(BKEY_LIST_STATE)) {
                    // If present, must not be null
                    rebuildMode = Objects.requireNonNull(args.getParcelable(BKEY_LIST_STATE),
                                                         BKEY_LIST_STATE);
                }

                // check for an explicit bookshelf set
                if (args.containsKey(BKEY_BOOKSHELF)) {
                    // might be null, that's ok.
                    bookshelf = Bookshelf.getBookshelf(context, args.getInt(BKEY_BOOKSHELF));
                }
            }
        } else {
            // always preserve the state when the hosting fragment was revived
            rebuildMode = RebuildBooklist.FromSaved;
        }

        // create if not explicitly set above
        if (searchCriteria == null) {
            searchCriteria = new SearchCriteria();
        }

        // Set the last/preferred bookshelf if not explicitly set above
        // or use the default == first start of the app
        if (bookshelf == null) {
            bookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT);
        }
    }

    boolean isProposeBackup() {
        // We only offer ONCE
        final boolean tmp = proposeBackup;
        proposeBackup = false;
        return tmp;
    }

    void resetPreferredListRebuildMode(@NonNull final Context context) {
        rebuildMode = RebuildBooklist.getPreferredMode(context);
    }

    @NonNull
    MenuHandler getViewBookHandler() {
        if (viewBookHandler == null) {
            viewBookHandler = new ViewBookOnWebsiteHandler();
        }
        return viewBookHandler;
    }

    @NonNull
    MenuHandler getAmazonHandler() {
        if (amazonHandler == null) {
            amazonHandler = new AmazonHandler();
        }
        return amazonHandler;
    }

    /**
     * Get the Bookshelf list to show in the Spinner.
     * Will be empty until a call to {@link #reloadBookshelfList(Context)} is made.
     *
     * @return list
     */
    @NonNull
    List<Bookshelf> getBookshelfList() {
        return bookshelfList;
    }

    /**
     * Construct the Bookshelf list to show in the Spinner.
     *
     * @param context Current context.
     */
    void reloadBookshelfList(@NonNull final Context context) {
        bookshelfList.clear();
        bookshelfList.add(Bookshelf.getBookshelf(context, Bookshelf.ALL_BOOKS));
        bookshelfList.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
    }

    /**
     * Find the position of the currently set Bookshelf in the Spinner.
     * (with fallback to the default, or to 0 if needed)
     *
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    int getSelectedBookshelfSpinnerPosition(@NonNull final Context context) {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);

        // Not strictly needed, but guard against future changes
        if (bookshelfList.isEmpty()) {
            reloadBookshelfList(context);
        }

        // position we want to find
        Integer selectedPosition = null;
        // fallback if no selection found
        Integer defaultPosition = null;

        for (int i = 0; i < bookshelfList.size(); i++) {
            final long id = bookshelfList.get(i).getId();
            // find the position of the default shelf.
            if (id == Bookshelf.DEFAULT) {
                defaultPosition = i;
            }
            // find the position of the selected shelf
            if (id == bookshelf.getId()) {
                selectedPosition = i;
            }
        }

        if (selectedPosition != null) {
            return selectedPosition;

        } else {
            return Objects.requireNonNullElse(defaultPosition, 0);
        }
    }

    @NonNull
    Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf;
    }

    /**
     * Load and set the desired Bookshelf.
     *
     * @param context     Current context
     * @param bookshelfId of desired Bookshelf
     */
    void setCurrentBookshelf(@NonNull final Context context,
                             final long bookshelfId) {
        final long previousBookshelfId = bookshelf == null ? 0 : bookshelf.getId();

        bookshelf = ServiceLocator.getInstance().getBookshelfDao().getById(bookshelfId);
        if (bookshelf == null) {
            bookshelf = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        }
        bookshelf.setAsPreferred();

        if (previousBookshelfId != bookshelf.getId()) {
            currentCenteredBookId = 0;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean reloadSelectedBookshelf(@NonNull final Context context) {
        final Bookshelf newBookshelf =
                Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.ALL_BOOKS);
        if (!newBookshelf.equals(bookshelf)) {
            // if it was.. switch to it.
            bookshelf = newBookshelf;
            return true;
        }
        return false;
    }


    /**
     * Get the style of the current bookshelf.
     *
     * @param context Current context
     *
     * @return style
     */
    @NonNull
    Style getStyle(@NonNull final Context context) {
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);
        return bookshelf.getStyle(context);
    }

    /**
     * Should be called after <strong>a style was changed/selected</strong>.
     * The style should exist (id != 0), or if it doesn't, the default style will be used instead.
     *
     * @param context   Current context
     * @param styleUuid the style to apply
     */
    void onStyleChanged(@NonNull final Context context,
                        @NonNull final String styleUuid) {
        // Always validate first
        final Style style = ServiceLocator.getInstance().getStyles()
                                          .getStyleOrDefault(context, styleUuid);
        Objects.requireNonNull(bookshelf, Bookshelf.TAG);

        // set as the global default.
        ServiceLocator.getInstance().getStyles().setDefault(style.getUuid());
        // save the new bookshelf/style combination
        bookshelf.setAsPreferred();
        bookshelf.setStyle(context, style);
    }

    /**
     * Save current position information in the preferences.
     * We do this to preserve this data across application shutdown/startup.
     *
     * @param context    Current context
     * @param position   adapter list position; i.e. first visible position in the list
     * @param viewOffset offset in pixels for the first visible position in the list
     */
    void saveListPosition(@NonNull final Context context,
                          final int position,
                          final int viewOffset) {
        if (listLoaded) {
            Objects.requireNonNull(bookshelf, Bookshelf.TAG);
            bookshelf.setFirstVisibleItemPosition(context, position, viewOffset);
        }
    }

    /**
     * Check if a rebuild is needed in {@code Activity#onResume()}.
     *
     * @return {@code true} if a rebuild is needed
     */
    boolean isForceRebuildInOnResume() {
        return forceRebuildInOnResume;
    }

    /**
     * Request a rebuild at the next {@code Activity#onResume()}.
     *
     * @param forceRebuild Flag
     */
    void setForceRebuildInOnResume(final boolean forceRebuild) {
        forceRebuildInOnResume = forceRebuild;
    }

    /**
     * Check if the list has (ever) loaded successfully.
     *
     * @return {@code true} if loaded at least once.
     */
    boolean isListLoaded() {
        return listLoaded;
    }

    @IntRange(from = 0)
    public long getCurrentCenteredBookId() {
        return currentCenteredBookId;
    }

    /**
     * Set the <strong>desired</strong> book id to position the list.
     * Pass {@code 0} to disable.
     *
     * @param bookId to use
     */
    void setCurrentCenteredBookId(@IntRange(from = 0) final long bookId) {
        currentCenteredBookId = bookId;
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param rowData with data
     *
     * @return {@code true} if this book is available for lending.
     */
    boolean isAvailable(@NonNull final DataHolder rowData) {
        final String loanee;
        if (rowData.contains(DBKey.LOANEE_NAME)) {
            loanee = rowData.getString(DBKey.LOANEE_NAME);
        } else {
            loanee = ServiceLocator.getInstance().getLoaneeDao().getLoaneeByBookId(
                    rowData.getLong(DBKey.FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
    }

    @NonNull
    SearchCriteria getSearchCriteria() {
        return Objects.requireNonNull(searchCriteria);
    }

    /**
     * This is used to re-display the list in onResume.
     * i.e. {@link #currentCenteredBookId} was set, but a rebuild was not needed.
     *
     * @return the node(s), can be empty, but never {@code null}
     */
    @NonNull
    List<BooklistNode> getTargetNodes() {
        if (currentCenteredBookId != 0) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            return booklist.getVisibleBookNodes(currentCenteredBookId);
        }

        return new ArrayList<>();
    }

    /**
     * Set the desired state on the given node.
     *
     * @param nodeRowId          list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    BooklistNode setNode(final long nodeRowId,
                         @NonNull final BooklistNode.NextState nextState,
                         final int relativeChildLevel) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.setNode(nodeRowId, nextState, relativeChildLevel);
    }

    void expandAllNodes(@IntRange(from = 1) final int topLevel,
                        final boolean expand) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        booklist.setAllNodes(topLevel, expand);
    }


    @NonNull
    Optional<BooklistNode> getNextBookWithoutCover(final long rowId) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getNextBookWithoutCover(rowId);
    }

    @NonNull
    BooklistHeader getHeaderContent(@NonNull final Context context) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        Objects.requireNonNull(bookshelf, "bookshelf");
        return new BooklistHeader(context, getStyle(context),
                                  booklist.countBooks(), booklist.countDistinctBooks(),
                                  bookshelf.getFilters(),
                                  searchCriteria);
    }

    @NonNull
    String getBookNavigationTableName() {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getNavigationTableName();
    }

    /**
     * Wrapper to get the list cursor.
     *
     * @return cursor
     */
    @NonNull
    Cursor getNewListCursor() {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getNewListCursor();
    }

    @NonNull
    List<Author> getAuthorsByBookId(@IntRange(from = 1) final long bookId) {
        return ServiceLocator.getInstance().getAuthorDao().getAuthorsByBookId(bookId);
    }

    @NonNull
    ArrayList<Long> getBookIdsByAuthor(@NonNull final String nodeKey,
                                       final boolean onlyThisShelf,
                                       @IntRange(from = 0) final long authorId) {
        if (onlyThisShelf || authorId == 0) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            return booklist.getBookIdsForNodeKey(nodeKey);
        } else {
            return ServiceLocator.getInstance().getAuthorDao().getBookIds(authorId);
        }
    }

    @NonNull
    ArrayList<Long> getBookIdsBySeries(@NonNull final String nodeKey,
                                       final boolean onlyThisShelf,
                                       @IntRange(from = 0) final long seriesId) {
        if (onlyThisShelf || seriesId == 0) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            return booklist.getBookIdsForNodeKey(nodeKey);
        } else {
            return ServiceLocator.getInstance().getSeriesDao().getBookIds(seriesId);
        }
    }

    @NonNull
    ArrayList<Long> getBookIdsByPublisher(@NonNull final String nodeKey,
                                          final boolean onlyThisShelf,
                                          @IntRange(from = 0) final long publisherId) {
        if (onlyThisShelf || publisherId == 0) {
            Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
            return booklist.getBookIdsForNodeKey(nodeKey);
        } else {
            return ServiceLocator.getInstance().getPublisherDao().getBookIds(publisherId);
        }
    }

    boolean setAuthorComplete(@IntRange(from = 1) final long authorId,
                              final boolean isComplete) {
        return ServiceLocator.getInstance().getAuthorDao().setComplete(authorId, isComplete);
    }

    boolean setSeriesComplete(@IntRange(from = 1) final long seriesId,
                              final boolean isComplete) {
        return ServiceLocator.getInstance().getSeriesDao().setComplete(seriesId, isComplete);
    }

    boolean setBookRead(@IntRange(from = 1) final long bookId,
                        final boolean isRead) {
        return bookDao.setRead(bookId, isRead);
    }

    @NonNull
    Book getBook(@IntRange(from = 1) final long bookId) {
        return Book.from(bookId);
    }

    /**
     * Delete the given Series.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final Series series) {
        return ServiceLocator.getInstance().getSeriesDao().delete(context, series);
    }

    /**
     * Delete the given Publisher.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final Publisher publisher) {
        return ServiceLocator.getInstance().getPublisherDao().delete(context, publisher);
    }

    /**
     * Delete the given Bookshelf.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} on a successful delete
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Bookshelf bookshelf) {
        return ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf);
    }

    /**
     * Delete the given Book.
     *
     * @param bookId to delete
     *
     * @return {@code true} on a successful delete
     */
    boolean deleteBook(@IntRange(from = 1) final long bookId) {
        return bookDao.delete(bookId);
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean lendBook(@IntRange(from = 1) final long bookId,
                     @SuppressWarnings("SameParameterValue") @Nullable final String loanee) {
        return ServiceLocator.getInstance().getLoaneeDao().setLoanee(bookId, loanee);
    }

    @NonNull
    int[] onBookRead(@IntRange(from = 1) final long bookId,
                     final boolean read) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.updateBookRead(bookId, read)
                       .stream()
                       .mapToInt(BooklistNode::getAdapterPosition)
                       .toArray();
    }

    @NonNull
    int[] onBookLend(@IntRange(from = 1) final long bookId,
                     @Nullable final String loanee) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.updateBookLoanee(bookId, loanee)
                       .stream()
                       .mapToInt(BooklistNode::getAdapterPosition)
                       .toArray();
    }


    /**
     * Called when a book/book-list was updated with internet data.
     *
     * @param data returned from the update contract
     */
    void onBookAutoUpdateFinished(@Nullable final UpdateBooksOutput data) {
        if (data != null) {
            if (data.listModified) {
                // we processed a list, just force a rebuild
                forceRebuildInOnResume = true;

            } else if (data.bookModified > 0) {
                //URGENT: we processed a single book, we should NOT do a full rebuild
                forceRebuildInOnResume = true;
            }

            // If we got an reposition id back, make any potential rebuild re-position to it.
            if (data.repositionToBookId > 0) {
                currentCenteredBookId = data.repositionToBookId;
            }
        }
    }


    /**
     * This method is called from an ActivityResultContract after the result intent is parsed.
     *
     * @param data returned from the view/edit contract
     */
    void onBookEditFinished(@Nullable final EditBookOutput data) {
        if (data != null) {
            if (data.modified) {
                forceRebuildInOnResume = true;
            }

            // If we got an reposition id back, make any potential rebuild re-position to it.
            if (data.repositionToBookId > 0) {
                currentCenteredBookId = data.repositionToBookId;
            }
        }
    }

    /**
     * This method is called from an ActivityResultContract after the result intent is parsed.
     *
     * @param data returned from the view/edit contract
     */
    void onEditStylesFinished(@NonNull final Context context,
                              @Nullable final PreferredStylesContract.Output data) {
        if (data != null) {
            // we get the UUID for the selected style back.
            if (data.uuid != null && !data.uuid.isEmpty()) {
                onStyleChanged(context, data.uuid);
            }

            // This is independent from the above style having been modified ot not.
            if (data.modified) {
                forceRebuildInOnResume = true;
            }
        }
    }

    /**
     * This method is called from an ActivityResultContract after the result intent is parsed.
     *
     * @param data returned from the view/edit contract
     */
    void onEditStyleFinished(@NonNull final Context context,
                             @Nullable final EditStyleContract.Output data) {
        if (data != null) {
            // We get here from the StylePickerDialogFragment (i.e. the style menu)
            // when the user choose to EDIT a style.
            if (data.uuid != null && !data.uuid.isEmpty()) {
                onStyleChanged(context, data.uuid);

                // ALWAYS rebuild here, even when the style was not modified
                // as we're handling this as a style-change
                // (we could do checks... but it's not worth the effort.)
                // i.e. same as in mOnStylePickerListener
                forceRebuildInOnResume = true;
            }
        }
    }

    void onManageBookshelvesFinished(@NonNull final Context context,
                                     final long bookshelfId) {
        if (bookshelfId != 0 && bookshelfId != getCurrentBookshelf().getId()) {
            setCurrentBookshelf(context, bookshelfId);
            forceRebuildInOnResume = true;
        }
    }

    void onFtsSearchFinished(@Nullable final SearchCriteria criteria) {
        if (criteria != null) {
            searchCriteria = criteria;
            forceRebuildInOnResume = true;
        }
    }


    /**
     * Called when the user has finished an Import.
     * <p>
     * This method is called from a ActivityResultContract after the result intent is parsed.
     *
     * @param importResults returned from the import
     */
    void onImportFinished(@NonNull final Context context,
                          @Nullable final ImportResults importResults) {
        if (importResults != null) {
            if (importResults.styles > 0) {
                // Force a refresh of the cached styles
                ServiceLocator.getInstance().getStyles().clearCache();
            }
            if (importResults.preferences > 0) {
                // Refresh the preferred bookshelf. This also refreshes its style.
                reloadSelectedBookshelf(context);
            }

            // styles, prefs, books, covers,... it all requires a rebuild.
            forceRebuildInOnResume = true;
        }
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     */
    void buildBookList() {
        Objects.requireNonNull(bookshelf, ERROR_NULL_BOOKLIST);

        //noinspection ConstantConditions
        boBTask.build(bookshelf, rebuildMode, searchCriteria, currentCenteredBookId);
    }

    boolean isBuilding() {
        return boBTask.isRunning();
    }

    void onBuildFinished(@NonNull final BoBTask.Outcome outcome) {
        // the new build is completely done. We can safely discard the previous one.
        if (booklist != null) {
            booklist.close();
        }

        booklist = outcome.getList();

        // Save a flag to say list was loaded at least once successfully
        listLoaded = true;

        // preserve the new state by default
        rebuildMode = RebuildBooklist.FromSaved;
    }

    void onBuildCancelled() {
        // reset the central book id.
        currentCenteredBookId = 0;
    }

    void onBuildFailed() {
        // reset the central book id.
        currentCenteredBookId = 0;
    }

    @NonNull
    public List<BooklistNode> getVisibleBookNodes(final long bookId) {
        Objects.requireNonNull(booklist, ERROR_NULL_BOOKLIST);
        return booklist.getVisibleBookNodes(bookId);
    }

}
