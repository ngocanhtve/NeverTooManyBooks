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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BdtAuthor;

public interface BedethequeCacheDao {

    /**
     * Find a {@link BdtAuthor} with the given name.
     *
     * @param name to find
     *
     * @return the {@link BdtAuthor}, or {@code null} if not found
     */
    @Nullable
    BdtAuthor findByName(@NonNull String name,
                         @NonNull Locale locale);

    /**
     * Create the {@link BdtAuthor}s as supplied into the database.
     *
     * @param locale         to use
     * @param recordSupplier a supplier which delivers a {@link BdtAuthor} to insert,
     *                       or {@code null} when done.
     *
     * @return {@code true} if at least one row was inserted
     *
     * @throws DaoWriteException on failure
     */
    boolean insert(@NonNull Locale locale,
                   @NonNull Supplier<BdtAuthor> recordSupplier)
            throws DaoWriteException;

    /**
     * Update a {@link BdtAuthor}.
     *
     * @param bdtAuthor to update
     * @param locale    to use
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull BdtAuthor bdtAuthor,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Check if there is at least one {@link BdtAuthor} in the database whose name
     * starts with the given character.
     * <p>
     * This makes the assumption if there is one, then the whole list-page for that character
     * has been downloaded and cached.
     *
     * @param c1 to lookup
     *
     * @return {@code true} if there is
     */
    boolean isAuthorPageCached(char c1);

    /**
     * Get the total amount of authors cached.
     *
     * @return amount
     */
    int countAuthors();

    /**
     * Clear the entire cache.
     */
    void clearCache();
}