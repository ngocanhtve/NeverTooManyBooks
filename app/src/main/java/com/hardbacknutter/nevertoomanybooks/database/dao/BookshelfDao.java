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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public interface BookshelfDao {

    /**
     * Get the {@link Bookshelf} based on the given id.
     *
     * @param id of Bookshelf to find
     *
     * @return the {@link Bookshelf}, or {@code null} if not found
     */
    @Nullable
    Bookshelf getById(long id);

    /**
     * Find a {@link Bookshelf} by using the appropriate fields of the passed {@link Bookshelf}.
     * The incoming object is not modified.
     *
     * @param bookshelf to find the id of
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    long find(@NonNull Bookshelf bookshelf);

    /**
     * Find a {@link Bookshelf} with the given name.
     *
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    Bookshelf findByName(@NonNull String name);

    /**
     * Get the filters defined for the given bookshelf.
     *
     * @param bookshelfId to fetch
     *
     * @return filters
     */
    @NonNull
    List<PFilter<?>> getFilters(long bookshelfId);

    /**
     * Convenience method, fetch all shelves, and return them as a List.
     *
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return a list of all bookshelves in the database.
     */
    @NonNull
    List<Bookshelf> getAll();

    /**
     * Get all Bookshelves; mainly for the purpose of exports.
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return Cursor over all Bookshelves
     */
    @NonNull
    Cursor fetchAllUserShelves();

    /**
     * Passed a list of Objects, remove duplicates. We keep the first occurrence.
     *
     * @param list List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Collection<Bookshelf> list);

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     *
     * @param bookshelf to update
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull Bookshelf bookshelf);

    /**
     * Creates a new bookshelf in the database.
     *
     * @param context   Current context
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    long insert(@NonNull Context context,
                @NonNull Bookshelf bookshelf);

    /**
     * Update a bookshelf.
     *
     * @param context   Current context
     * @param bookshelf to update
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull Context context,
                   @NonNull Bookshelf bookshelf);

    /**
     * Delete the passed {@link Bookshelf}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull Bookshelf bookshelf);

    /**
     * Moves all books from the 'source' {@link Bookshelf}, to the 'destId' {@link Bookshelf}.
     * The (now unused) 'source' {@link Bookshelf} is deleted.
     *
     * @param source from where to move
     * @param destId to move to
     *
     * @return the amount of books moved.
     */
    @SuppressWarnings("UnusedReturnValue")
    int merge(@NonNull Bookshelf source,
              long destId);

    /**
     * Purge book list node state data for the given {@link Bookshelf}.
     * <p>
     * Called when a {@link Bookshelf} is deleted or manually from the
     * {@link Bookshelf} management context menu.
     *
     * @param bookshelfId to purge
     */
    void purgeNodeStates(long bookshelfId);

    /**
     * Get a list of all the bookshelves this book is on.
     *
     * @param bookId to use
     *
     * @return the list
     */
    @NonNull
    ArrayList<Bookshelf> getBookshelvesByBookId(@IntRange(from = 1) long bookId);
}
