/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

public class StyleCoder
        implements JsonCoder<Style> {

    private static final String TAG = "StyleCoder";

    /** The combined bitmask value for the PK_DETAILS_SHOW* values. */
    private static final String PK_DETAILS_FIELD_VISIBILITY = "style.details.show.fields";
    /** The combined bitmask value for the PK_LIST_SHOW* values. */
    private static final String PK_LIST_FIELD_VISIBILITY = "style.list.show.fields";
    /** The JSON encode string with the orderBy columns for the book level. */
    private static final String PK_LIST_FIELD_ORDER_BY = "style.list.sort.fields";

    /** The sub-tag for the array with the style settings. */
    private static final String STYLE_SETTINGS = "settings";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_SORT = "sort";

    /**
     * Static wrapper convenience method.
     * Encode the {@link Style#getBookLevelFieldsOrderBy()} to a JSON String.
     *
     * @param style to use
     *
     * @return JSON encoded string
     *
     * @throws JSONException upon any parsing error
     */
    @NonNull
    public static String getBookLevelFieldsOrderByAsJsonString(@NonNull final Style style)
            throws JSONException {
        final JSONArray columns = new StyleCoder().encodeBookLevelFieldsOrderBy(style);
        return Objects.requireNonNull(columns.toString(),
                                      "encBookLevelFieldsOrderBy was NULL");
    }

    /**
     * Static wrapper convenience method.
     * Decode a JSON String for use with {@link BaseStyle#setBookLevelFieldsOrderBy(Map)}.
     *
     * @param source JSON encoded string
     *
     * @return map with columns; will be empty if decoding failed (see log for any reason).
     */
    @NonNull
    public static Map<String, Sort> decodeBookLevelFieldsOrderBy(@NonNull final String source) {
        try {
            return new StyleCoder().decodeBookLevelFieldsOrderBy(new JSONArray(source));

        } catch (@NonNull final JSONException e) {
            // Do not crash, this is not critical, but DO log
            LoggerFactory.getLogger().e(TAG, e);
        }
        return Map.of();
    }

    @NonNull
    @Override
    public JSONObject encode(@NonNull final Style style)
            throws JSONException {
        final JSONObject out = new JSONObject();

        final StyleType type = style.getType();

        out.put(DBKey.STYLE_TYPE, type.getId());
        out.put(DBKey.STYLE_UUID, style.getUuid());

        out.put(DBKey.STYLE_IS_PREFERRED, style.isPreferred());
        out.put(DBKey.STYLE_MENU_POSITION, style.getMenuPosition());

        if (type == StyleType.Builtin) {
            // We're done
            return out;
        }

        if (type == StyleType.User) {
            out.put(DBKey.STYLE_NAME, ((UserStyle) style).getName());
        }

        // The settings will be stored under a new JSON object 'STYLE_SETTINGS'
        final JSONObject settings = new JSONObject();

        encodeGroups(style, settings);

        settings.put(StyleDataStore.PK_LAYOUT, style.getLayout().getId());
        settings.put(StyleDataStore.PK_COVER_CLICK_ACTION, style.getCoverClickAction().getId());
        settings.put(StyleDataStore.PK_COVER_SCALE, style.getCoverScale());
        settings.put(StyleDataStore.PK_TEXT_SCALE, style.getTextScale());
        settings.put(StyleDataStore.PK_GROUP_ROW_HEIGHT, style.isGroupRowUsesPreferredHeight());

        settings.put(StyleDataStore.PK_LIST_HEADER, style.getHeaderFieldVisibilityValue());
        settings.put(PK_LIST_FIELD_ORDER_BY, encodeBookLevelFieldsOrderBy(style));
        settings.put(PK_LIST_FIELD_VISIBILITY,
                     style.getFieldVisibility(FieldVisibility.Screen.List).getBitValue());

        settings.put(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST,
                     style.isSortAuthorByGivenName());
        settings.put(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST,
                     style.isShowAuthorByGivenName());

        settings.put(PK_DETAILS_FIELD_VISIBILITY,
                     style.getFieldVisibility(FieldVisibility.Screen.Detail).getBitValue());

        // Store them
        out.put(STYLE_SETTINGS, settings);

        return out;
    }

    private void encodeGroups(@NonNull final Style style,
                              @NonNull final JSONObject options) {

        options.put(StyleDataStore.PK_EXPANSION_LEVEL, style.getExpansionLevel());

        options.put(StyleDataStore.PK_GROUPS, new JSONArray(
                style.getGroupList()
                     .stream()
                     .map(BooklistGroup::getId)
                     .collect(Collectors.toList())));
        options.put(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE, style.getPrimaryAuthorType());
        for (final Style.UnderEach item : Style.UnderEach.values()) {
            options.put(item.getPrefKey(), style.isShowBooksUnderEachGroup(item.getGroupId()));
        }
    }

    @NonNull
    private JSONArray encodeBookLevelFieldsOrderBy(@NonNull final Style style)
            throws JSONException {
        final JSONArray columns = new JSONArray();
        style.getBookLevelFieldsOrderBy().forEach((columnName, sort) -> {
            final JSONObject column = new JSONObject();
            column.put(COLUMN_NAME, columnName);
            column.put(COLUMN_SORT, sort.name());
            columns.put(column);
        });

        return columns;
    }

    @NonNull
    @Override
    public Style decode(@NonNull final JSONObject data)
            throws JSONException {

        final String uuid = data.getString(DBKey.STYLE_UUID);

        final StyleType type;
        if (data.has(DBKey.STYLE_TYPE)) {
            // Version 5.1 archives store the type; just use it.
            type = StyleType.byId(data.getInt(DBKey.STYLE_TYPE));
        } else {
            // without a STYLE_TYPE, we're reading a version 5.0 or earlier
            // Use the UUID to check if we're reading a builtin Style.
            if (BuiltinStyle.isBuiltin(uuid)) {
                type = StyleType.Builtin;
            } else {
                type = StyleType.User;
            }
        }

        final Style style;
        switch (type) {
            case User: {
                style = UserStyle.createFromImport(uuid);
                if (data.has(DBKey.STYLE_NAME)) {
                    ((UserStyle) style).setName(data.getString(DBKey.STYLE_NAME));
                }
                break;
            }
            case Builtin: {
                final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
                style = stylesHelper.getStyle(uuid).orElseGet(
                        // It's a recognized Builtin Style, but it's deprecated.
                        // We return the default builtin style instead.
                        () -> stylesHelper.getStyle(BuiltinStyle.DEFAULT_UUID).orElseThrow());
                break;
            }
            case Global: {
                style = ServiceLocator.getInstance().getStyles().getGlobalStyle();
                break;
            }
            default:
                throw new IllegalArgumentException();
        }

        style.setPreferred(data.getBoolean(DBKey.STYLE_IS_PREFERRED));
        style.setMenuPosition(data.getInt(DBKey.STYLE_MENU_POSITION));

        if (data.has(STYLE_SETTINGS)) {
            // any element in the source which we don't know, will simply be ignored.
            final JSONObject source = data.getJSONObject(STYLE_SETTINGS);

            if (style.getType() == StyleType.User && source.has(StyleDataStore.PK_GROUPS)) {
                decodeGroups((UserStyle) style, source);
            }
            decodeSettings(source, style);
        }

        return style;
    }

    private void decodeSettings(@NonNull final JSONObject source,
                                @NonNull final Style style) {

        // It's either a StyleType.User or a StyleType.Global
        // TODO: casting to BaseStyle is not a nice thing to do...
        final BaseStyle baseStyle = (BaseStyle) style;

        if (source.has(StyleDataStore.PK_LAYOUT)) {
            baseStyle.setLayout(Style.Layout.byId(
                    source.getInt(StyleDataStore.PK_LAYOUT)));
        }
        if (source.has(StyleDataStore.PK_COVER_CLICK_ACTION)) {
            baseStyle.setCoverClickAction(Style.CoverClickAction.byId(
                    source.getInt(StyleDataStore.PK_COVER_CLICK_ACTION)));
        }
        if (source.has(StyleDataStore.PK_COVER_SCALE)) {
            baseStyle.setCoverScale(source.getInt(StyleDataStore.PK_COVER_SCALE));
        }
        if (source.has(StyleDataStore.PK_TEXT_SCALE)) {
            baseStyle.setTextScale(source.getInt(StyleDataStore.PK_TEXT_SCALE));
        }
        if (source.has(StyleDataStore.PK_GROUP_ROW_HEIGHT)) {
            baseStyle.setGroupRowUsesPreferredHeight(
                    source.getBoolean(StyleDataStore.PK_GROUP_ROW_HEIGHT));
        }

        if (source.has(StyleDataStore.PK_LIST_HEADER)) {
            baseStyle.setHeaderFieldVisibilityValue(
                    source.getInt(StyleDataStore.PK_LIST_HEADER));
        }
        if (source.has(PK_LIST_FIELD_VISIBILITY)) {
            style.getFieldVisibility(FieldVisibility.Screen.List)
                 .setBitValue(source.getLong(PK_LIST_FIELD_VISIBILITY));
        } else {
            // backwards compatibility
            decodeV2ListVisibility(style, source);
        }
        if (source.has(PK_LIST_FIELD_ORDER_BY)) {
            baseStyle.setBookLevelFieldsOrderBy(decodeBookLevelFieldsOrderBy(
                    source.getJSONArray(PK_LIST_FIELD_ORDER_BY)));
        }

        if (source.has(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST)) {
            baseStyle.setSortAuthorByGivenName(
                    source.getBoolean(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST));
        }
        if (source.has(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST)) {
            baseStyle.setShowAuthorByGivenName(
                    source.getBoolean(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST));
        }

        if (source.has(PK_DETAILS_FIELD_VISIBILITY)) {
            style.getFieldVisibility(FieldVisibility.Screen.Detail)
                 .setBitValue(source.getLong(PK_DETAILS_FIELD_VISIBILITY));
        } else {
            // backwards compatibility
            decodeV2DetailVisibility(style, source);
        }
    }

    private void decodeGroups(@NonNull final UserStyle userStyle,
                              @NonNull final JSONObject source)
            throws JSONException {

        if (source.has(StyleDataStore.PK_EXPANSION_LEVEL)) {
            userStyle.setExpansionLevel(
                    source.getInt(StyleDataStore.PK_EXPANSION_LEVEL));
        }

        final JSONArray groupArray = source.getJSONArray(StyleDataStore.PK_GROUPS);
        final List<Integer> groupIds = IntStream.range(0, groupArray.length())
                                                .mapToObj(groupArray::getInt)
                                                .collect(Collectors.toList());
        userStyle.setGroupIds(groupIds);

        if (source.has(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE)) {
            userStyle.setPrimaryAuthorType(
                    source.getInt(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE));
        }

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            if (source.has(item.getPrefKey())) {
                userStyle.setShowBooksUnderEachGroup(item.getGroupId(),
                                                     source.getBoolean(item.getPrefKey()));
            }
        }
    }

    @NonNull
    private Map<String, Sort> decodeBookLevelFieldsOrderBy(@NonNull final JSONArray columns)
            throws JSONException {
        final Map<String, Sort> result = new LinkedHashMap<>();

        for (int i = 0; i < columns.length(); i++) {
            final JSONObject column = columns.getJSONObject(i);
            final String name = column.getString(COLUMN_NAME);
            final String value = column.getString(COLUMN_SORT);
            result.put(name, Sort.valueOf(value));
        }
        return result;
    }

    private void decodeV2DetailVisibility(@NonNull final Style style,
                                          @NonNull final JSONObject source) {

        final FieldVisibility visibility = style.getFieldVisibility(FieldVisibility.Screen.Detail);

        if (source.has(V2.DETAILS_COVER[0])) {
            visibility.setVisible(DBKey.COVER[0], source.getBoolean(V2.DETAILS_COVER[0]));
        }
        if (source.has(V2.DETAILS_COVER[1])) {
            visibility.setVisible(DBKey.COVER[1], source.getBoolean(V2.DETAILS_COVER[1]));
        }
        // reminder: this is for backwards compatibility - don't add new fields here!
    }

    private void decodeV2ListVisibility(@NonNull final Style style,
                                        @NonNull final JSONObject source) {

        final FieldVisibility visibility = style.getFieldVisibility(FieldVisibility.Screen.List);

        if (source.has(V2.LIST_THUMBNAILS)) {
            visibility.setVisible(DBKey.COVER[0], source.getBoolean(V2.LIST_THUMBNAILS));
        }
        if (source.has(V2.LIST_AUTHOR)) {
            visibility.setVisible(DBKey.FK_AUTHOR, source.getBoolean(V2.LIST_AUTHOR));
        }
        if (source.has(V2.LIST_PUBLISHER)) {
            visibility.setVisible(DBKey.FK_PUBLISHER, source.getBoolean(V2.LIST_PUBLISHER));
        }
        if (source.has(V2.LIST_PUBLICATION_DATE)) {
            visibility.setVisible(DBKey.BOOK_PUBLICATION__DATE,
                                  source.getBoolean(V2.LIST_PUBLICATION_DATE));
        }
        if (source.has(V2.LIST_FORMAT)) {
            visibility.setVisible(DBKey.FORMAT, source.getBoolean(V2.LIST_FORMAT));
        }
        if (source.has(V2.LIST_LANGUAGE)) {
            visibility.setVisible(DBKey.LANGUAGE, source.getBoolean(V2.LIST_LANGUAGE));
        }
        if (source.has(V2.LIST_LOCATION)) {
            visibility.setVisible(DBKey.LOCATION, source.getBoolean(V2.LIST_LOCATION));
        }
        if (source.has(V2.LIST_RATING)) {
            visibility.setVisible(DBKey.RATING, source.getBoolean(V2.LIST_RATING));
        }
        if (source.has(V2.LIST_BOOKSHELVES)) {
            visibility.setVisible(DBKey.FK_BOOKSHELF, source.getBoolean(V2.LIST_BOOKSHELVES));
        }
        if (source.has(V2.LIST_ISBN)) {
            visibility.setVisible(DBKey.BOOK_ISBN, source.getBoolean(V2.LIST_ISBN));
        }
        // reminder: this is for backwards compatibility - don't add new fields here!
    }

    /**
     * Preference keys used by the old V2 version. Here for backwards compatibility
     * so we can still read older backups.
     */
    private static final class V2 {

        static final String[] DETAILS_COVER = {
                "style.details.show.thumbnail.0",
                "style.details.show.thumbnail.1",
        };
        private static final String LIST_AUTHOR = "style.booklist.show.author";
        private static final String LIST_PUBLISHER = "style.booklist.show.publisher";
        private static final String LIST_PUBLICATION_DATE = "style.booklist.show.publication.date";
        private static final String LIST_FORMAT = "style.booklist.show.format";
        private static final String LIST_LANGUAGE = "style.booklist.show.language";
        private static final String LIST_LOCATION = "style.booklist.show.location";
        private static final String LIST_RATING = "style.booklist.show.rating";
        private static final String LIST_BOOKSHELVES = "style.booklist.show.bookshelves";
        private static final String LIST_ISBN = "style.booklist.show.isbn";
        private static final String LIST_THUMBNAILS = "style.booklist.show.thumbnails";
    }

}
