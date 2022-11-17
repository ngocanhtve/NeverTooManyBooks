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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;

/**
 * Cleanup routines for some columns/tables which can be run at upgrades, import, startup
 * <p>
 * Work in progress.
 * <p>
 * TODO: add a loop check for the covers cache database:
 * read all book uuid's and clean the covers.db removing non-existing uuid rows.
 */
public class DBCleaner {

    /** Log tag. */
    private static final String TAG = "DBCleaner";

    /** Database Access. */
    @NonNull
    private final SynchronizedDb db;
    @NonNull
    private final ServiceLocator serviceLocator;

    /**
     * Constructor.
     */
    public DBCleaner() {
        serviceLocator = ServiceLocator.getInstance();
        db = serviceLocator.getDb();
    }

    public int clean(@NonNull final Context context) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        // do a mass update of any languages not yet converted to ISO 639-2 codes
        languages(context, userLocale);

        // make sure there are no 'T' separators in datetime fields
        datetimeFormat();

        // validate booleans to have 0/1 content (could do just ALL_TABLES)
        booleanColumns(DBDefinitions.TBL_BOOKS,
                       DBDefinitions.TBL_AUTHORS,
                       DBDefinitions.TBL_SERIES);

        // clean/correct style UUID's on Bookshelves for deleted styles.
        bookshelves(context);

        //TEST: we only check & log for now, but don't update yet...
        // we need to test with bad data
        bookBookshelf(true);

        // re-sort positional links - theoretically this should never be needed... flw.
        int modified;
        modified = serviceLocator.getAuthorDao().repositionAuthor(context);
        modified += serviceLocator.getSeriesDao().repositionSeries(context);
        modified += serviceLocator.getPublisherDao().repositionPublishers(context);
        modified += serviceLocator.getTocEntryDao().repositionTocEntries(context);

        return modified;
    }

    /**
     * Do a bulk update of any languages not yet converted to ISO codes.
     * Special entries are left untouched; example "Dutch+French" a bilingual edition.
     *
     * @param context Current context
     */
    public void languages(@NonNull final Context context,
                          @NonNull final Locale userLocale) {

        final LanguageDao languageDao = serviceLocator.getLanguageDao();
        final Languages langHelper = serviceLocator.getLanguages();

        for (final String lang : languageDao.getList()) {
            if (lang != null && !lang.isEmpty()) {
                final String iso;

                if (lang.length() > 3) {
                    // It's likely a 'display' name of a language.
                    iso = langHelper.getISO3FromDisplayName(userLocale, lang);
                } else {
                    // It's almost certainly a language code
                    iso = langHelper.getISO3FromCode(lang);
                }

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "languages|Global language update"
                               + "|from=" + lang
                               + "|to=" + iso);
                }
                if (!iso.equals(lang)) {
                    languageDao.rename(lang, iso);
                }
            }
        }
    }

    /**
     * Replace 'T' occurrences with ' '.
     * See package-info docs for
     * {@link FullDateParser}
     */
    public void datetimeFormat() {
        final String[] columns = {
                DBKey.DATE_LAST_UPDATED__UTC,
                DBKey.DATE_ADDED__UTC,
                };

        final Pattern T = Pattern.compile("T");

        final Collection<Pair<Long, String>> rows = new ArrayList<>();

        for (final String key : columns) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + DBKey.PK_ID + ',' + key + " FROM " + DBDefinitions.TBL_BOOKS.getName()
                    + " WHERE " + key + " LIKE '%T%'", null)) {
                while (cursor.moveToNext()) {
                    rows.add(new Pair<>(cursor.getLong(0), cursor.getString(1)));
                }
            }

            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "dates",
                         "key=" + key
                         + "|rows.size()=" + rows.size());
            }
            try (SynchronizedStatement stmt = db.compileStatement(
                    "UPDATE " + DBDefinitions.TBL_BOOKS.getName()
                    + " SET " + key + "=? WHERE " + DBKey.PK_ID + "=?")) {

                for (final Pair<Long, String> row : rows) {
                    stmt.bindString(1, T.matcher(row.second).replaceFirst(" "));
                    stmt.bindLong(2, row.first);
                    stmt.executeUpdateDelete();
                }
            }
            // reuse for next column
            rows.clear();
        }
    }

    /**
     * Validates {@link Bookshelf} being set to a valid {@link Style}.
     *
     * @param context Current context
     */
    public void bookshelves(@NonNull final Context context) {
        serviceLocator.getBookshelfDao().getAll()
                      .forEach(bookshelf -> bookshelf.validateStyle(context));
    }

    /**
     * Validates all boolean columns to contain '0' or '1'.
     *
     * @param tables list of tables
     */
    public void booleanColumns(@NonNull final TableDefinition... tables) {
        for (final TableDefinition table : tables) {
            table.getDomains()
                 .stream()
                 .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Boolean)
                 .forEach(domain -> booleanCleanup(table.getName(), domain.getName()));
        }
    }

    /**
     * Enforce boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     */
    private void booleanCleanup(@NonNull final String table,
                                @NonNull final String column) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "booleanCleanup|table=" + table + "|column=" + column);
        }

        final String select = "SELECT DISTINCT " + column + " FROM " + table
                              + " WHERE " + column + " NOT IN ('0','1')";
        toLog("booleanCleanup", select);

        final String update = "UPDATE " + table + " SET " + column + "=?"
                              + " WHERE lower(" + column + ") IN ";
        String sql;
        sql = update + "('true','t','yes')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 1);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    Log.d(TAG, "booleanCleanup|true=" + count);
                }
            }
        }

        sql = update + "('false','f','no')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 0);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    Log.d(TAG, "booleanCleanup|false=" + count);
                }
            }
        }
    }

    /* ****************************************************************************************** */

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    public void bookBookshelf(final boolean dryRun) {
        final String select = "SELECT DISTINCT " + DBKey.FK_BOOK
                              + " FROM " + DBDefinitions.TBL_BOOK_BOOKSHELF
                              + " WHERE " + DBKey.FK_BOOKSHELF + " IS NULL";

        toLog("bookBookshelf|ENTER", select);
        if (!dryRun) {
            final String sql = "DELETE " + DBDefinitions.TBL_BOOK_BOOKSHELF
                               + " WHERE " + DBKey.FK_BOOKSHELF + " IS NULL";
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("bookBookshelf|EXIT", select);
        }
    }

    /**
     * Convert any {@code null} values to an empty string.
     * <p>
     * Used to correct data in columns which have "string default ''"
     *
     * @param table  to check
     * @param column to check
     * @param dryRun {@code true} to run the update.
     */
    public void nullString2empty(@NonNull final String table,
                                 @NonNull final String column,
                                 final boolean dryRun) {
        final String select =
                "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + " IS NULL";
        toLog("nullString2empty|ENTER", select);
        if (!dryRun) {
            final String sql =
                    "UPDATE " + table + " SET " + column + "=''" + " WHERE " + column + " IS NULL";
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("nullString2empty|EXIT", select);
        }
    }

    /**
     * WIP... debug
     * Execute the query and log the results.
     *
     * @param state Enter/Exit
     * @param query to execute
     */
    private void toLog(@NonNull final String state,
                       @NonNull final String query) {
        if (BuildConfig.DEBUG /* always */) {
            try (SynchronizedCursor cursor = db.rawQuery(query, null)) {
                Log.d(TAG, state + "|row count=" + cursor.getCount());
                while (cursor.moveToNext()) {
                    final String field = cursor.getColumnName(0);
                    final String value = cursor.getString(0);

                    Log.d(TAG, state + '|' + field + '=' + value);
                }
            }
        }
    }
}
