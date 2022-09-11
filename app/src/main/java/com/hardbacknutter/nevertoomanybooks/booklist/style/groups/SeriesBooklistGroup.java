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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Sort;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Specialized BooklistGroup representing a {@link Series} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class SeriesBooklistGroup
        extends BooklistGroup
        implements UnderEachGroup {

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;

    /**
     * Constructor.
     */
    SeriesBooklistGroup() {
        super(SERIES);
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_SERIES_TITLE,
                                                       DBDefinitions.TBL_SERIES,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key domain.
        // We override the display domain in #displayDomainExpression.
        return new GroupKey(R.string.lbl_series, "s",
                            new DomainExpression(DBDefinitions.DOM_FK_SERIES,
                                                 DBDefinitions.TBL_SERIES.dot(DBKey.PK_ID),
                                                 Sort.Unsorted))
                .addGroupDomain(
                        // We do not sort on the key domain but add the OB column instead
                        new DomainExpression(
                                new Domain.Builder("blg_ser_sort", SqLiteDataType.Text)
                                        .build(),
                                DBDefinitions.TBL_SERIES.dot(DBKey.SERIES_TITLE_OB),
                                Sort.Asc))
                .addGroupDomain(
                        // Group by id (we want the id available and there is
                        // a chance two Series will have the same name)
                        new DomainExpression(DBDefinitions.DOM_FK_SERIES,
                                             DBDefinitions.TBL_BOOK_SERIES))
                .addGroupDomain(
                        // Group by complete-flag
                        new DomainExpression(DBDefinitions.DOM_SERIES_IS_COMPLETE,
                                             DBDefinitions.TBL_SERIES))
                .addBaseDomain(
                        // The series number in the base data in sorted order.
                        // This field is NOT displayed.
                        // Casting it as a float allows for the possibility of 3.1,
                        // or even 3.1|Omnibus 3-10" as a series number.
                        new DomainExpression(
                                new Domain.Builder("blg_ser_num_float", SqLiteDataType.Real)
                                        .build(),
                                "CAST("
                                + DBDefinitions.TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER)
                                + " AS REAL)",
                                Sort.Asc))
                .addBaseDomain(
                        // The series number in the base data in sorted order.
                        // This field is displayed.
                        // Covers non-numeric data (where the above float would fail)
                        new DomainExpression(
                                DBDefinitions.DOM_BOOK_NUM_IN_SERIES,
                                DBDefinitions.TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER),
                                Sort.Asc));
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    @Override
    public boolean isShowBooksUnderEach() {
        return underEach;
    }

    @Override
    public void setShowBooksUnderEach(final boolean value) {
        underEach = value;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(StyleDataStore.PSK_STYLE_SERIES);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SeriesBooklistGroup that = (SeriesBooklistGroup) o;
        return underEach == that.underEach
               && displayDomainExpression.equals(that.displayDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), underEach, displayDomainExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "SeriesBooklistGroup{"
               + super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", underEach=" + underEach
               + '}';
    }
}
