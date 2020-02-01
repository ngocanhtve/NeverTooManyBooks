/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_AFTERWORD;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_ARTIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COLORIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_CONTRIBUTOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COVER_ARTIST;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_COVER_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_EDITOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_FOREWORD;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_INKING;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_ORIGINAL_SCRIPT_WRITER;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_PSEUDONYM;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_TRANSLATOR;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_UNKNOWN;
import static com.hardbacknutter.nevertoomanybooks.entities.Author.TYPE_WRITER;

/**
 * Translate Goodreads author types (roles) into our native type codes.
 * <p>
 * Not based on MapperBase, as the value is a real integer
 * and not a resource id.
 */
public class AuthorTypeMapper {

    /** Log tag. */
    private static final String TAG = "AuthorTypeMapper";

    private static final Map<String, Integer> MAPPER = new HashMap<>();

    // use all lowercase keys (unless they are diacritic)
    static {
        // English
        MAPPER.put("author", TYPE_WRITER);
        MAPPER.put("original script writer", TYPE_ORIGINAL_SCRIPT_WRITER);
        MAPPER.put("adapter", TYPE_WRITER);

        MAPPER.put("illuminator", TYPE_ARTIST);
        MAPPER.put("illustrator", TYPE_ARTIST);
        MAPPER.put("illustrations", TYPE_ARTIST);
        MAPPER.put("colorist", TYPE_COLORIST);
        MAPPER.put("coverart", TYPE_COVER_ARTIST);
        MAPPER.put("cover artist", TYPE_COVER_ARTIST);
        MAPPER.put("cover illustrator", TYPE_COVER_ARTIST);

        MAPPER.put("pseudonym", TYPE_PSEUDONYM);
        MAPPER.put("editor", TYPE_EDITOR);

        MAPPER.put("translator", TYPE_TRANSLATOR);
        MAPPER.put("translator, annotations", TYPE_TRANSLATOR | TYPE_CONTRIBUTOR);

        MAPPER.put("preface", TYPE_FOREWORD);
        MAPPER.put("foreword", TYPE_FOREWORD);
        MAPPER.put("foreword by", TYPE_FOREWORD);
        MAPPER.put("afterword", TYPE_AFTERWORD);

        MAPPER.put("contributor", TYPE_CONTRIBUTOR);
        MAPPER.put("additional material", TYPE_CONTRIBUTOR);

        // French, unless listed above
        MAPPER.put("text", TYPE_WRITER);
        MAPPER.put("scénario", TYPE_WRITER);
        MAPPER.put("dessins", TYPE_ARTIST);
        MAPPER.put("dessin", TYPE_ARTIST);
        MAPPER.put("avec la contribution de", TYPE_CONTRIBUTOR);
        MAPPER.put("contribution", TYPE_CONTRIBUTOR);
        MAPPER.put("couleurs", TYPE_COLORIST);

        // Dutch, unless listed above
        MAPPER.put("scenario", TYPE_WRITER);
        MAPPER.put("tekeningen", TYPE_ARTIST);
        MAPPER.put("inkting", TYPE_INKING);
        MAPPER.put("inkting cover", TYPE_COVER_INKING);
        MAPPER.put("inkleuring", TYPE_COLORIST);
        MAPPER.put("vertaler", TYPE_TRANSLATOR);

        // German, unless listed above
        MAPPER.put("Übersetzer", TYPE_TRANSLATOR);

        // Italian, unless listed above
        MAPPER.put("testi", TYPE_WRITER);
        MAPPER.put("disegni", TYPE_ARTIST);

        // Current (2020-01-30) strings have been seen on Goodreads.
        // There are obviously MANY missing.... both for the listed languages above and for
        // other languages not even considered here.
        // Will need to add them when/as they show up.
        // Maybe better if this is done in an external file on a per language basis ?
        // Maybe some day see if we can pull a full list from Goodreads?

        // More Goodreads:
        // Visual Art
        // Design
    }

    public static int map(@NonNull final Locale locale,
                          @NonNull final String typeName) {
        Integer mapped = MAPPER.get(typeName.toLowerCase(locale).trim());
        if (mapped != null) {
            return mapped;
        }

        // unknown, log it for future enhancement.
        Logger.warn(App.getAppContext(), TAG, "map|typeName=`" + typeName + "`");
        return TYPE_UNKNOWN;
    }

    private AuthorTypeMapper() {
    }
}
