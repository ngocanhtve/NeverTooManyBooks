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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * Details of a database table as retrieved from {@code PRAGMA table_info(tableName)}.
 * <p>
 * This functionality could be made a part {@link TableDefinition}.
 * Keeping it separate; so we can optionally use it with just a table-name.
 */
public class TableInfo {

    /** columns of this table. */
    @NonNull
    private final Map<String, ColumnInfo> mColumns;
    /** only stored for debug purposes. */
    @NonNull
    private final String mTableName;

    /**
     * Constructor.
     *
     * @param db        Database Access
     * @param tableName name of table
     */
    TableInfo(@NonNull final SQLiteDatabase db,
              @NonNull final String tableName) {

        mTableName = tableName;
        mColumns = describeTable(db, mTableName);
    }

    /**
     * Get the information on all columns.
     *
     * @return the collection of column information
     */
    @NonNull
    public Collection<ColumnInfo> getColumns() {
        return mColumns.values();
    }

    /**
     * Get the information about a specific column.
     *
     * @param name of column
     *
     * @return the info, or {@code null} if not found
     */
    @Nullable
    public ColumnInfo getColumn(@NonNull final String name) {
        final String lcName = name.toLowerCase(ServiceLocator.getSystemLocale());
        return mColumns.get(lcName);
    }

    /**
     * Get the column details for the given table.
     *
     * @param db        Database Access
     * @param tableName Name of the database table to lookup
     *
     * @return A collection of ColumnInfo objects.
     */
    @NonNull
    private Map<String, ColumnInfo> describeTable(@NonNull final SQLiteDatabase db,
                                                  @NonNull final String tableName) {
        final Locale systemLocale = ServiceLocator.getSystemLocale();

        final Map<String, ColumnInfo> allColumns = new HashMap<>();
        try (Cursor colCsr = db.rawQuery("PRAGMA table_info(" + tableName + ')', null)) {
            while (colCsr.moveToNext()) {
                final ColumnInfo col = new ColumnInfo(colCsr);
                allColumns.put(col.name.toLowerCase(systemLocale), col);
            }
        }

        if (allColumns.isEmpty()) {
            throw new SQLiteException("Unable to get column details");
        }
        return allColumns;
    }

    @Override
    @NonNull
    public String toString() {
        return "TableInfo{"
               + "mTableName=" + mTableName
               + ", mColumns=" + mColumns.values()
               + '}';
    }
}
