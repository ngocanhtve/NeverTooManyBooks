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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents an Author.
 * <p>
 * <strong>Note:</strong> "type" is a column of {@link DBDefinitions#TBL_BOOK_AUTHOR}
 * So this class does not strictly represent an Author, but a "BookAuthor"
 * When the type is disregarded, it is a real Author representation.
 * <p>
 * Author types:
 * <a href="http://www.loc.gov/marc/relators/relaterm.html">
 * http://www.loc.gov/marc/relators/relaterm.html</a>
 * <p>
 * ENHANCE: add a column 'real-name-id' with the id of another author entry
 * i.e one entry typed 'pseudonym' with the 'real-name-id' column pointing to the real name entry.
 */
public class Author
        implements ParcelableEntity, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Author> CREATOR = new Creator<>() {
        @Override
        public Author createFromParcel(@NonNull final Parcel source) {
            return new Author(source);
        }

        @Override
        public Author[] newArray(final int size) {
            return new Author[size];
        }
    };
    /** Generic Author; the default. A single person created the book. */
    public static final int TYPE_UNKNOWN = 0;

    /*
     * {@link DBDefinitions#DOM_BOOK_AUTHOR_TYPE_BITMASK}.
     * NEWTHINGS: author type: add a bit flag
     * Never change the bit value!
     */
    /** WRITER: primary or only writer. i.e. in contrast to any of the below. */
    public static final int TYPE_WRITER = 1;
    /** WRITER: not distinguished for now. If we do, use TYPE_ORIGINAL_SCRIPT_WRITER = 1 << 1; */
    public static final int TYPE_ORIGINAL_SCRIPT_WRITER = TYPE_WRITER;

    /** WRITER: the foreword. */
    public static final int TYPE_FOREWORD = 1 << 2;
    /** WRITER: the afterword. */
    public static final int TYPE_AFTERWORD = 1 << 3;
    /** WRITER: translator. */
    public static final int TYPE_TRANSLATOR = 1 << 4;
    /** WRITER: introduction. (some sites makes a distinction with a foreword). */
    public static final int TYPE_INTRODUCTION = 1 << 5;


    /** editor (e.g. of an anthology). */
    public static final int TYPE_EDITOR = 1 << 6;
    /** generic collaborator. */
    public static final int TYPE_CONTRIBUTOR = 1 << 7;


    /** ARTIST: cover. */
    public static final int TYPE_COVER_ARTIST = 1 << 8;
    /** ARTIST: cover inking (if different from above). */
    public static final int TYPE_COVER_INKING = 1 << 9;

    /** Audio books. */
    public static final int TYPE_NARRATOR = 1 << 10;

    /** COLOR: cover. */
    public static final int TYPE_COVER_COLORIST = 1 << 11;


    /** ARTIST: art work; could be illustrations, or the pages of a comic. */
    public static final int TYPE_ARTIST = 1 << 12;
    /** ARTIST: art work inking (if different from above). */
    public static final int TYPE_INKING = 1 << 13;

    // unused for now
    // public static final int TYPE_ = 1 << 14;

    /** COLOR: internal colorist. */
    public static final int TYPE_COLORIST = 1 << 15;

    /**
     * Any: indicate that this name entry is a pseudonym.
     * ENHANCE: pseudonym flag on its own this is not much use; need to link it with the real name
     */
    public static final int TYPE_PSEUDONYM = 1 << 16;

    /**
     * All valid bits for the type.
     * NEWTHINGS: author type: add to the mask
     */
    private static final int TYPE_BITMASK_ALL =
            TYPE_UNKNOWN
            | TYPE_WRITER | TYPE_ORIGINAL_SCRIPT_WRITER | TYPE_FOREWORD | TYPE_AFTERWORD
            | TYPE_TRANSLATOR | TYPE_INTRODUCTION | TYPE_EDITOR | TYPE_CONTRIBUTOR
            | TYPE_COVER_ARTIST | TYPE_COVER_INKING | TYPE_NARRATOR | TYPE_COVER_COLORIST
            | TYPE_ARTIST | TYPE_INKING | TYPE_COLORIST | TYPE_PSEUDONYM;

    /** This is the global setting! There is also a specific style-level setting. */
    private static final String PK_SHOW_AUTHOR_NAME_GIVEN_FIRST = "show.author.name.given_first";
    /** Maps the type-bit to a string resource for the type-label. */
    private static final Map<Integer, Integer> TYPES = new LinkedHashMap<>();

    /**
     * ENHANCE: author middle name; needs internationalisation ?
     * <p>
     * Ursula Le Guin
     * Marianne De Pierres
     * A. E. Van Vogt
     * Rip Von Ronkel
     */
    private static final Pattern FAMILY_NAME_PREFIX_PATTERN =
            Pattern.compile("(le|de|van|von)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * j/s lower or upper case
     * <p>
     * Foo Bar Jr.
     * Foo Bar Jr
     * Foo Bar Junior
     * Foo Bar Sr.
     * Foo Bar Sr
     * Foo Bar Senior
     * Foo Bar II
     * Charles Emerson Winchester III
     * <p>
     * same as above, but with comma:
     * Foo Bar, Jr.
     * <p>
     * Not covered yet, and seen in the wild:
     * "James jr. Tiptree" -> suffix as a middle name.
     * "Dr. Asimov" -> titles... pre or suffixed
     */
    private static final Pattern FAMILY_NAME_SUFFIX_PATTERN =
            Pattern.compile("jr\\.|jr|junior|sr\\.|sr|senior|II|III",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /*
     * NEWTHINGS: author type: add the label for the type
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        TYPES.put(TYPE_WRITER, R.string.lbl_author_type_writer);

        TYPES.put(TYPE_TRANSLATOR, R.string.lbl_author_type_translator);
        TYPES.put(TYPE_INTRODUCTION, R.string.lbl_author_type_intro);
        TYPES.put(TYPE_EDITOR, R.string.lbl_author_type_editor);
        TYPES.put(TYPE_CONTRIBUTOR, R.string.lbl_author_type_contributor);
        TYPES.put(TYPE_NARRATOR, R.string.lbl_author_type_narrator);

        TYPES.put(TYPE_ARTIST, R.string.lbl_author_type_artist);
        TYPES.put(TYPE_INKING, R.string.lbl_author_type_inking);
        TYPES.put(TYPE_COLORIST, R.string.lbl_author_type_colorist);

        TYPES.put(TYPE_COVER_ARTIST, R.string.lbl_author_type_cover_artist);
        TYPES.put(TYPE_COVER_INKING, R.string.lbl_author_type_cover_inking);
        TYPES.put(TYPE_COVER_COLORIST, R.string.lbl_author_type_cover_colorist);
    }

    /** Row ID. */
    private long id;
    /** Family name(s). */
    @NonNull
    private String familyName;
    /** Given name(s). */
    @NonNull
    private String givenNames;
    /** whether we have all we want from this Author. */
    private boolean complete;
    /** Bitmask. */
    @Type
    private int type = TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public Author(@NonNull final String familyName,
                  @NonNull final String givenNames) {
        this.familyName = familyName.trim();
        this.givenNames = givenNames.trim();
    }

    /**
     * Constructor.
     *
     * @param familyName Family name
     * @param givenNames Given names
     * @param isComplete whether an Author is completed, i.e if the user has all they
     *                   want from this Author.
     */
    public Author(@NonNull final String familyName,
                  @NonNull final String givenNames,
                  final boolean isComplete) {
        this.familyName = familyName.trim();
        this.givenNames = givenNames.trim();
        complete = isComplete;
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the Author in the database.
     * @param rowData with data
     */
    public Author(final long id,
                  @NonNull final DataHolder rowData) {
        this.id = id;
        familyName = rowData.getString(DBKey.AUTHOR_FAMILY_NAME);
        givenNames = rowData.getString(DBKey.AUTHOR_GIVEN_NAMES);
        complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);

        if (rowData.contains(DBKey.AUTHOR_TYPE__BITMASK)) {
            type = rowData.getInt(DBKey.AUTHOR_TYPE__BITMASK);
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Author(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection ConstantConditions
        familyName = in.readString();
        //noinspection ConstantConditions
        givenNames = in.readString();
        complete = in.readByte() != 0;
        type = in.readInt();
    }

    @NonNull
    public static Author createUnknownAuthor(@NonNull final Context context) {
        final String unknownAuthor = context.getString(R.string.unknown_author);
        return new Author(unknownAuthor, "");
    }

    /**
     * Parse a string into a family/given name pair.
     * If the string contains a comma (and the part after it is not a recognised suffix)
     * then the string is assumed to be in the format of "family, given-names"
     * All other formats are decoded as complete as possible.
     * <p>
     * Recognised pre/suffixes: see {@link #FAMILY_NAME_PREFIX_PATTERN}
     * and {@link #FAMILY_NAME_SUFFIX_PATTERN}
     * <ul>Not covered:
     *      <li>multiple, and not concatenated, family names.</li>
     *      <li>more than 1 un-encoded comma.</li>
     * </ul>
     *
     * <strong>Note:</strong> uses a simple String decoder.
     * Any complex decoding for JSON format must be done before calling here.
     *
     * @param name a String containing the name
     *
     * @return Author
     */
    @NonNull
    public static Author from(@NonNull final String name) {
        String uName = ParseUtils.unEscape(name);

        // take into account that there can be escaped commas....
        // do we really need this except when reading from a backup ?
        final List<String> tmp = StringList.newInstance().decode(uName, ',', true);
        if (tmp.size() > 1) {
            final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(tmp.get(1));
            if (suffixMatcher.find()) {
                // concatenate without the comma. Further processing will take care of the suffix.
                uName = tmp.get(0) + ' ' + tmp.get(1);
            } else {
                // not a suffix, assume the names are already formatted.
                return new Author(tmp.get(0), tmp.get(1));
            }
        }

        final String[] names = uName.split(" ");
        // two easy cases
        switch (names.length) {
            case 1:
                return new Author(names[0], "");
            case 2:
                return new Author(names[1], names[0]);
            default:
                break;
        }

        // we have 3 or more parts, check the family name for suffixes and prefixes
        final StringBuilder buildFamilyName = new StringBuilder();
        // the position to check, start at the end.
        int pos = names.length - 1;

        final Matcher suffixMatcher = FAMILY_NAME_SUFFIX_PATTERN.matcher(names[pos]);
        if (suffixMatcher.find()) {
            // suffix and the element before it are part of the last name.
            buildFamilyName.append(names[pos - 1]).append(' ').append(names[pos]);
            pos -= 2;
        } else {
            // no suffix.
            buildFamilyName.append(names[pos]);
            pos--;
        }

        // the last name could also have a prefix
        final Matcher middleNameMatcher = FAMILY_NAME_PREFIX_PATTERN.matcher(names[pos]);
        if (middleNameMatcher.find()) {
            // insert it at the front of the family name
            buildFamilyName.insert(0, names[pos] + ' ');
            pos--;
        }

        // everything else are considered given names
        final StringBuilder buildGivenNames = new StringBuilder();
        for (int i = 0; i <= pos; i++) {
            buildGivenNames.append(names[i]).append(' ');
        }

        return new Author(buildFamilyName.toString(), buildGivenNames.toString());
    }

    /**
     * Get the Authors. If there is more than one, we get the first Author + an ellipsis.
     *
     * @param context Current context
     * @param authors list to condense
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public static String getCondensedNames(@NonNull final Context context,
                                           @NonNull final List<Author> authors) {
        // could/should? use ListFormatter
        if (authors.isEmpty()) {
            return "";
        } else {
            final String text = authors.get(0).getLabel(context);
            if (authors.size() > 1) {
                return context.getString(R.string.and_others, text);
            }
            return text;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(familyName);
        dest.writeString(givenNames);
        dest.writeByte((byte) (complete ? 1 : 0));
        dest.writeInt(type);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the 'complete' status of the Author.
     *
     * @return {@code true} if the Author is complete
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Set the 'complete' status of the Author.
     *
     * @param isComplete Flag indicating the user considers this Author to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        complete = isComplete;
    }

    @Type
    public int getType() {
        return type;
    }

    /**
     * Set the type(s).
     *
     * @param type to set
     */
    public void setType(@Type final int type) {
        this.type = type & TYPE_BITMASK_ALL;
    }

    /**
     * Add a type to the current type(s).
     *
     * @param type to add
     */
    public void addType(@Type final int type) {
        this.type |= type & TYPE_BITMASK_ALL;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getLabel(context, Details.Normal, null);
    }

    /**
     * Get the label to use for <strong>displaying</strong>.
     *
     * <ul>
     *     <li>{@link Details#Full}: standard formatted name combined
     *          (if enabled) with the author type. The latter uses HTML formatting.
     *     </li>
     *     <li>{@link Details#Normal}, {@link Details#Auto}: standard formatted name.</li>
     *     <li>{@link Details#Short}: initial + family-name</li>
     * </ul>
     *
     * @param context Current context
     * @param details the amount of details wanted
     * @param style   (optional) to use
     *
     * @return the label to use.
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @Nullable final Style style) {
        final boolean givenFirst;
        if (style != null) {
            givenFirst = style.isShowAuthorByGivenName();
        } else {
            givenFirst = PreferenceManager.getDefaultSharedPreferences(context)
                                          .getBoolean(PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false);
        }

        switch (details) {
            case Full: {
                String label = getFormattedName(givenFirst);

                if (GlobalFieldVisibility.isUsed(DBKey.AUTHOR_TYPE__BITMASK)) {
                    final String typeLabels = getTypeLabels(context);
                    if (!typeLabels.isEmpty()) {
                        label += " <small><i>" + typeLabels + "</i></small>";
                    }
                }
                return label;
            }
            case Auto:
            case Normal: {
                return getFormattedName(givenFirst);
            }
            case Short: {
                if (givenNames.isEmpty()) {
                    return familyName;
                } else if (givenFirst) {
                    return givenNames.substring(0, 1) + ' ' + familyName;
                } else {
                    return familyName + ' ' + givenNames.charAt(0);
                }
            }
            default:
                throw new IllegalArgumentException("details=" + details);
        }
    }

    /**
     * Return the <strong>specified</strong> 'human readable' version of the name.
     *
     * @param givenNameFirst {@code true} if we want "given-names family-name" formatted name.
     *                       {@code false} for "last-family, first-names"
     *
     * @return formatted name
     */
    @NonNull
    public String getFormattedName(final boolean givenNameFirst) {
        if (givenNames.isEmpty()) {
            return familyName;
        } else {
            if (givenNameFirst) {
                return givenNames + ' ' + familyName;
            } else {
                return familyName + ", " + givenNames;
            }
        }
    }

    /**
     * Get a CSV string with the type of this author; or the empty string if no specific types
     * are set.
     *
     * @param context Current context
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    private String getTypeLabels(@NonNull final Context context) {
        if (type != TYPE_UNKNOWN) {
            final List<String> list = TYPES.entrySet()
                                           .stream()
                                           .filter(entry -> (entry.getKey() & (long) type) != 0)
                                           .map(entry -> context.getString(entry.getValue()))
                                           .collect(Collectors.toList());

            if (!list.isEmpty()) {
                return context.getString(R.string.brackets, String.join(", ", list));
            }
        }
        return "";
    }

    /**
     * Syntax sugar to set the names in one call.
     *
     * @param familyName Family name
     * @param givenNames Given names
     */
    public void setName(@NonNull final String familyName,
                        @NonNull final String givenNames) {
        this.familyName = familyName;
        this.givenNames = givenNames;
    }

    @NonNull
    public String getFamilyName() {
        return familyName;
    }

    /**
     * Get the given name ('first' name) of the Author.
     * Will be {@code ""} if unknown.
     *
     * @return given-name
     */
    @NonNull
    public String getGivenNames() {
        return givenNames;
    }

    /**
     * Replace local details from another author.
     *
     * @param source            Author to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Author source,
                         final boolean includeBookFields) {
        familyName = source.familyName;
        givenNames = source.givenNames;
        complete = source.complete;
        if (includeBookFields) {
            type = source.type;
        }
    }

    /**
     * Get the Locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     * <p>
     * An item not using a locale, should just returns the fallbackLocale itself.
     *
     * @param context    Current context
     * @param userLocale Locale to use if the item does not have a Locale of its own.
     *
     * @return the item Locale, or the userLocale.
     */
    @Override
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale userLocale) {
        //ENHANCE: The Author Locale should be based on the main language the author writes in.
        return userLocale;
    }

    @Override
    public boolean merge(@NonNull final Mergeable mergeable) {
        final Author incoming = (Author) mergeable;

        // always combine the types
        type = type | incoming.getType();

        // if this object has no id, and the incoming has an id, then we copy the id.
        if (id == 0 && incoming.getId() > 0) {
            id = incoming.getId();
        }
        return true;
    }

    /**
     * Diacritic neutral version of {@link  #hashCode()} without id.
     *
     * @return hashcode
     */
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(familyName), ParseUtils.toAscii(givenNames));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, familyName, givenNames);
    }

    /**
     * Equality: <strong>id, family and given-names</strong>.
     * <ul>
     *   <li>'type' is on a per book basis. See {@link AuthorDao#pruneList}.</li>
     *   <li>'isComplete' is a user setting and is ignored.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Author that = (Author) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(familyName, that.familyName)
               && Objects.equals(givenNames, that.givenNames);
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner sj = new StringJoiner(",", "Type{", "}");

        if ((type & TYPE_WRITER) != 0) {
            sj.add("TYPE_WRITER");
        }
        //        if ((mType & TYPE_ORIGINAL_SCRIPT_WRITER) != 0) {
        //            sj.add("TYPE_ORIGINAL_SCRIPT_WRITER");
        //        }
        if ((type & TYPE_FOREWORD) != 0) {
            sj.add("TYPE_FOREWORD");
        }
        if ((type & TYPE_AFTERWORD) != 0) {
            sj.add("TYPE_AFTERWORD");
        }


        if ((type & TYPE_TRANSLATOR) != 0) {
            sj.add("TYPE_TRANSLATOR");
        }
        if ((type & TYPE_INTRODUCTION) != 0) {
            sj.add("TYPE_INTRODUCTION");
        }
        if ((type & TYPE_EDITOR) != 0) {
            sj.add("TYPE_EDITOR");
        }
        if ((type & TYPE_CONTRIBUTOR) != 0) {
            sj.add("TYPE_CONTRIBUTOR");
        }


        if ((type & TYPE_COVER_ARTIST) != 0) {
            sj.add("TYPE_COVER_ARTIST");
        }
        if ((type & TYPE_COVER_INKING) != 0) {
            sj.add("TYPE_COVER_INKING");
        }
        if ((type & TYPE_NARRATOR) != 0) {
            sj.add("TYPE_NARRATOR");
        }
        if ((type & TYPE_COVER_COLORIST) != 0) {
            sj.add("TYPE_COVER_COLORIST");
        }


        if ((type & TYPE_ARTIST) != 0) {
            sj.add("TYPE_ARTIST");
        }
        if ((type & TYPE_INKING) != 0) {
            sj.add("TYPE_INKING");
        }
        if ((type & TYPE_COLORIST) != 0) {
            sj.add("TYPE_COLORIST");
        }
        if ((type & TYPE_PSEUDONYM) != 0) {
            sj.add("TYPE_PSEUDONYM");
        }

        return "Author{"
               + "id=" + id
               + ", familyName=`" + familyName + '`'
               + ", givenNames=`" + givenNames + '`'
               + ", complete=" + complete
               + ", type=0b" + Integer.toBinaryString(type) + ": " + sj
               + '}';
    }

    // NEWTHINGS: author type: add to the IntDef
    @IntDef(flag = true,
            value = {TYPE_UNKNOWN,
                     TYPE_WRITER, TYPE_FOREWORD, TYPE_AFTERWORD,
                     TYPE_TRANSLATOR, TYPE_INTRODUCTION, TYPE_EDITOR, TYPE_CONTRIBUTOR,
                     TYPE_COVER_ARTIST, TYPE_COVER_INKING, TYPE_NARRATOR, TYPE_COVER_COLORIST,
                     TYPE_ARTIST, TYPE_INKING, TYPE_COLORIST, TYPE_PSEUDONYM
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }
}
