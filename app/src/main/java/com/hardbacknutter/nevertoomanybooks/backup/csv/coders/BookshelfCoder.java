/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * StringList factory for a Bookshelf.
 * <ul>Format:
 *      <li>shelfName * {json}</li>
 * </ul>
 *
 * <strong>Note:</strong> In the format definition, the " * {json}" suffix is optional
 * and can be missing.
 */
public class BookshelfCoder
        implements StringList.Coder<Bookshelf> {

    private static final char[] ESCAPE_CHARS = {'(', ')'};
    @NonNull
    private final ListStyle mDefaultStyle;

    /**
     * Constructor.
     *
     * @param defaultStyle to use for bookshelves without a style set.
     */
    BookshelfCoder(@NonNull final ListStyle defaultStyle) {
        mDefaultStyle = defaultStyle;
    }

    /**
     * Backwards compatibility rules ',' (not using the default '|').
     */
    @Override
    public char getElementSeparator() {
        return ',';
    }

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    @NonNull
    @Override
    public String encode(@NonNull final Bookshelf bookshelf) {
        String result = escape(bookshelf.getName(), ESCAPE_CHARS);

        final JSONObject details = new JSONObject();
        try {
            if (!bookshelf.getStyleUuid().isEmpty()) {
                details.put(DBDefinitions.KEY_FK_STYLE, bookshelf.getStyleUuid());
            }
        } catch (@NonNull final JSONException e) {
            throw new IllegalStateException(e);
        }

        if (details.length() != 0) {
            result += ' ' + String.valueOf(getObjectSeparator()) + ' ' + details.toString();
        }
        return result;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        final Bookshelf bookshelf = new Bookshelf(parts.get(0), mDefaultStyle);
        if (parts.size() > 1) {
            try {
                final JSONObject details = new JSONObject(parts.get(1));
                // It's quite possible that the UUID is not a style we (currently) know.
                // But that does not matter as we'll check it upon first access.
                if (details.has(DBDefinitions.KEY_FK_STYLE)) {
                    bookshelf.setStyleUuid(details.optString(DBDefinitions.KEY_FK_STYLE));
                } else if (details.has("style")) {
                    bookshelf.setStyleUuid(details.optString("style"));
                }
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return bookshelf;
    }
}
