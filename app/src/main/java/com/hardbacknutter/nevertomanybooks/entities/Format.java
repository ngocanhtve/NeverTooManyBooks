/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;

/**
 * System wide book format representation.
 * <p>
 * {@link DBDefinitions#DOM_BOOK_FORMAT}
 * <p>
 * Good description:  http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Format
 */
public final class Format {

    /** map to translate site book format' terminology with our own. */
    private static final Map<String, Integer> MAPPER = new HashMap<>();

    // use all lowercase keys!
    static {
        // ISFDB
        // mass market paperback
        MAPPER.put("mmpb", R.string.book_format_paperback);
        MAPPER.put("pb", R.string.book_format_paperback);
        MAPPER.put("tp", R.string.book_format_trade_paperback);
        MAPPER.put("hc", R.string.book_format_hardcover);
        MAPPER.put("ebook", R.string.book_format_ebook);
        MAPPER.put("digest", R.string.book_format_digest);
        MAPPER.put("audio cassette", R.string.book_format_audiobook);
        MAPPER.put("audio cd", R.string.book_format_audiobook);
        MAPPER.put("unknown", R.string.unknown);

        // Goodreads, not already listed above.
        MAPPER.put("mass market paperback", R.string.book_format_paperback);
        MAPPER.put("paperback", R.string.book_format_paperback);
        MAPPER.put("hardcover", R.string.book_format_hardcover);
    }

    private Format() {
    }

    /**
     * Try to map website terminology to our own localised.
     *
     * @param source string to map
     *
     * @return localized equivalent, or the source if no mapping exists.
     */
    public static String map(@NonNull final Context context,
                             @NonNull final String source) {

        Locale locale = context.getResources().getConfiguration().locale;
        if (locale == null) {
            locale = Locale.ENGLISH;
        }
        Integer resId = MAPPER.get(source.toLowerCase(locale));
        return resId != null ? context.getString(resId)
                             : source;
    }
}
