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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Except for the clone constructor (which is 'protected') all constructors are private.
 * Use the factory methods instead for clarity.
 */
public class UserStyle
        extends BaseStyle {

    @Nullable
    private String name;

    /**
     * Constructor for <strong>importing</strong> styles.
     *
     * @param uuid UUID of the style
     *
     * @see StyleCoder
     */
    private UserStyle(@NonNull final String uuid) {
        super(uuid);
        id = 0;
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param rowData with data
     */
    private UserStyle(@NonNull final DataHolder rowData) {
        super(rowData.getString(DBKey.STYLE_UUID));

        id = rowData.getLong(DBKey.PK_ID);
        preferred = rowData.getBoolean(DBKey.STYLE_IS_PREFERRED);
        menuPosition = rowData.getInt(DBKey.STYLE_MENU_POSITION);

        name = rowData.getString(DBKey.STYLE_NAME);

        List<Integer> groupIds;
        try {
            groupIds = Arrays.stream(rowData.getString(DBKey.STYLE_GROUPS).split(","))
                             .map(Integer::parseInt)
                             .collect(Collectors.toList());
        } catch (@NonNull final NumberFormatException ignore) {
            // we should never get here... flw... try to recover.
            groupIds = List.of(BooklistGroup.AUTHOR);
        }
        setGroupIds(groupIds);
        setPrimaryAuthorTypes(rowData.getInt(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE));

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooks(item, rowData.getBoolean(item.getDbKey()));
        }

        expansionLevel = rowData.getInt(DBKey.STYLE_EXP_LEVEL);
        groupRowUsesPreferredHeight = rowData.getBoolean(DBKey.STYLE_ROW_USES_PREF_HEIGHT);

        sortAuthorByGivenName = rowData.getBoolean(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME);
        showAuthorByGivenName = rowData.getBoolean(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME);

        textScale = rowData.getInt(DBKey.STYLE_TEXT_SCALE);
        coverScale = rowData.getInt(DBKey.STYLE_COVER_SCALE);

        headerFieldVisibility = rowData.getInt(DBKey.STYLE_LIST_HEADER);
        listFieldVisibility.setValue(rowData.getInt(DBKey.STYLE_LIST_SHOW_FIELDS));
        detailsFieldVisibility.setValue(rowData.getInt(DBKey.STYLE_DETAILS_SHOW_FIELDS));
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param style   to clone
     * @param id      for the new style
     * @param uuid    for the new style
     */
    protected UserStyle(@NonNull final Context context,
                        @NonNull final BaseStyle style,
                        final long id,
                        @NonNull final String uuid) {
        super(uuid);

        this.id = id;
        preferred = style.preferred;
        menuPosition = style.menuPosition;

        // Store the new name.
        name = style.getLabel(context);

        setGroupList(style.getGroupList());
        setPrimaryAuthorTypes(style.getPrimaryAuthorType());

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooks(item, style.isShowBooks(item));
        }

        expansionLevel = style.expansionLevel;
        groupRowUsesPreferredHeight = style.groupRowUsesPreferredHeight;

        showAuthorByGivenName = style.showAuthorByGivenName;
        sortAuthorByGivenName = style.sortAuthorByGivenName;

        textScale = style.textScale;
        coverScale = style.coverScale;

        headerFieldVisibility = style.headerFieldVisibility;
        listFieldVisibility.setValue(style.listFieldVisibility.getValue());
        detailsFieldVisibility.setValue(style.detailsFieldVisibility.getValue());
    }

    @NonNull
    public static UserStyle createFromDatabase(@NonNull final DataHolder rowData) {
        return new UserStyle(rowData);
    }

    @NonNull
    public static UserStyle createFromImport(@NonNull final String uuid) {
        return new UserStyle(uuid);
    }

    @NonNull
    public String getName() {
        return Objects.requireNonNull(name, "name");
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @Override
    public boolean isUserDefined() {
        return true;
    }

    @Override
    @NonNull
    public String getTypeDescription(@NonNull final Context context) {
        return context.getString(R.string.style_is_user_defined);
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getName();
    }
}
