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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

/**
 * Encapsulate Filters and all related data/logic.
 * <p>
 * Example of the stored filters:
 * <pre>
 *     {@code
 *     <string name="style.booklist.filter.isbn">-1</string>
 *     <string name="style.booklist.filter.signed">-1</string>
 *     <string name="style.booklist.filter.anthology">-1</string>
 *     <set name="style.booklist.filter.bookshelves">
 *         <string>5</string>
 *         <string>6</string>
 *     </set>
 *     <boolean name="style.booklist.filter.editions.active" value="true" />
 *     <boolean name="style.booklist.filter.bookshelves.active" value="true" />
 *     <string name="style.booklist.filter.lending">-1</string>
 *     <set name="style.booklist.filter.editions">
 *         <string>4</string>
 *         <string>16</string>
 *     </set>
 *     <string name="style.booklist.filter.read">-1</string>
 *     }
 * </pre>
 */
public class Filters {

    /** Booklist BooleanFilter - ListPreference. */
    public static final String PK_FILTER_READ = "style.booklist.filter." + DBKey.BOOL_READ;
    /** Booklist BooleanFilter - ListPreference. */
    public static final String PK_FILTER_SIGNED = "style.booklist.filter." + DBKey.BOOL_SIGNED;
    /** Booklist BooleanFilter - ListPreference. */
    public static final String PK_FILTER_TOC_BITMASK = "style.booklist.filter." + DBKey.BITMASK_TOC;
    /** Booklist BooleanFilter - ListPreference. */
    public static final String PK_FILTER_LOANEE = "style.booklist.filter.lending";

    /** Booklist NotEmptyFilter - ListPreference. */
    public static final String PK_FILTER_ISBN = "style.booklist.filter." + DBKey.KEY_ISBN;

    /** Booklist BitmaskFilter - MultiSelectListPreference. */
    public static final String PK_FILTER_EDITION_BITMASK = "style.booklist.filter.editions";

    /** Booklist IntListFilter - MultiSelectListPreference. */
    public static final String PK_FILTER_BOOKSHELVES = "style.booklist.filter.bookshelves";

    /**
     * All filters in an <strong>ordered</strong> map.
     */
    private final Map<String, StyleFilter<?>> mFilters = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     */
    public Filters(final boolean isPersistent,
                   @NonNull final StylePersistenceLayer persistenceLayer) {

        mFilters.put(PK_FILTER_READ, new BooleanFilter(
                isPersistent, persistenceLayer, R.string.lbl_read, PK_FILTER_READ,
                TBL_BOOKS, DOM_BOOK_READ));

        mFilters.put(PK_FILTER_SIGNED, new BooleanFilter(
                isPersistent, persistenceLayer, R.string.lbl_signed, PK_FILTER_SIGNED,
                TBL_BOOKS, DOM_BOOK_SIGNED));

        mFilters.put(PK_FILTER_TOC_BITMASK, new BooleanFilter(
                isPersistent, persistenceLayer, R.string.lbl_anthology, PK_FILTER_TOC_BITMASK,
                TBL_BOOKS, DOM_BOOK_TOC_BITMASK));

        mFilters.put(PK_FILTER_LOANEE, new BooleanFilter(
                isPersistent, persistenceLayer, R.string.lbl_lend_out, PK_FILTER_LOANEE,
                TBL_BOOK_LOANEE, DOM_LOANEE));

        mFilters.put(PK_FILTER_ISBN, new NotEmptyFilter(
                isPersistent, persistenceLayer, R.string.lbl_isbn, PK_FILTER_ISBN,
                TBL_BOOKS, DOM_BOOK_ISBN));

        mFilters.put(PK_FILTER_EDITION_BITMASK, new BitmaskFilter(
                isPersistent, persistenceLayer, R.string.lbl_edition, PK_FILTER_EDITION_BITMASK,
                TBL_BOOKS, DOM_BOOK_EDITION_BITMASK,
                Book.Edition.BITMASK_ALL_BITS));
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    public Filters(final boolean isPersistent,
                   @NonNull final StylePersistenceLayer persistenceLayer,
                   @NonNull final Filters that) {
        for (final StyleFilter<?> filter : that.mFilters.values()) {
            final StyleFilter<?> clonedFilter = filter.clone(isPersistent, persistenceLayer);
            mFilters.put(clonedFilter.getKey(), clonedFilter);
        }
    }

    /**
     * Get the list of <strong>active and non-active</strong> Filters.
     *
     * @return list
     */
    @NonNull
    public Collection<StyleFilter<?>> getAll() {
        return mFilters.values();
    }

    /**
     * Get the list of <strong>active</strong> Filters.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    public Collection<StyleFilter<?>> getActiveFilters(@NonNull final Context context) {
        return mFilters.values()
                       .stream()
                       .filter(f -> f.isActive(context))
                       .collect(Collectors.toList());
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param key   filter to set
     * @param value to use
     */
    @SuppressWarnings("SameParameterValue")
    public void setFilter(@Key @NonNull final String key,
                          final boolean value) {
        //noinspection ConstantConditions
        ((BooleanFilter) mFilters.get(key)).set(value);
    }

    @NonNull
    private List<String> getLabels(@NonNull final Context context,
                                   final boolean all) {

        return mFilters.values().stream()
                       .filter(f -> f.isActive(context) || all)
                       .map(f -> f.getLabel(context))
                       .sorted()
                       .collect(Collectors.toList());
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the list of in-use filter names in a human readable format.
     *
     * @param context Current context
     * @param all     {@code true} to get all, {@code false} for only the active filters
     *
     * @return summary text
     */
    @NonNull
    public String getSummaryText(@NonNull final Context context,
                                 final boolean all) {

        final List<String> labels = getLabels(context, all);
        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return TextUtils.join(", ", labels);
        }
    }

    /**
     * Get a flat map with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return flat map
     */
    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = new HashMap<>();
        for (final StyleFilter<?> filter : mFilters.values()) {
            map.put(filter.getKey(), filter);
        }
        return map;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Filters filters = (Filters) o;
        return Objects.equals(mFilters, filters.mFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFilters);
    }

    @NonNull
    @Override
    public String toString() {
        return "Filters{"
               + "mFilters=" + mFilters
               + '}';
    }

    @StringDef({PK_FILTER_ISBN,
                PK_FILTER_READ,
                PK_FILTER_SIGNED,
                PK_FILTER_LOANEE,
                PK_FILTER_TOC_BITMASK,
                PK_FILTER_EDITION_BITMASK,
                PK_FILTER_BOOKSHELVES})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Key {

    }
}
