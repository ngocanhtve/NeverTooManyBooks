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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.AddBookToShelfApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.IsbnToIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ReviewEditApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShelfListApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIsbnApiHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * This is a layer between the API handler classes and the Goodreads sync tasks.
 * <p>
 * Keeps a cache of the Goodreads shelves list.
 * <p>
 * <a href="https://www.goodreads.com/api/documentation">api documentation</a>
 */
public class GoodreadsManager {

    public static final String BASE_URL = "https://www.goodreads.com";
    public static final Locale SITE_LOCALE = Locale.US;

    public static final int CONNECTION_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 10_000;

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "GR";

    /** Preferences prefix. */
    public static final String PREF_KEY = "goodreads";

    /** Log tag. */
    private static final String TAG = "GoodreadsManager";
    /** Whether to show any Goodreads sync menus at all. */
    private static final String PK_ENABLED = PREF_KEY + ".enabled";
    /** Whether to collect genre string from the popular bookshelves. */
    private static final String PK_COLLECT_GENRE = PREF_KEY + ".search.collect.genre";
    /** last id we send to Goodreads. */
    private static final String PK_LAST_BOOK_SEND = PREF_KEY + ".last.send.id";
    /** last time we synced with Goodreads. */
    private static final String PK_LAST_SYNC_DATE = PREF_KEY + ".last.sync.date";

    /** A localized context. */
    @NonNull
    private final Context mContext;
    @NonNull
    private final Locale mUserLocale;

    @NonNull
    private final GoodreadsDao mGoodreadsDao;
    @NonNull
    private final BookshelfDao mBookshelfDao;

    /** Authentication handler. */
    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    /** Cache this handler. */
    @Nullable
    private IsbnToIdApiHandler mIsbnToIdApiHandler;
    /** Cache this handler. */
    @Nullable
    private AddBookToShelfApiHandler mAddBookToShelfApiHandler;
    /** Cache this handler. */
    @Nullable
    private ReviewEditApiHandler mReviewEditApiHandler;
    /** Cache this handler. */
    @Nullable
    private ShowBookByIdApiHandler mShowBookByIdApiHandler;
    /** Cache this handler. */
    @Nullable
    private ShowBookByIsbnApiHandler mShowBookByIsbnApiHandler;


    /** Cached list of shelves. */
    @Nullable
    private GoodreadsShelves mShelvesList;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     */
    @WorkerThread
    public GoodreadsManager(@NonNull final Context context,
                            @NonNull final GoodreadsAuth grAuth) {
        mContext = context;
        mUserLocale = mContext.getResources().getConfiguration().getLocales().get(0);

        mGoodreadsAuth = grAuth;
        mGoodreadsDao = ServiceLocator.getInstance().getGoodreadsDao();
        mBookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
    }

    /**
     * Check if SYNC menus should be shown at all. This does not affect searching.
     *
     * @param global Global preferences
     *
     * @return {@code true} if menus should be shown
     */
    public static boolean isSyncEnabled(@NonNull final SharedPreferences global) {
        return global.getBoolean(PK_ENABLED, true);
    }

    public static boolean isCollectGenre() {
        return ServiceLocator.getGlobalPreferences().getBoolean(PK_COLLECT_GENRE, true);
    }

    @Nullable
    public static LocalDateTime getLastSyncDate() {
        return new ISODateParser().parse(
                ServiceLocator.getGlobalPreferences().getString(PK_LAST_SYNC_DATE, null));
    }

