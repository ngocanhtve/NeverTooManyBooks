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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.AUTHOR_TYPE__BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_CONDITION_COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_PUBLICATION__DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.CALIBRE_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.CALIBRE_BOOK_MAIN_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.CALIBRE_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.DATE_ADDED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.DATE_LAST_UPDATED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.EDITION__BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FIRST_PUBLICATION__DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.LOANEE_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PAGE_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PERSONAL_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PRINT_RUN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.READ_END__DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.READ_START__DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.READ__BOOL;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.SERIES_BOOK_NUMBER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.SID_STRIP_INFO;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.SIGNED__BOOL;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.STRIP_INFO_AMOUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.STRIP_INFO_COLL_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.STRIP_INFO_OWNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.STRIP_INFO_WANTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.TOC_TYPE__BITMASK;

/**
 * Database access helper class.
 * <p>
 * insert/update of a Book failures are handled with {@link DaoWriteException}
 * which makes the deep nesting of calls easier to handle.
 * <p>
 * All others follow the pattern of:
 * insert: return new id, or {@code -1} for error.
 * update: return rows affected, can be 0; or boolean when appropriate.
 * <p>
 * Individual deletes return boolean (i.e. 0 or 1 row affected)
 * Multi-deletes return either void, or the number of rows deleted.
 * <p>
 * TODO: some places ignore insert/update failures. A storage full could trigger a failure.
 */
