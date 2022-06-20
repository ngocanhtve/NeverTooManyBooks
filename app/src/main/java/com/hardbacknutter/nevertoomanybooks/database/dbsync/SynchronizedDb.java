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
package com.hardbacknutter.nevertoomanybooks.database.dbsync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;

/**
 * Database wrapper class that performs thread synchronization on all operations.
 * <p>
 * After getting a question "why?": See {@link Synchronizer} for details.
 * <p>
 * About the SQLite version:
 * <a href="https://developer.android.com/reference/android/database/sqlite/package-summary">
 * package-summary</a>
 * <p>
 * API 28   3.22.0
 * API 27   3.19.4
 * API 26   3.18.2
 * <p>
 * But some device manufacturers include different versions of SQLite on their devices.
 */
public class SynchronizedDb
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "SynchronizedDb";

    private static final String ERROR_TX_LOCK_WAS_NULL = "Lock passed in was NULL";
    private static final String ERROR_TX_ALREADY_STARTED = "TX already started";
    private static final String ERROR_TX_NOT_STARTED = "No TX started";
    private static final String ERROR_TX_INSIDE_SHARED = "Inside shared TX";
    private static final String ERROR_TX_WRONG_LOCK = "Wrong lock";

    @NonNull
    private final SQLiteOpenHelper mSqLiteOpenHelper;
    private final int mPreparedStmtCacheSize;

    /** Underlying (and open for writing) database. */
    @NonNull
    private final SQLiteDatabase mSqlDb;

    /** Sync object to use. */
    @NonNull
    private final Synchronizer mSynchronizer;

    /** Factory object to create the custom cursor. */
    private final SQLiteDatabase.CursorFactory mCursorFactory = (db, mq, et, q) ->
            new SynchronizedCursor(mq, et, q, getSynchronizer());

    /** Factory object to create a {@link TypedCursor} cursor. */
    private final SQLiteDatabase.CursorFactory mTypedCursorFactory =
            (db, d, et, q) -> new TypedCursor(d, et, q, getSynchronizer());


    /**
     * Currently held transaction lock, if any.
     * <p>
     * Set in {@link #beginTransaction(boolean)}
     * and released in {@link #endTransaction(Synchronizer.SyncLock)}
     */
    @Nullable
    private Synchronizer.SyncLock mTxLock;

    /**
     * Constructor.
     *
     * @param synchronizer     Synchronizer to use
     * @param sqLiteOpenHelper SQLiteOpenHelper to open the underlying database
     *
     * @throws SQLiteException if the database cannot be opened
     */
    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
                          @NonNull final SQLiteOpenHelper sqLiteOpenHelper) {
        this(synchronizer, sqLiteOpenHelper, -1);
    }

    /**
     * Constructor.
     * <p>
     * The javadoc for setMaxSqlCacheSize says the default is 10,
     * but if you check the source code (verified API 30):
     * android/database/sqlite/SQLiteDatabaseConfiguration.java: public int maxSqlCacheSize;
     * the default is in fact 25 as set in the constructor of that class.
     *
     * @param synchronizer          Synchronizer to use
     * @param sqLiteOpenHelper      SQLiteOpenHelper to open the underlying database
     * @param preparedStmtCacheSize the number or prepared statements to cache.
     *
     * @throws SQLiteException if the database cannot be opened
     */
    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
                          @NonNull final SQLiteOpenHelper sqLiteOpenHelper,
                          @IntRange(to = SQLiteDatabase.MAX_SQL_CACHE_SIZE)
                          final int preparedStmtCacheSize) {
        mSynchronizer = synchronizer;
        mSqLiteOpenHelper = sqLiteOpenHelper;
        mPreparedStmtCacheSize = preparedStmtCacheSize;

        // Trigger onCreate/onUpdate/... for the database
        final Synchronizer.SyncLock syncLock = mSynchronizer.getExclusiveLock();
        try {
            mSqlDb = getWritableDatabase();
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugDumpInfo(@NonNull final SQLiteDatabase db) {
        final String[] sql = {"SELECT sqlite_version() AS sqlite_version",
                              "PRAGMA encoding",
                              "PRAGMA collation_list",
                              "PRAGMA foreign_keys",
                              "PRAGMA recursive_triggers",
                              };
        for (final String s : sql) {
            try (Cursor cursor = db.rawQuery(s, null)) {
                if (cursor.moveToNext()) {
                    Log.d(TAG, "debugDumpInfo|" + s + " = " + cursor.getString(0));
                }
            }
        }
    }

    /**
     * Open the database for reading. {@see SqLiteOpenHelper#getReadableDatabase()}
     *
     * @return database
     *
     * @throws SQLiteException if the database cannot be opened for reading
     */
    @NonNull
    private SQLiteDatabase getReadableDatabase() {
        final SQLiteDatabase db = mSqLiteOpenHelper.getReadableDatabase();
        // only set when bigger than the default
        if ((mPreparedStmtCacheSize > 25)) {
            db.setMaxSqlCacheSize(mPreparedStmtCacheSize);
        }
        return db;
    }

    /**
     * Open the database for writing. {@see SqLiteOpenHelper#getWritableDatabase()}
     *
     * @return database
     *
     * @throws SQLiteException if the database cannot be opened for writing
     */
    @NonNull
    private SQLiteDatabase getWritableDatabase() {
        final SQLiteDatabase db = mSqLiteOpenHelper.getWritableDatabase();
        // only set when bigger than the default
        if ((mPreparedStmtCacheSize > 25)) {
            db.setMaxSqlCacheSize(mPreparedStmtCacheSize);
        }
        return db;
    }

    @Override
    public void close() {
        mSqlDb.close();
    }

    /**
     * Locking-aware recreating {@link TableDefinition.TableType#Temporary} tables.
     * <p>
     * If the table has no references to it, this method can also
     * be used on {@link TableDefinition.TableType#Standard}.
     * <p>
     * Drop this table (if it exists) and (re)create it including its indexes.
     *
     * @param table                 to recreate
     * @param withDomainConstraints Indicates if fields should have constraints applied
     */
    public void recreate(@NonNull final TableDefinition table,
                         final boolean withDomainConstraints) {

        // We're being paranoid here... we should always be called in a transaction,
        // which means we should not bother with LOCK_EXCLUSIVE.
        // But having the logic in place because: 1) future proof + 2) developer boo-boo,

        if (BuildConfig.DEBUG /* always */) {
            if (!mSqlDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        try {
            // Drop the table in case there is an orphaned instance with the same name.
            if (table.exists(mSqlDb)) {
                mSqlDb.execSQL("DROP TABLE IF EXISTS " + table.getName());
            }
            table.create(mSqlDb, withDomainConstraints);
            table.createIndices(mSqlDb);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * <strong>Note:</strong> SQLite maintains a Statement cache based on sql string matching.
     * However, to avoid the overhead, loops should use {@link #rawQuery(String, String[])} instead.
     *
     * @param table  the table to insert the row into
     * @param values this map contains the initial column values for the
     *               row. The keys should be the column names and the values the
     *               column values
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final String table,
                       @NonNull final ContentValues values) {

        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: insert does not throw exceptions for the actual insert.
        // but it can throw other exceptions.
        try {
            return mSqlDb.insert(table, null, values);

        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>Note:</strong> SQLite maintains a Statement cache based on sql string matching.
     * However, to avoid the overhead, loops should use {@link #compileStatement} instead.
     *
     * @return the number of rows affected
     */
    public int update(@NonNull final String table,
                      @NonNull final ContentValues values,
                      @NonNull final String whereClause,
                      @Nullable final String[] whereArgs) {

        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: update does not throw exceptions for the actual update.
        // but it can throw other exceptions.
        try {
            return mSqlDb.update(table, values, whereClause, whereArgs);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>Note:</strong> SQLite maintains a Statement cache based on sql string matching.
     * However, to avoid the overhead, loops should use {@link #compileStatement} instead.
     *
     * @return the number of rows affected if a whereClause is passed in, 0
     *         otherwise. To remove all rows and get a count pass "1" as the
     *         whereClause.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int delete(@NonNull final String table,
                      @Nullable final String whereClause,
                      @Nullable final String[] whereArgs) {

        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: delete does not throw exceptions for the actual delete.
        // but it can throw other exceptions.
        try {
            return mSqlDb.delete(table, whereClause, whereArgs);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     */
    @NonNull
    public SynchronizedStatement compileStatement(@NonNull final String sql) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        try {
            return new SynchronizedStatement(mSynchronizer, mSqlDb, sql);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     */
    public void execSQL(@NonNull final String sql) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_EXEC_SQL) {
            Log.d(TAG, "ENTER|execSQL|sql=" + sql);
        }

        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.Exclusive) {
                throw new TransactionException(ERROR_TX_INSIDE_SHARED);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        try {
            mSqlDb.execSQL(sql);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }


    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @return the cursor
     */
    @NonNull
    public SynchronizedCursor rawQuery(@NonNull final String sql,
                                       @Nullable final String[] selectionArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSynchronizer.getSharedLock();
        }

        try {
            /* lint says this cursor is not always closed.
             * 2019-01-14: the only place it's not closed is in {@link SearchSuggestionProvider}
             * where it seems not possible to close it ourselves.
             * TEST: do we actually need to use the factory here ? mSqlDb was created
             *  with a factory?
             */
            return (SynchronizedCursor)
                    mSqlDb.rawQueryWithFactory(mCursorFactory, sql, selectionArgs, null);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Wrapper for underlying database method.
     * It is recommended that custom cursors subclass SynchronizedCursor.
     * <p>
     * Runs the provided SQL and returns a cursor over the result set.
     *
     * @param sql           the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param editTable     the name of the first table, which is editable
     *
     * @return A {@link TypedCursor} object, which is positioned before the first entry.
     *         Note that {@link Cursor}s are not synchronized,
     *         see the documentation for more details.
     */
    @NonNull
    public TypedCursor rawQueryWithTypedCursor(@NonNull final String sql,
                                               @Nullable final String[] selectionArgs,
                                               @Nullable final String editTable) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSynchronizer.getSharedLock();
        }
        try {
            return (TypedCursor) mSqlDb
                    .rawQueryWithFactory(mTypedCursorFactory, sql, selectionArgs, editTable);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }


    public TableInfo getTableInfo(@NonNull final TableDefinition tableDefinition) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSynchronizer.getSharedLock();
        }
        try {
            return tableDefinition.getTableInfo(mSqlDb);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Drop the given table, if it exists.
     *
     * @param tableName to drop
     */
    public void drop(@NonNull final String tableName) {
        execSQL("DROP TABLE IF EXISTS " + tableName);
    }

    /**
     * Run '<a href="https://www.sqlite.org/pragma.html#pragma_optimize">optimize</a>'
     * on the whole database.
     */
    public void optimize() {
        execSQL("PRAGMA optimize");
    }

    /**
     * Run '<a href="https://www.sqlite.org/lang_analyze.html">analyse</a>' on the whole database.
     */
    public void analyze() {
        execSQL("analyze");
    }

    /**
     * Run 'analyse' on a table.
     *
     * @param table to analyse.
     */
    public void analyze(@NonNull final TableDefinition table) {
        execSQL("analyze " + table);
    }

    /**
     * Wrapper.
     *
     * @return {@code true} if the current thread is in a transaction.
     */
    public boolean inTransaction() {
        return mSqlDb.inTransaction();
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public Synchronizer.SyncLock beginTransaction(final boolean isUpdate) {
        final Synchronizer.SyncLock txLock;
        if (isUpdate) {
            txLock = mSynchronizer.getExclusiveLock();
        } else {
            txLock = mSynchronizer.getSharedLock();
        }
        // We have the lock, but if the real beginTransaction() throws an exception,
        // we need to release the lock.
        try {
            // If we have a lock, and there is currently a TX active...die
            // Note: because we get a lock, two 'isUpdate' transactions will
            // block, this is only likely to happen with two TXs on the current thread
            // or two non-update TXs on different thread.
            if (mTxLock == null) {
                mSqlDb.beginTransaction();
            } else {
                throw new TransactionException(ERROR_TX_ALREADY_STARTED);
            }
        } catch (@NonNull final RuntimeException e) {
            txLock.unlock();
            throw new TransactionException("beginTransaction failed: " + e.getMessage(), e);
        }
        mTxLock = txLock;
        return txLock;
    }

    /**
     * Wrapper for underlying database method.
     */
    public void setTransactionSuccessful() {
        // We could pass in the lock and do the same checks as we do in #endTransaction
        mSqlDb.setTransactionSuccessful();
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * <strong>MUST</strong> be called from a 'finally' block.
     *
     * @param txLock Lock returned from {@link #beginTransaction(boolean)}.
     */
    public void endTransaction(@Nullable final Synchronizer.SyncLock txLock) {
        if (txLock == null) {
            throw new TransactionException(ERROR_TX_LOCK_WAS_NULL);
        }
        if (mTxLock == null) {
            throw new TransactionException(ERROR_TX_NOT_STARTED);
        }
        if (!mTxLock.equals(txLock)) {
            throw new TransactionException(ERROR_TX_WRONG_LOCK);
        }

        try {
            mSqlDb.endTransaction();
        } finally {
            // Clear mTxLock before unlocking so another thread does not
            // see the old lock when it gets the lock
            mTxLock = null;
            txLock.unlock();
        }
    }

    /**
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return the underlying SQLiteDatabase object.
     */
    @NonNull
    public SQLiteDatabase getSQLiteDatabase() {
        return mSqlDb;
    }

    @NonNull
    public String getDatabasePath() {
        return mSqlDb.getPath();
    }

    /**
     * For use by the cursor factory only.
     *
     * @return the underlying Synchronizer object.
     */
    @NonNull
    private Synchronizer getSynchronizer() {
        return mSynchronizer;
    }

    /**
     * DEBUG. Dumps the content of this table to the debug output.
     *
     * @param tableDefinition to dump
     * @param limit           LIMIT limit
     * @param orderBy         ORDER BY orderBy
     * @param tag             log tag to use
     * @param header          a header which will be logged first
     */
    public void dumpTable(@NonNull final TableDefinition tableDefinition,
                          final int limit,
                          @NonNull final String orderBy,
                          @NonNull final String tag,
                          @NonNull final String header) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(tag, "Table: " + tableDefinition.getName() + ": " + header);

            final String sql =
                    "SELECT * FROM " + tableDefinition.getName()
                    + " ORDER BY " + orderBy + " LIMIT " + limit;
            try (Cursor cursor = rawQuery(sql, null)) {
                final StringBuilder columnHeading = new StringBuilder("\n");
                final String[] columnNames = cursor.getColumnNames();
                for (final String column : columnNames) {
                    columnHeading.append(String.format("%-12s  ", column));
                }
                Log.d(tag, columnHeading.toString());

                while (cursor.moveToNext()) {
                    final StringBuilder line = new StringBuilder();
                    for (int c = 0; c < cursor.getColumnCount(); c++) {
                        line.append(String.format("%-12s  ", cursor.getString(c)));
                    }
                    Log.d(tag, line.toString());
                }
            }
        }
    }
}