    public static void setLastSyncDate(@Nullable final LocalDateTime dateTime) {
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        if (dateTime == null) {
            global.edit().remove(PK_LAST_SYNC_DATE).apply();
        } else {
            global.edit()
                  .putString(PK_LAST_SYNC_DATE,
                             dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .apply();
        }
    }

    public long getLastBookIdSend() {
        return ServiceLocator.getGlobalPreferences().getLong(PK_LAST_BOOK_SEND, 0);
    }

    public void putLastBookIdSend(final long lastBookSend) {
        ServiceLocator.getGlobalPreferences()
                      .edit().putLong(PK_LAST_BOOK_SEND, lastBookSend)
                      .apply();
    }

    /**
     * Get a localized context.
     *
     * @return context
     */
    @NonNull
    public Context getContext() {
        return mContext;
    }

    @NonNull
    public GoodreadsDao getGoodreadsDao() {
        return mGoodreadsDao;
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     * <ul>The bookData bundle has to have:
     *      <li>{@link DBKey#PK_ID}</li>
     *      <li>{@link DBKey#SID_GOODREADS_BOOK}</li>
     *      <li>{@link DBKey#KEY_ISBN}</li>
     *      <li>{@link DBKey#BOOL_READ}</li>
     *      <li>{@link DBKey#DATE_READ_START}</li>
     *      <li>{@link DBKey#DATE_READ_END}</li>
     *      <li>{@link DBKey#KEY_RATING}</li>
     * </ul>
     * <p>
     * See {@link GoodreadsDao#fetchBookForExport}
     *
     * @param bookData with book data to send
     *
     * @return {@link GrStatus.SendBook} encapsulating one of the {@link GrStatus} int codes.
     *
     * @throws IOException on other failures
     */
    @WorkerThread
    public GrStatus.SendBook sendBook(@NonNull final DataHolder bookData)
            throws CredentialsException, IOException, HttpStatusException,
                   DiskFullException, ExternalStorageException {

        final long bookId = bookData.getLong(DBKey.PK_ID);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_SEND) {
            Logger.d(TAG, "sendOneBook", "bookId=" + bookId);
        }

        long grBookId;
        Bundle grBookData = null;

        final boolean[] fetchCovers = {false, false};

        try {
            // See if the book already has a Goodreads id and if it is valid.
            grBookId = bookData.getLong(DBKey.SID_GOODREADS_BOOK);
            if (grBookId > 0) {
                try {
                    // Get the book details to make sure we have a valid book ID
                    grBookData = getBookById(grBookId, fetchCovers, new Bundle());

                } catch (@NonNull final HttpNotFoundException ignore) {
                    grBookId = 0;
                }
            }

            // wasn't there, see if we can find it using the ISBN instead.
            if (grBookId == 0) {
                final String isbnStr = bookData.getString(DBKey.KEY_ISBN);
                if (isbnStr.isEmpty()) {
                    return GrStatus.SendBook.NoIsbn;
                }
                final ISBN isbn = ISBN.createISBN(isbnStr);
                if (!isbn.isValid(true)) {
                    return GrStatus.SendBook.NoIsbn;
                }

                // Get the book details using ISBN
                grBookData = getBookByIsbn(isbn.asText(), fetchCovers, new Bundle());
                grBookId = grBookData.getLong(DBKey.SID_GOODREADS_BOOK);

                // If we got an ID, save it against the book
                if (grBookId > 0) {
                    mGoodreadsDao.setGoodreadsBookId(bookId, grBookId);
                } else {
                    // Still nothing... Give up.
                    return GrStatus.SendBook.NotFound;
                }
            }

            // We found a Goodreads book.
            // Get the review id if we have the book details. For new books, it will not be present.
            //noinspection ConstantConditions
            long reviewId = grBookData.getLong(ShowBookApiHandler.SiteField.REVIEW_ID);

            // Lists of shelf names and our best guess at the Goodreads canonical name
            final Collection<String> shelves = new ArrayList<>();
            final Collection<String> canonicalShelves = new ArrayList<>();

            // Get the list of shelves from Goodreads.
            // This is cached per instance of GoodreadsManager.
            final GoodreadsShelves grShelfList = getShelves();

            // Build the list of shelves for the book that we have in the local database
            int exclusiveCount = 0;
            for (final Bookshelf bookshelf : mBookshelfDao.getBookshelvesByBookId(bookId)) {
                final String bookshelfName = bookshelf.getName();
                shelves.add(bookshelfName);

                final String canonicalShelfName =
                        GoodreadsShelf.canonicalizeName(mUserLocale, bookshelfName);
                canonicalShelves.add(canonicalShelfName);

                // Count how many of these shelves are exclusive in Goodreads.
                if (grShelfList.isExclusive(canonicalShelfName)) {
                    exclusiveCount++;
                }
            }

            // If no exclusive shelves are specified, add a pseudo-shelf to match Goodreads
            // because review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                final String pseudoShelf;
                if (bookData.getInt(DBKey.BOOL_READ) != 0) {
                    pseudoShelf = "Read";
                } else {
                    pseudoShelf = "To Read";
                }
                if (!shelves.contains(pseudoShelf)) {
                    shelves.add(pseudoShelf);
                    canonicalShelves.add(GoodreadsShelf.canonicalizeName(mUserLocale, pseudoShelf));
                }
            }

            // Get the names of the shelves the book is currently on at Goodreads
            List<String> grShelves = null;
            if (grBookData.containsKey(ShowBookApiHandler.SiteField.SHELVES)) {
                grShelves = grBookData.getStringArrayList(ShowBookApiHandler.SiteField.SHELVES);
            }
            // not in grBookData, or failed to get
            if (grShelves == null) {
                grShelves = new ArrayList<>();
            }

            // Remove from any shelves from Goodreads that are not in our local list
            for (final String grShelf : grShelves) {
                if (!canonicalShelves.contains(grShelf)) {
                    try {
                        // Goodreads does not seem to like removing books from the special shelves.
                        if (!(grShelfList.isExclusive(grShelf))) {
                            removeBookFromShelf(grBookId, grShelf);
                        }
                    } catch (@NonNull final HttpNotFoundException ignore) {
                        // Ignore here; probably means the book was not on this shelf anyway
                    }
                }
            }

            // Add shelves to Goodreads if they are not currently there
            final Collection<String> shelvesToAddTo = new ArrayList<>();
            for (final String shelf : shelves) {
                // Get the name the shelf will have at Goodreads
                final String canonicalShelfName = GoodreadsShelf
                        .canonicalizeName(mUserLocale, shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                final boolean okToSend = exclusiveCount < 2
                                         || !grShelfList.isExclusive(canonicalShelfName);

                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    shelvesToAddTo.add(shelf);
                }
            }
            if (!shelvesToAddTo.isEmpty()) {
                reviewId = addBookToShelf(grBookId, shelvesToAddTo);
            }

            // We should be safe always updating here because:
            // - all books that are already added have a review ID,
            //   which we would have got from the bundle
            // - all new books will be added to at least one shelf,
            //   which will have returned a review ID.
            // But, just in case, we check the review ID, and if 0,
            // we add the book to the 'Default' shelf.
            //
            if (reviewId == 0) {
                reviewId = addBookToShelf(grBookId, GoodreadsShelf.DEFAULT_SHELF);
            }

            // Finally update the remaining review details.
            updateReview(reviewId, bookData);

        } catch (@NonNull final HttpNotFoundException | SAXException e) {
            return GrStatus.SendBook.NotFound;
        }

        return GrStatus.SendBook.Success;
    }

    /**
     * Wrapper to update a book (review).
     *
     * <ul>The bookData bundle has to have:
     *      <li>{@link DBKey#BOOL_READ}</li>
     *      <li>{@link DBKey#DATE_READ_START}</li>
     *      <li>{@link DBKey#DATE_READ_END}</li>
     *      <li>{@link DBKey#KEY_RATING}</li>
     * </ul>
     *
     * @param reviewId Goodreads review id to update
     * @param bookData Bundle with the required data for the review.
     */
    private void updateReview(final long reviewId,
                              @NonNull final DataHolder bookData)
            throws CredentialsException, IOException, SAXException {
        if (mReviewEditApiHandler == null) {
            mReviewEditApiHandler = new ReviewEditApiHandler(mContext, mGoodreadsAuth);
        }
        mReviewEditApiHandler.update(reviewId,
                                     bookData.getBoolean(DBKey.BOOL_READ),
                                     bookData.getString(DBKey.DATE_READ_START),
                                     bookData.getString(DBKey.DATE_READ_END),
                                     (int) bookData.getDouble(DBKey.KEY_RATING),
                                     null);
    }

    /**
     * Wrapper to search for a book.
     *
     * @param grBookId    Goodreads book id to get
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return Bundle of Goodreads book data
     *
     * @throws IOException on failures
     */
    @NonNull
    private Bundle getBookById(final long grBookId,
                               @NonNull final boolean[] fetchCovers,
                               @NonNull final Bundle bookData)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException,
                   DiskFullException, ExternalStorageException {

        if (mShowBookByIdApiHandler == null) {
            mShowBookByIdApiHandler = new ShowBookByIdApiHandler(mContext, mGoodreadsAuth);
        }
        return mShowBookByIdApiHandler.searchByExternalId(grBookId, fetchCovers, bookData);
    }

    /**
     * Wrapper to search for a book.
     *
     * @param validIsbn   ISBN to use, must be valid
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return Bundle with Goodreads book data
     *
     * @throws IOException on failures
     */
    @NonNull
    private Bundle getBookByIsbn(@NonNull final String validIsbn,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Bundle bookData)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException,
                   DiskFullException, ExternalStorageException {

        if (mShowBookByIsbnApiHandler == null) {
            mShowBookByIsbnApiHandler = new ShowBookByIsbnApiHandler(mContext, mGoodreadsAuth);
        }
        return mShowBookByIsbnApiHandler.searchByIsbn(validIsbn, fetchCovers, bookData);
    }

    /**
     * Fetch all our shelves from Goodreads.
     * We only fetch them once and cache them.
     *
     * @return the Goodreads shelves
     *
     * @throws IOException on failures
     */
    @NonNull
    private GoodreadsShelves getShelves()
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mShelvesList == null) {
            final ShelfListApiHandler handler = new ShelfListApiHandler(mContext, mGoodreadsAuth);
            mShelvesList = new GoodreadsShelves(handler.getAll());
        }
        return mShelvesList;
    }

    /**
     * Wrapper to add book to shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws IOException on failures
     */
    private long addBookToShelf(final long grBookId,
                                @SuppressWarnings("SameParameterValue")
                                @NonNull final String shelfName)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfName);
    }

    /**
     * Wrapper to add book to a list of shelves.
     *
     * @param grBookId   GoodReads book id
     * @param shelfNames list of GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws IOException on failures
     */
    private long addBookToShelf(final long grBookId,
                                @NonNull final Collection<String> shelfNames)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfNames);
    }

    /**
     * Wrapper to remove a book from a shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @throws IOException on failures
     */
    private void removeBookFromShelf(final long grBookId,
                                     @NonNull final String shelfName)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        mAddBookToShelfApiHandler.remove(grBookId, shelfName);
    }

    /**
     * Wrapper to call ISBN->ID.
     *
     * @param isbn to search for
     *
     * @return Goodreads book ID
     *
     * @throws IOException on failures
     */
    @SuppressWarnings("unused")
    private long isbnToId(@NonNull final String isbn)
            throws CredentialsException, IOException,
                   HttpNotFoundException, HttpStatusException {

        if (mIsbnToIdApiHandler == null) {
            mIsbnToIdApiHandler = new IsbnToIdApiHandler(mContext, mGoodreadsAuth);
        }
        return mIsbnToIdApiHandler.isbnToId(isbn);
    }

}