public class BookDaoImpl
        extends BaseDaoImpl
        implements BookDao {

    /** Log tag. */
    private static final String TAG = "BookDaoImpl";

    /** log error string. */
    private static final String ERROR_CREATING_BOOK_FROM = "Failed creating book from\n";
    /** log error string. */
    private static final String ERROR_UPDATING_BOOK_FROM = "Failed updating book from\n";
    /** log error string. */
    private static final String ERROR_STORING_COVERS = "Failed storing the covers for book from\n";

    @NonNull
    private final DateParser dateParser;

    /**
     * Constructor.
     */
    public BookDaoImpl() {
        super(TAG);
        dateParser = new ISODateParser();
    }

    @Override
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    public long insert(@NonNull final Context context,
                       @NonNull final Book /* in/out */ book,
                       @BookFlags final int flags)
            throws StorageException,
                   DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final BookDaoHelper bookDaoHelper = new BookDaoHelper(context, book, true);
            final ContentValues cv = bookDaoHelper
                    .process(context)
                    .filterValues(mDb.getTableInfo(TBL_BOOKS));

            // Make sure we have at least one author
            final List<Author> authors = book.getAuthors();
            if (authors.isEmpty()) {
                throw new DaoWriteException("No authors for book=" + book);
            }

            final String addedOrUpdatedNow = SqlEncode.date(LocalDateTime.now(ZoneOffset.UTC));

            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(DATE_ADDED__UTC)) {
                cv.put(DATE_ADDED__UTC, addedOrUpdatedNow);
            }
            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(DATE_LAST_UPDATED__UTC)) {
                cv.put(DATE_LAST_UPDATED__UTC, addedOrUpdatedNow);
            }

            // if allowed, and we have an id, use it.
            if ((flags & BOOK_FLAG_USE_ID_IF_PRESENT) != 0 && book.getId() > 0) {
                cv.put(PK_ID, book.getId());
            } else {
                // in all other circumstances, make absolutely sure we DO NOT pass in an id.
                cv.remove(PK_ID);
            }

            // go!
            final long newBookId = mDb.insert(TBL_BOOKS.getName(), cv);
            if (newBookId <= 0) {
                Logger.error(TAG, new Throwable(), "Insert failed"
                                                   + "|table=" + TBL_BOOKS.getName()
                                                   + "|cv=" + cv);

                book.putLong(PK_ID, 0);
                book.remove(BOOK_UUID);
                throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book);
            }

            // set the new id/uuid on the Book itself
            book.putLong(PK_ID, newBookId);
            // always lookup the UUID
            // (even if we inserted with a uuid... to protect against future changes)
            final String uuid = getBookUuid(newBookId);
            SanityCheck.requireValue(uuid, "uuid");
            book.putString(BOOK_UUID, uuid);

            // next we add the links to series, authors,...
            insertBookLinks(context, book, flags);

            // and populate the search suggestions table
            ServiceLocator.getInstance().getFtsDao().insert(newBookId);

            // lastly we move the covers from the cache dir to their permanent dir/name
            try {
                bookDaoHelper.persistCovers();

            } catch (@NonNull final StorageException e) {
                book.putLong(PK_ID, 0);
                book.remove(BOOK_UUID);
                throw e;

            } catch (@NonNull final IOException e) {
                book.putLong(PK_ID, 0);
                book.remove(BOOK_UUID);
                throw new DaoWriteException(ERROR_STORING_COVERS + book, e);
            }

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
            return newBookId;

        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @BookFlags final int flags)
            throws StorageException,
                   DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final BookDaoHelper bookDaoHelper = new BookDaoHelper(context, book, false);
            final ContentValues cv = bookDaoHelper
                    .process(context)
                    .filterValues(mDb.getTableInfo(TBL_BOOKS));

            // Disallow UUID updates
            if (cv.containsKey(BOOK_UUID)) {
                cv.remove(BOOK_UUID);
            }

            // set the KEY_DATE_LAST_UPDATED to 'now' if we're allowed,
            // or if it's not already present.
            if ((flags & BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT) == 0
                || !cv.containsKey(DATE_LAST_UPDATED__UTC)) {
                cv.put(DATE_LAST_UPDATED__UTC, SqlEncode
                        .date(LocalDateTime.now(ZoneOffset.UTC)));
            }

            // Reminder: We're updating ONLY the fields present in the ContentValues.
            // Other fields in the database row are not affected.
            // go !
            final boolean success =
                    0 < mDb.update(TBL_BOOKS.getName(), cv, PK_ID + "=?",
                                   new String[]{String.valueOf(book.getId())});

            if (success) {
                // always lookup the UUID
                final String uuid = getBookUuid(book.getId());
                SanityCheck.requireValue(uuid, "uuid");
                book.putString(BOOK_UUID, uuid);

                insertBookLinks(context, book, flags);

                ServiceLocator.getInstance().getFtsDao().update(book.getId());

                try {
                    bookDaoHelper.persistCovers();

                } catch (@NonNull final IOException e) {
                    throw new DaoWriteException(ERROR_STORING_COVERS + book);
                }

                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } else {
                throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book);
            }
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        final boolean success = delete(book.getId());
        if (success) {
            book.remove(DBKey.PK_ID);
            book.remove(BOOK_UUID);
        }
        return success;
    }

    @Override
    public boolean delete(@NonNull final BookLight bookLight) {
        final boolean success = delete(bookLight.getId());
        if (success) {
            bookLight.setId(0);
        }
        return success;
    }

    @Override
    public boolean delete(@IntRange(from = 1) final long id) {

        final String uuid = getBookUuid(id);
        // sanity check
        if (uuid == null) {
            return false;
        }

        int rowsAffected = 0;
        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Delete.BOOK_BY_ID)) {
                stmt.bindLong(1, id);
                rowsAffected = stmt.executeUpdateDelete();
            }

            if (rowsAffected > 0) {
                // sanity check
                if (!uuid.isEmpty()) {
                    // Delete the covers from the file system.
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        Book.getPersistedCoverFile(uuid, cIdx).ifPresent(FileUtils::delete);
                    }
                    // and from the cache. If the user flipped the cache on/off we'll
                    // not always be cleaning up correctly. It's not that important though.
                    if (ImageUtils.isImageCachingEnabled()) {
                        ServiceLocator.getInstance().getCoverCacheDao().delete(uuid);
                    }
                }
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(TAG, e, "Failed to delete book");

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        return rowsAffected == 1;
    }

    /**
     * Called during book insert & update.
     * Each step in this method will first delete all entries in the Book-[tableX] table
     * for this bookId, and then insert the new links.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @throws DaoWriteException on failure
     */
    private void insertBookLinks(@NonNull final Context context,
                                 @NonNull final Book book,
                                 final int flags)
            throws DaoWriteException {

        // Only lookup locales when we're NOT in batch mode (i.e. NOT doing an import)
        final boolean lookupLocale = (flags & BOOK_FLAG_IS_BATCH_OPERATION) == 0;

        // unconditional lookup of the book locale!
        final Locale bookLocale = book.getLocale(context);

        if (book.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Bookshelves will be inserted if new, but not updated
            insertBookBookshelf(context, book.getId(), book.getBookshelves());
        }

        if (book.contains(Book.BKEY_AUTHOR_LIST)) {
            // Authors will be inserted if new, but not updated
            insertAuthors(context,
                          book.getId(),
                          book.getAuthors(),
                          lookupLocale,
                          bookLocale);
        }

        if (book.contains(Book.BKEY_SERIES_LIST)) {
            // Series will be inserted if new, but not updated
            insertSeries(context,
                         book.getId(),
                         book.getSeries(),
                         lookupLocale,
                         bookLocale);
        }

        if (book.contains(Book.BKEY_PUBLISHER_LIST)) {
            // Publishers will be inserted if new, but not updated
            insertPublishers(context,
                             book.getId(),
                             book.getPublishers(),
                             lookupLocale,
                             bookLocale);
        }

        if (book.contains(Book.BKEY_TOC_LIST)) {
            // TOC entries are two steps away; they can exist in other books
            // Hence we will both insert new entries
            // AND update existing ones if needed.
            insertOrUpdateToc(context,
                              book.getId(),
                              book.getToc(),
                              lookupLocale,
                              bookLocale);
        }

        if (book.contains(LOANEE_NAME)) {
            ServiceLocator.getInstance().getLoaneeDao()
                          .setLoanee(book, book.getString(LOANEE_NAME));
        }

        if (book.contains(CALIBRE_BOOK_UUID)) {
            // Calibre libraries will be inserted if new, but not updated
            ServiceLocator.getInstance().getCalibreDao().updateOrInsert(book);
        }

        if (book.contains(SID_STRIP_INFO)) {
            ServiceLocator.getInstance().getStripInfoDao().updateOrInsert(book);
        }
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * The list is pruned before storage.
     * New shelves are added, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  of the book
     * @param list    the list of bookshelves
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void insertBookBookshelf(@NonNull final Context context,
                                     @IntRange(from = 1) final long bookId,
                                     @NonNull final Collection<Bookshelf> list)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

        // fix id's and remove duplicates; shelves don't use a Locale, hence no lookup done.
        bookshelfDao.pruneList(list);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookBookshelfByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }


        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_BOOKSHELF)) {
            for (final Bookshelf bookshelf : list) {
                // create if needed - do NOT do updates here
                if (bookshelf.getId() == 0) {
                    if (bookshelfDao.insert(context, bookshelf) == -1) {
                        throw new DaoWriteException("insert Bookshelf");
                    }
                }

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Bookshelf");
                }
            }
        }
    }

    /**
     * Delete the link between Bookshelves and the given Book.
     * Note that the actual Bookshelves are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookBookshelfByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(Sql.Delete.BOOK_BOOKSHELF_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void insertAuthors(@NonNull final Context context,
                              @IntRange(from = 1) final long bookId,
                              @NonNull final Collection<Author> list,
                              final boolean lookupLocale,
                              @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        // fix id's and remove duplicates
        authorDao.pruneList(context, list, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookAuthorByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        int position = 0;
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_AUTHOR)) {
            for (final Author author : list) {
                // create if needed - do NOT do updates here
                if (author.getId() == 0) {
                    if (authorDao.insert(context, author) == -1) {
                        throw new DaoWriteException("insert Author");
                    }
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, author.getId());
                stmt.bindLong(3, position);
                stmt.bindLong(4, author.getType());
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Author");
                }
            }
        }
    }

    /**
     * Delete the link between Authors and the given Book.
     * Note that the actual Authors are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookAuthorByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Delete.BOOK_AUTHOR_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void insertSeries(@NonNull final Context context,
                             @IntRange(from = 1) final long bookId,
                             @NonNull final Collection<Series> list,
                             final boolean lookupLocale,
                             @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();

        // fix id's and remove duplicates
        seriesDao.pruneList(context, list, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookSeriesByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }


        int position = 0;
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_SERIES)) {
            for (final Series series : list) {
                // create if needed - do NOT do updates here
                if (series.getId() == 0) {
                    if (seriesDao.insert(context, series, bookLocale) == -1) {
                        throw new DaoWriteException("insert Series");
                    }
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, series.getId());
                stmt.bindString(3, series.getNumber());
                stmt.bindLong(4, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Series");
                }
            }
        }
    }

    /**
     * Delete the link between Series and the given Book.
     * Note that the actual Series are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookSeriesByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Delete.BOOK_SERIES_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void insertPublishers(@NonNull final Context context,
                                 @IntRange(from = 1) final long bookId,
                                 @NonNull final Collection<Publisher> list,
                                 final boolean lookupLocale,
                                 @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();

        // fix id's and remove duplicates
        publisherDao.pruneList(context, list, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookPublishersByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }


        int position = 0;
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_PUBLISHER)) {
            for (final Publisher publisher : list) {
                // create if needed - do NOT do updates here
                if (publisher.getId() == 0) {
                    if (publisherDao.insert(context, publisher, bookLocale) == -1) {
                        throw new DaoWriteException("insert Publisher");
                    }
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, publisher.getId());
                stmt.bindLong(3, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Publisher");
                }
            }
        }
    }

    /**
     * Delete the link between Publisher and the given Book.
     * Note that the actual Publisher are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookPublishersByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(Sql.Delete.BOOK_PUBLISHER_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void insertOrUpdateToc(@NonNull final Context context,
                                  @IntRange(from = 1) final long bookId,
                                  @NonNull final Collection<TocEntry> list,
                                  final boolean lookupLocale,
                                  @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates
        final TocEntryDao tocEntryDao = ServiceLocator.getInstance().getTocEntryDao();
        tocEntryDao.pruneList(context, list, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookTocEntryByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_TOC_ENTRY);
             SynchronizedStatement stmtInsToc = mDb.compileStatement(Sql.Insert.TOC_ENTRY);
             SynchronizedStatement stmtUpdToc = mDb.compileStatement(Sql.Update.TOCENTRY)) {

            long position = 0;
            for (final TocEntry tocEntry : list) {
                // Author must be handled separately; the id will already have been 'fixed'
                // during the TocEntry.pruneList call.
                // Create if needed - do NOT do updates here
                final Author author = tocEntry.getPrimaryAuthor();
                if (author.getId() == 0) {
                    if (authorDao.insert(context, author) == -1) {
                        throw new DaoWriteException("insert Author");
                    }
                }

                final OrderByHelper.OrderByData obd;
                if (lookupLocale) {
                    obd = OrderByHelper.createOrderByData(context, tocEntry.getTitle(),
                                                          bookLocale, tocEntry::getLocale);
                } else {
                    obd = OrderByHelper.createOrderByData(context, tocEntry.getTitle(),
                                                          bookLocale, null);
                }

                if (tocEntry.getId() == 0) {
                    stmtInsToc.bindLong(1, tocEntry.getPrimaryAuthor().getId());
                    stmtInsToc.bindString(2, tocEntry.getTitle());
                    stmtInsToc.bindString(3, SqlEncode.orderByColumn(obd.title, obd.locale));
                    stmtInsToc.bindString(4, tocEntry
                            .getFirstPublicationDate().getIsoString());

                    final long iId = stmtInsToc.executeInsert();
                    if (iId > 0) {
                        tocEntry.setId(iId);
                    } else {
                        throw new DaoWriteException("insert TocEntry");
                    }

                } else {
                    // We cannot update the author as it's part of the primary key.
                    // (we should never even get here if the author was changed)
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (stmtUpdToc) {
                        stmtUpdToc.bindString(1, tocEntry.getTitle());
                        stmtUpdToc.bindString(2, SqlEncode
                                .orderByColumn(obd.title, obd.locale));
                        stmtUpdToc.bindString(3, tocEntry.getFirstPublicationDate()
                                                         .getIsoString());
                        stmtUpdToc.bindLong(4, tocEntry.getId());
                        if (stmtUpdToc.executeUpdateDelete() != 1) {
                            throw new DaoWriteException("update TocEntry");
                        }
                    }
                }

                // create the book<->TocEntry link.
                //
                // As we delete all links before insert/updating above, we normally
                // *always* need to re-create the link here.
                // However, this will fail if we inserted "The Universe" and updated "Universe, The"
                // as the former will be stored as "Universe, The" so conflicting with the latter.
                // We tried to mitigate this conflict before it could trigger an issue here, but it
                // complicated the code and frankly ended in a chain of special condition
                // code branches during processing of internet search data.
                // So... let's just catch the SQL constraint exception and ignore it.
                // (do not use the sql 'REPLACE' command! We want to keep the original position)
                try {
                    position++;
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (stmt) {
                        stmt.bindLong(1, tocEntry.getId());
                        stmt.bindLong(2, bookId);
                        stmt.bindLong(3, position);
                        if (stmt.executeInsert() == -1) {
                            throw new DaoWriteException("insert Book-TocEntry");
                        }
                    }
                } catch (@NonNull final SQLiteConstraintException ignore) {
                    // ignore and reset the position counter.
                    position--;

                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "updateOrInsertTOC"
                                   + "|SQLiteConstraintException"
                                   + "|tocEntry=" + tocEntry.getId()
                                   + "|bookId=" + bookId);
                    }
                }
            }
        }
    }

    /**
     * Delete the link between {@link TocEntry}'s and the given Book.
     * Note that the actual {@link TocEntry}'s are NOT deleted here.
     *
     * @param bookId id of the book
     */
    private void deleteBookTocEntryByBookId(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(Sql.Delete.BOOK_TOC_ENTRIES_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public boolean setRead(@IntRange(from = 1) final long id,
                           final boolean isRead) {
        final String now = isRead ? SqlEncode.date(LocalDateTime.now()) : "";

        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Update.READ)) {
            stmt.bindBoolean(1, isRead);
            stmt.bindString(2, now);
            stmt.bindLong(3, id);
            return 0 < stmt.executeUpdateDelete();
        }
    }

    @Override
    public boolean setRead(@NonNull final Book book,
                           final boolean isRead) {
        final String now = isRead ? SqlEncode.date(LocalDateTime.now()) : "";

        final boolean success;
        // don't call standalone method, we want to use the same 'now' to update the book
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Update.READ)) {
            stmt.bindBoolean(1, isRead);
            stmt.bindString(2, now);
            stmt.bindLong(3, book.getId());
            success = 0 < stmt.executeUpdateDelete();
        }

        if (success) {
            book.putBoolean(READ__BOOL, isRead);
            book.putString(READ_END__DATE, now);
            book.putString(DATE_LAST_UPDATED__UTC, now);
        }

        return success;
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Count.BOOKS)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return an Cursor with all Books selected by the passed arguments.
     *
     * @param whereClause   without the 'where' keyword, can be {@code null} or {@code ""}
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param orderByClause without the 'order by' keyword, can be {@code null} or {@code ""}
     *
     * @return A Book Cursor with 0..1 row
     */
    private TypedCursor getBookCursor(@Nullable final CharSequence whereClause,
                                      @Nullable final String[] selectionArgs,
                                      @Nullable final CharSequence orderByClause) {

        final String sql = Sql.Select.SQL_BOOK
                           + (whereClause != null && whereClause.length() > 0
                              ? _WHERE_ + whereClause : "")
                           + (orderByClause != null && orderByClause.length() > 0
                              ? _ORDER_BY_ + orderByClause : "")

                           + _COLLATION;

        final TypedCursor cursor = mDb.rawQueryWithTypedCursor(sql, selectionArgs, null);
        // force the TypedCursor to retrieve the real column types.
        cursor.setDb(mDb, TBL_BOOKS);
        return cursor;
    }

    @Override
    @NonNull
    public TypedCursor fetchById(@IntRange(from = 1) final long id) {
        return getBookCursor(TBL_BOOKS.dot(PK_ID) + "=?",
                             new String[]{String.valueOf(id)},
                             null);
    }

    @Override
    @NonNull
    public TypedCursor fetchByKey(@NonNull final String key,
                                  @NonNull final String externalId) {
        return getBookCursor(TBL_BOOKS.dot(key) + "=?", new String[]{externalId},
                             TBL_BOOKS.dot(PK_ID));
    }

    @Override
    @NonNull
    public TypedCursor fetchById(@NonNull final List<Long> idList) {
        SanityCheck.requireValue(idList, "idList");

        if (idList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(PK_ID) + "=?",
                                 new String[]{String.valueOf(idList.get(0))},
                                 null);

        } else {
            return getBookCursor(TBL_BOOKS.dot(PK_ID)
                                 + " IN (" + TextUtils.join(",", idList) + ')',
                                 null,
                                 TBL_BOOKS.dot(PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchFromIdOnwards(final long id) {
        return getBookCursor(TBL_BOOKS.dot(PK_ID) + ">=?",
                             new String[]{String.valueOf(id)},
                             TBL_BOOKS.dot(PK_ID));
    }

    @Override
    public int countBooksForExport(@Nullable final LocalDateTime since) {
        if (since == null) {
            try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Count.BOOKS)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } else {
            try (SynchronizedStatement stmt = mDb.compileStatement(
                    Sql.Count.BOOKS + _WHERE_ + DATE_LAST_UPDATED__UTC + ">=?")) {
                stmt.bindString(1, SqlEncode.date(since));
                return (int) stmt.simpleQueryForLongOrZero();
            }
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExport(@Nullable final LocalDateTime since) {
        if (since == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(PK_ID));
        } else {
            return getBookCursor(TBL_BOOKS.dot(DATE_LAST_UPDATED__UTC) + ">=?",
                                 new String[]{SqlEncode.date(since)},
                                 TBL_BOOKS.dot(PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExportToCalibre(final long libraryId,
                                                    @Nullable final LocalDateTime since) {
        if (since == null) {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(FK_CALIBRE_LIBRARY) + "=?",
                                 new String[]{String.valueOf(libraryId)},
                                 TBL_BOOKS.dot(PK_ID));
        } else {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(FK_CALIBRE_LIBRARY) + "=?"
                                 + _AND_ + TBL_BOOKS.dot(DATE_LAST_UPDATED__UTC) + ">=?",
                                 new String[]{String.valueOf(libraryId), SqlEncode.date(since)},
                                 TBL_BOOKS.dot(PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchBooksForExportToStripInfo(@Nullable final LocalDateTime since) {
        if (since == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(PK_ID));
        } else {
            return getBookCursor(TBL_STRIPINFO_COLLECTION.dot(
                                         STRIP_INFO_LAST_SYNC_DATE__UTC) + ">=?",
                                 new String[]{SqlEncode.date(since)},
                                 TBL_BOOKS.dot(PK_ID));
        }
    }

    @Override
    @NonNull
    public TypedCursor fetchByIsbn(@NonNull final List<String> isbnList) {
        SanityCheck.requireValue(isbnList, "isbnList");

        if (isbnList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(BOOK_ISBN) + "=?",
                                 new String[]{SqlEncode.string(isbnList.get(0))},
                                 null);
        } else {
            return getBookCursor(TBL_BOOKS.dot(BOOK_ISBN)
                                 + " IN ("
                                 + isbnList.stream()
                                           .map(s -> '\'' + SqlEncode.string(s) + '\'')
                                           .collect(Collectors.joining(","))
                                 + ')',
                                 null,
                                 TBL_BOOKS.dot(PK_ID));
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getBookUuidList() {
        return getColumnAsStringArrayList(Sql.Select.ALL_BOOK_UUID);
    }

    /**
     * Return the book UUID based on the id.
     *
     * @param bookId of the book
     *
     * @return the book UUID, or {@code null} if not found/failure
     */
    @Nullable
    private String getBookUuid(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Get.BOOK_UUID_BY_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    @Override
    @IntRange(from = 0)
    public long getBookIdByUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Get.BOOK_ID_BY_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public Pair<String, String> getBookTitleAndIsbnById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = mDb.rawQuery(Sql.Get.BOOK_TITLE_AND_ISBN_BY_BOOK_ID,
                                          new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Pair<>(cursor.getString(0),
                                  cursor.getString(1));
            } else {
                return new Pair<>(null, null);
            }
        }
    }

    @Override
    @NonNull
    public ArrayList<Pair<Long, String>> getBookIdAndTitleByIsbn(@NonNull final ISBN isbn) {
        final ArrayList<Pair<Long, String>> list = new ArrayList<>();
        // if the string is ISBN-10 compatible,
        // i.e. an actual ISBN-10, or an ISBN-13 in the 978 range,
        // we search on both formats
        if (isbn.isIsbn10Compat()) {
            try (Cursor cursor = mDb.rawQuery(Sql.Select.BY_VALID_ISBN,
                                              new String[]{isbn.asText(ISBN.Type.Isbn10),
                                                           isbn.asText(ISBN.Type.Isbn13)})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            try (Cursor cursor = mDb.rawQuery(Sql.Select.BY_ISBN, new String[]{isbn.asText()})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        }

        return list;
    }

    @Override
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean bookExistsById(@IntRange(from = 1) final long id) {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Count.BOOK_EXISTS)) {
            stmt.bindLong(1, id);
            return stmt.simpleQueryForLongOrZero() == 1;
        }
    }

    @Override
    public boolean bookExistsByIsbn(@NonNull final String isbnStr) {
        final ISBN isbn = new ISBN(isbnStr, false);
        return !getBookIdAndTitleByIsbn(isbn).isEmpty();
    }

    @Override
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String key) {
        if (!DBKey.MONEY_KEYS.contains(key)) {
            throw new IllegalArgumentException(key);
        }

        final String sql = "SELECT DISTINCT UPPER(" + key + DBKey.SUFFIX_KEY_CURRENCY
                           + ") FROM " + TBL_BOOKS.getName()
                           + _ORDER_BY_ + key + DBKey.SUFFIX_KEY_CURRENCY + _COLLATION;

        final ArrayList<String> list = getColumnAsStringArrayList(sql);
        if (list.isEmpty()) {
            // sure, this is very crude and discriminating.
            // But it will only ever be used *once* per currency column
            list.add(Money.EUR);
            list.add(Money.GBP);
            list.add(Money.USD);
        }
        return list;
    }

    @Nullable
    public LocalDateTime getLastUpdateDate(@IntRange(from = 1) final long id) {
        try (SynchronizedStatement stmt =
                     mDb.compileStatement(Sql.Get.LAST_UPDATE_DATE_BY_BOOK_ID)) {
            stmt.bindLong(1, id);
            return dateParser.parse(stmt.simpleQueryForStringOrNull());
        }
    }

    @SuppressWarnings("UtilityClassWithoutPrivateConstructor")
    private static class Sql {

        /**
         * Count/exist statements.
         */
        static final class Count {

            /** Count all {@link Book}'s. */
            static final String BOOKS =
                    SELECT_COUNT_FROM_ + TBL_BOOKS.getName();

            /** Check if a {@link Book} exists. */
            static final String BOOK_EXISTS =
                    SELECT_COUNT_FROM_ + TBL_BOOKS.getName() + _WHERE_ + PK_ID + "=?";
        }

        /**
         * Sql SELECT to lookup a single item.
         */
        static final class Get {

            /** Get the UUID of a {@link Book} by the Book id. */
            static final String BOOK_UUID_BY_ID =
                    SELECT_ + BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + PK_ID + "=?";

            /** Get the title and ISBN of a {@link Book} by the Book id. */
            static final String BOOK_TITLE_AND_ISBN_BY_BOOK_ID =
                    SELECT_ + TITLE + ',' + BOOK_ISBN
                    + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + PK_ID + "=?";

            /** Get the last-update-date for a {@link Book} by its id. */
            static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                    SELECT_ + DATE_LAST_UPDATED__UTC + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + PK_ID + "=?";

            /** Get the id of a {@link Book} by UUID. */
            static final String BOOK_ID_BY_UUID =
                    SELECT_ + PK_ID + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + BOOK_UUID + "=?";
        }

        /**
         * Sql SELECT returning a list, with a WHERE clause.
         */
        static final class Select {

            /** Find the {@link Book} id+title based on a search for the ISBN (both 10 & 13). */
            static final String BY_VALID_ISBN =
                    SELECT_ + PK_ID + ',' + TITLE + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + BOOK_ISBN + " LIKE ? OR " + BOOK_ISBN + " LIKE ?";

            /**
             * Find the {@link Book} id+title based on a search for the ISBN.
             * The isbn need not be valid and can in fact be any code whatsoever.
             */
            static final String BY_ISBN =
                    SELECT_ + PK_ID + ',' + TITLE + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + BOOK_ISBN + " LIKE ?";

            /** Book UUID only, for accessing all cover image files. */
            static final String ALL_BOOK_UUID =
                    SELECT_ + BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

            /** The SELECT and FROM clause for getting a book (list). */
            static final String SQL_BOOK;

            static {
                // Developer: adding fields ? Now is a good time to update {@link Book#duplicate}/
                // Note we could use TBL_BOOKS.dot("*")
                // We'd fetch the unneeded TITLE_OB field, but that would be ok.
                // Nevertheless, listing the fields here gives a better understanding

                SQL_BOOK = SELECT_ + TBL_BOOKS.dotAs(
                        PK_ID, BOOK_UUID, TITLE, BOOK_ISBN, TOC_TYPE__BITMASK,
                        BOOK_PUBLICATION__DATE, PRINT_RUN,
                        PRICE_LISTED, PRICE_LISTED_CURRENCY,
                        FIRST_PUBLICATION__DATE,
                        FORMAT, COLOR, GENRE, LANGUAGE, PAGE_COUNT,
                        // Main/public description about the content/publication
                        DESCRIPTION,
                        // partially edition info, partially user-owned info.
                        EDITION__BITMASK,
                        // user notes
                        PERSONAL_NOTES, BOOK_CONDITION, BOOK_CONDITION_COVER,
                        LOCATION, SIGNED__BOOL, RATING,
                        READ__BOOL, READ_START__DATE, READ_END__DATE,
                        DATE_ACQUIRED,
                        PRICE_PAID, PRICE_PAID_CURRENCY,
                        // added/updated
                        DATE_ADDED__UTC, DATE_LAST_UPDATED__UTC
                        //NEWTHINGS: adding a new search engine: optional: add engine specific keys
                                                    )

                           + ',' + TBL_BOOKS.dotAs(
                        SearchEngineRegistry.getInstance().getExternalIdDomains())

                           // COALESCE nulls to "" for the LEFT OUTER JOINed LOANEE name
                           + ",COALESCE(" + TBL_BOOK_LOANEE.dot(LOANEE_NAME) + ", '')"
                           + _AS_ + LOANEE_NAME

                           //FIXME: we should not join with all tables we MIGHT need

                           // LEFT OUTER JOIN, columns default to NULL
                           + ','
                           + TBL_CALIBRE_BOOKS
                                   .dotAs(CALIBRE_BOOK_ID,
                                          CALIBRE_BOOK_UUID,
                                          CALIBRE_BOOK_MAIN_FORMAT,
                                          FK_CALIBRE_LIBRARY)

                           // LEFT OUTER JOIN, columns default to NULL
                           + ','
                           + TBL_STRIPINFO_COLLECTION
                                   .dotAs(STRIP_INFO_COLL_ID,
                                          STRIP_INFO_OWNED,
                                          STRIP_INFO_WANTED,
                                          STRIP_INFO_AMOUNT,
                                          STRIP_INFO_LAST_SYNC_DATE__UTC)

                           + _FROM_ + TBL_BOOKS.ref()
                           + TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE)
                           + TBL_BOOKS.leftOuterJoin(TBL_CALIBRE_BOOKS)
                           + TBL_BOOKS.leftOuterJoin(TBL_STRIPINFO_COLLECTION);
            }
        }

        /**
         * Sql INSERT.
         */
        static final class Insert {

            static final String TOC_ENTRY =
                    INSERT_INTO_ + TBL_TOC_ENTRIES.getName()
                    + '(' + FK_AUTHOR
                    + ',' + TITLE
                    + ',' + KEY_TITLE_OB
                    + ',' + FIRST_PUBLICATION__DATE
                    + ") VALUES (?,?,?,?)";
            static final String BOOK_TOC_ENTRY =
                    INSERT_INTO_ + TBL_BOOK_TOC_ENTRIES.getName()
                    + '(' + FK_TOC_ENTRY
                    + ',' + FK_BOOK
                    + ',' + KEY_BOOK_TOC_ENTRY_POSITION
                    + ") VALUES (?,?,?)";
            static final String BOOK_BOOKSHELF =
                    INSERT_INTO_ + TBL_BOOK_BOOKSHELF.getName()
                    + '(' + FK_BOOK
                    + ',' + FK_BOOKSHELF
                    + ") VALUES (?,?)";
            static final String BOOK_AUTHOR =
                    INSERT_INTO_ + TBL_BOOK_AUTHOR.getName()
                    + '(' + FK_BOOK
                    + ',' + FK_AUTHOR
                    + ',' + BOOK_AUTHOR_POSITION
                    + ',' + AUTHOR_TYPE__BITMASK
                    + ") VALUES(?,?,?,?)";
            static final String BOOK_SERIES =
                    INSERT_INTO_ + TBL_BOOK_SERIES.getName()
                    + '(' + FK_BOOK
                    + ',' + FK_SERIES
                    + ',' + SERIES_BOOK_NUMBER
                    + ',' + BOOK_SERIES_POSITION
                    + ") VALUES(?,?,?,?)";
            static final String BOOK_PUBLISHER =
                    INSERT_INTO_ + TBL_BOOK_PUBLISHER.getName()
                    + '(' + FK_BOOK
                    + ',' + FK_PUBLISHER
                    + ',' + BOOK_PUBLISHER_POSITION
                    + ") VALUES(?,?,?)";
        }

        /**
         * Sql UPDATE.
         */
        static final class Update {

            /** Update a single {@link TocEntry}. */
            static final String TOCENTRY =
                    UPDATE_ + TBL_TOC_ENTRIES.getName()
                    + _SET_ + TITLE + "=?"
                    + ',' + KEY_TITLE_OB + "=?"
                    + ',' + FIRST_PUBLICATION__DATE + "=?"
                    + _WHERE_ + PK_ID + "=?";

            /** Update a single Book's read status and read_end date. */
            static final String READ =
                    UPDATE_ + TBL_BOOKS.getName()
                    + _SET_ + DATE_LAST_UPDATED__UTC + "=current_timestamp"
                    + ',' + READ__BOOL + "=?"
                    + ',' + READ_END__DATE + "=?"
                    + _WHERE_ + PK_ID + "=?";
        }

        /**
         * Sql DELETE.
         * <p>
         * All 'link' tables will be updated due to their FOREIGN KEY constraints.
         * The 'other-side' of a link table is cleaned by triggers.
         */
        static final class Delete {

            /** Delete a {@link Book}. */
            static final String BOOK_BY_ID =
                    DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + PK_ID + "=?";
            /**
             * Delete the link between a {@link Book} and an {@link Author}.
             * <p>
             * This is done when a book is updated; first delete all links, then re-create them.
             */
            static final String BOOK_AUTHOR_BY_BOOK_ID =
                    DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + FK_BOOK + "=?";
            /**
             * Delete the link between a {@link Book} and a {@link Bookshelf}.
             * <p>
             * This is done when a book is updated; first delete all links, then re-create them.
             */
            static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                    DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + FK_BOOK + "=?";
            /**
             * Delete the link between a {@link Book} and a {@link Series}.
             * <p>
             * This is done when a book is updated; first delete all links, then re-create them.
             */
            static final String BOOK_SERIES_BY_BOOK_ID =
                    DELETE_FROM_ + TBL_BOOK_SERIES.getName() + _WHERE_ + FK_BOOK + "=?";
            /**
             * Delete the link between a {@link Book} and a {@link Publisher}.
             * <p>
             * This is done when a book is updated; first delete all links, then re-create them.
             */
            static final String BOOK_PUBLISHER_BY_BOOK_ID =
                    DELETE_FROM_ + TBL_BOOK_PUBLISHER.getName() + _WHERE_ + FK_BOOK + "=?";
            /**
             * Delete the link between a {@link Book} and a {@link TocEntry}.
             * <p>
             * This is done when a TOC is updated; first delete all links, then re-create them.
             */
            static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                    DELETE_FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + _WHERE_ + FK_BOOK + "=?";
        }
    }
}
