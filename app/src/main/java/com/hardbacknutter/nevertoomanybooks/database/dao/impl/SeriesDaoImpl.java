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
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMerger;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

public class SeriesDaoImpl
        extends BaseDaoImpl
        implements SeriesDao {

    /** Log tag. */
    private static final String TAG = "SeriesDaoImpl";

    /** All Books (id only) for a given Series. */
    private static final String SELECT_BOOK_IDS_BY_SERIES_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID)
            + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?";

    /** All Books (id only) for a given Series and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKey.PK_ID)
            + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_BOOKS, TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_NAMES =
            SELECT_DISTINCT_ + DBKey.SERIES_TITLE
            + ',' + DBKey.SERIES_TITLE_OB
            + _FROM_ + TBL_SERIES.getName()
            + _ORDER_BY_ + DBKey.SERIES_TITLE_OB + _COLLATION;

    /** {@link Series}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_SERIES.getName();

    /** All Series for a Book; ordered by position, name. */
    private static final String SERIES_BY_BOOK_ID =
            SELECT_DISTINCT_ + TBL_SERIES.dotAs(DBKey.PK_ID,
                                                DBKey.SERIES_TITLE,
                                                DBKey.SERIES_TITLE_OB,
                                                DBKey.SERIES_IS_COMPLETE)
            + ',' + TBL_BOOK_SERIES.dotAs(DBKey.SERIES_BOOK_NUMBER,
                                          DBKey.BOOK_SERIES_POSITION)

            + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_SERIES)
            + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_SERIES.dot(DBKey.BOOK_SERIES_POSITION)
            + ',' + TBL_SERIES.dot(DBKey.SERIES_TITLE_OB) + _COLLATION;

    /** Get a {@link Series} by the Series id. */
    private static final String GET_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

    /**
     * Get the id of a {@link Series} by Title.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Searches SERIES_TITLE_OB on both "The Title" and "Title, The"
     */
    private static final String FIND_ID =
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_SERIES.getName()
            + _WHERE_ + DBKey.SERIES_TITLE_OB + "=?" + _COLLATION
            + " OR " + DBKey.SERIES_TITLE_OB + "=?" + _COLLATION;

    /**
     * Get the language (ISO3) code for a Series.
     * This is defined as the language code for the first book in the Series.
     */
    private static final String GET_LANGUAGE =
            SELECT_ + TBL_BOOKS.dotAs(DBKey.LANGUAGE)
            + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_SERIES) + "=?"
            + _ORDER_BY_ + TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER)
            + " LIMIT 1";

    private static final String COUNT_ALL =
            SELECT_COUNT_FROM_ + TBL_SERIES.getName();

    /** Count the number of {@link Book}'s in a {@link Series}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + DBKey.FK_BOOK + ") FROM " + TBL_BOOK_SERIES.getName()
            + _WHERE_ + DBKey.FK_SERIES + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_SERIES.getName()
            + '(' + DBKey.SERIES_TITLE
            + ',' + DBKey.SERIES_TITLE_OB
            + ',' + DBKey.SERIES_IS_COMPLETE
            + ") VALUES (?,?,?)";

    /** Delete a {@link Series}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_SERIES.getName() + _WHERE_ + DBKey.PK_ID + "=?";

    /** Purge a {@link Series} if no longer in use. */
    private static final String PURGE =
            DELETE_FROM_ + TBL_SERIES.getName()
            + _WHERE_ + DBKey.PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + DBKey.FK_SERIES + _FROM_ + TBL_BOOK_SERIES.getName() + ')';

    /**
     * Constructor.
     */
    public SeriesDaoImpl() {
        super(TAG);
    }

    @Override
    @Nullable
    public Series getById(final long id) {
        try (Cursor cursor = db.rawQuery(GET_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Series(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    @Override
    public long find(@NonNull final Context context,
                     @NonNull final Series series,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale) {

        final OrderByHelper.OrderByData obd;
        if (lookupLocale) {
            obd = OrderByHelper.createOrderByData(context, series.getTitle(),
                                                  bookLocale, series::getLocale);
        } else {
            obd = OrderByHelper.createOrderByData(context, series.getTitle(),
                                                  bookLocale, null);
        }

        try (SynchronizedStatement stmt = db.compileStatement(FIND_ID)) {
            stmt.bindString(1, SqlEncode.orderByColumn(series.getTitle(), obd.locale));
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getNames() {
        return getColumnAsStringArrayList(SELECT_ALL_NAMES);
    }

    @Override
    @NonNull
    public ArrayList<Series> getSeriesByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SERIES_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public String getLanguage(final long id) {
        try (SynchronizedStatement stmt = db.compileStatement(GET_LANGUAGE)) {
            stmt.bindLong(1, id);
            final String code = stmt.simpleQueryForStringOrNull();
            return code != null ? code : "";
        }
    }

    @Override
    @NonNull
    public ArrayList<Long> getBookIds(final long seriesId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_BOOK_IDS_BY_SERIES_ID,
                                         new String[]{String.valueOf(seriesId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public ArrayList<Long> getBookIds(final long seriesId,
                                      final long bookshelfId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                SELECT_BOOK_IDS_BY_SERIES_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(seriesId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return db.rawQuery(SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countBooks(@NonNull final Context context,
                           @NonNull final Series series,
                           @NonNull final Locale bookLocale) {
        if (series.getId() == 0 && fixId(context, series, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = db.compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, series.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean setComplete(final long seriesId,
                               final boolean isComplete) {
        final ContentValues cv = new ContentValues();
        cv.put(DBKey.SERIES_IS_COMPLETE, isComplete);

        return 0 < db.update(TBL_SERIES.getName(), cv, DBKey.PK_ID + "=?",
                             new String[]{String.valueOf(seriesId)});
    }

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Series> list,
                             final boolean lookupLocale,
                             @NonNull final Locale bookLocale) {
        if (list.isEmpty()) {
            return false;
        }

        final EntityMerger<Series> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Series current = entityMerger.next();

            final Locale locale;
            if (lookupLocale) {
                locale = current.getLocale(context, bookLocale);
            } else {
                locale = bookLocale;
            }

            // Don't lookup the locale a 2nd time.
            fixId(context, current, false, locale);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final Series series,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, series, lookupLocale, bookLocale);
        series.setId(id);
        return id;
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Series series,
                        @NonNull final Locale bookLocale) {

        if (series.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            fixId(context, series, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Series dbSeries = getById(series.getId());
            if (dbSeries != null) {
                // copy any updated fields
                series.copyFrom(dbSeries, false);
            } else {
                // not found?, set as 'new'
                series.setId(0);
            }
        }
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Series series,
                       @NonNull final Locale bookLocale) {

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, series.getTitle(), bookLocale, series::getLocale);

        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, SqlEncode.orderByColumn(obd.title, obd.locale));
            stmt.bindBoolean(3, series.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Series series,
                          @NonNull final Locale bookLocale) {

        final OrderByHelper.OrderByData obd = OrderByHelper.createOrderByData(
                context, series.getTitle(), bookLocale, series::getLocale);

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.SERIES_TITLE, series.getTitle());
        cv.put(DBKey.SERIES_TITLE_OB, SqlEncode.orderByColumn(obd.title, obd.locale));
        cv.put(DBKey.SERIES_IS_COMPLETE, series.isComplete());

        return 0 < db.update(TBL_SERIES.getName(), cv, DBKey.PK_ID + "=?",
                             new String[]{String.valueOf(series.getId())});
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, series.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            series.setId(0);
            repositionSeries(context);
        }
        return rowsAffected == 1;
    }

    @Override
    public void merge(@NonNull final Context context,
                      @NonNull final Series source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.FK_SERIES, destId);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final Series destination = getById(destId);

            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
            for (final long bookId : getBookIds(source.getId())) {
                final Book book = Book.from(bookId);

                final Collection<Series> fromBook = book.getSeries();
                final Collection<Series> destList = new ArrayList<>();

                for (final Series item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(destination);
                    } else {
                        destList.add(item);
                    }
                }
                bookDao.insertSeries(context, bookId, destList, true,
                                     book.getLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int repositionSeries(@NonNull final Context context) {
        final String sql =
                "SELECT " + DBKey.FK_BOOK + " FROM "
                + "(SELECT " + DBKey.FK_BOOK
                + ", MIN(" + DBKey.BOOK_SERIES_POSITION + ") AS mp"
                + " FROM " + TBL_BOOK_SERIES.getName() + " GROUP BY " + DBKey.FK_BOOK
                + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionSeries|" + TBL_BOOK_SERIES.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = context.getResources().getConfiguration().getLocales().get(0);
            final BookDao bookDao = ServiceLocator.getInstance().getBookDao();

            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Series> list = getSeriesByBookId(bookId);
                    bookDao.insertSeries(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);
            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionSeries|done");
                }
            }
        }
        return bookIds.size();
    }
}
