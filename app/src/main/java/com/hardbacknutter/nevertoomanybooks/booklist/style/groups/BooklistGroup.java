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

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_ADDED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LAST_UPDATED__UTC;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with a {@link GroupKey},
 * the latter providing a lightweight (final) object without user preferences.
 * The BooklistGroup encapsulates the {@link GroupKey}, adding user/temp stuff.
 * <p>
 * <p>
 * How to add a new Group:
 * <ol>
 *      <li>add it to {@link GroupKey} and update {@link #GROUP_KEY_MAX}</li>
 *      <li>if necessary add new domain to {@link DBDefinitions}</li>
 *      <li>modify {@link BooksOnBookshelfViewModel}
 *          to add the necessary grouped/sorted domains</li>
 *      <li>modify {@link BooklistAdapter#onCreateViewHolder}; If it is just a string field it can
 *          use a {@link BooklistAdapter}.GenericStringHolder otherwise add a new holder</li>
 * </ol>
 */
public class BooklistGroup {

    /**
     * The ID's for the groups.
     * <strong>Never change these</strong>, they get stored in the db.
     * <p>
     * Also: the code relies on BOOK being == 0
     */
    public static final int BOOK = 0;
    /** {@link AuthorBooklistGroup}. */
    public static final int AUTHOR = 1;
    /** {@link SeriesBooklistGroup}. */
    public static final int SERIES = 2;
    public static final int GENRE = 3;
    /** {@link PublisherBooklistGroup}. */
    public static final int PUBLISHER = 4;
    public static final int READ_STATUS = 5;
    public static final int LENDING = 6;
    public static final int DATE_PUBLISHED_YEAR = 7;
    public static final int DATE_PUBLISHED_MONTH = 8;
    public static final int BOOK_TITLE_1ST_LETTER = 9;
    public static final int DATE_ADDED_YEAR = 10;
    public static final int DATE_ADDED_MONTH = 11;
    public static final int DATE_ADDED_DAY = 12;
    public static final int FORMAT = 13;
    public static final int DATE_READ_YEAR = 14;
    public static final int DATE_READ_MONTH = 15;
    public static final int DATE_READ_DAY = 16;
    public static final int LOCATION = 17;
    public static final int LANGUAGE = 18;
    public static final int DATE_LAST_UPDATE_YEAR = 19;
    public static final int DATE_LAST_UPDATE_MONTH = 20;
    public static final int DATE_LAST_UPDATE_DAY = 21;
    public static final int RATING = 22;
    /** {@link BookshelfBooklistGroup}. */
    public static final int BOOKSHELF = 23;
    public static final int DATE_ACQUIRED_YEAR = 24;
    public static final int DATE_ACQUIRED_MONTH = 25;
    public static final int DATE_ACQUIRED_DAY = 26;
    public static final int DATE_FIRST_PUBLICATION_YEAR = 27;
    public static final int DATE_FIRST_PUBLICATION_MONTH = 28;
    public static final int COLOR = 29;
    @SuppressWarnings("WeakerAccess")
    public static final int SERIES_TITLE_1ST_LETTER = 30;
    public static final int CONDITION = 31;

    /**
     * NEWTHINGS: BooklistGroup.KEY
     * The highest valid index of GroupKey - ALWAYS to be updated after adding a group key.
     */
    @VisibleForTesting
    public static final int GROUP_KEY_MAX = 31;

    // Date based groups have to sort on the full date for cases
    // where we don't have all separate year,month,day fields.
    private static final DomainExpression DATE_PUBLISHED =
            new DomainExpression(DOM_BOOK_DATE_PUBLISHED, null, DomainExpression.Sort.Desc);
    private static final DomainExpression DATE_FIRST_PUBLICATION =
            new DomainExpression(DOM_DATE_FIRST_PUBLICATION, null, DomainExpression.Sort.Desc);
    private static final DomainExpression BOOK_IS_READ =
            new DomainExpression(DOM_BOOK_READ, null, DomainExpression.Sort.Desc);
    private static final DomainExpression DATE_READ_END =
            new DomainExpression(DOM_BOOK_DATE_READ_END, null, DomainExpression.Sort.Desc);
    private static final DomainExpression DATE_ADDED =
            new DomainExpression(DOM_ADDED__UTC, null, DomainExpression.Sort.Desc);
    private static final DomainExpression DATE_LAST_UPDATED =
            new DomainExpression(DOM_LAST_UPDATED__UTC, null, DomainExpression.Sort.Desc);
    private static final DomainExpression DATE_ACQUIRED =
            new DomainExpression(DOM_BOOK_DATE_ACQUIRED, null, DomainExpression.Sort.Desc);

    private static final String CASE_WHEN_ = "CASE WHEN ";
    private static final String _WHEN_ = " WHEN ";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";
    /** Cache for the static GroupKey instances. */
    private static final Map<Integer, GroupKey> GROUP_KEYS = new UniqueMap<>();

    /** The type of row/group we represent, see {@link GroupKey}. */
    @Id
    private final int id;
    /** The underlying group key object. */
    @NonNull
    private final GroupKey groupKey;
    /**
     * The domains represented by this group.
     * Set at <strong>runtime</strong> by the BooklistBuilder
     * based on current group <strong>and its outer groups</strong>
     */
    @Nullable
    private ArrayList<Domain> accumulatedDomains;

    /**
     * Constructor.
     *
     * @param id of group to create
     */
    BooklistGroup(@Id final int id) {
        this.id = id;
        groupKey = initGroupKey();
    }

    /**
     * Copy constructor.
     *
     * @param group to copy from
     */
    @SuppressWarnings("CopyConstructorMissesField")
    public BooklistGroup(@NonNull final BooklistGroup group) {
        id = group.id;
        groupKey = group.groupKey;
    }

    /**
     * Create a new BooklistGroup of the specified id, creating any specific
     * subclasses as necessary.
     *
     * @param id    of group to create
     * @param style Style reference.
     *
     * @return instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public static BooklistGroup newInstance(@Id final int id,
                                            @NonNull final Style style) {
        switch (id) {
            case AUTHOR:
                return new AuthorBooklistGroup(style);
            case SERIES:
                return new SeriesBooklistGroup(style);
            case PUBLISHER:
                return new PublisherBooklistGroup(style);
            case BOOKSHELF:
                return new BookshelfBooklistGroup(style);

            default:
                return new BooklistGroup(id);
        }
    }

    /**
     * Get a list of BooklistGroup's, one for each defined {@link GroupKey}'s.
     * This <strong>excludes</strong> the Book key itself.
     *
     * @param style Style reference.
     *
     * @return the list
     */
    @SuppressLint("WrongConstant")
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final Style style) {
        final List<BooklistGroup> list = new ArrayList<>();
        for (int id = 1; id <= GROUP_KEY_MAX; id++) {
            list.add(newInstance(id, style));
        }
        return list;
    }

    /**
     * If the field has a time part, convert it to local time.
     * This deals with legacy 'date-only' dates.
     * The logic being that IF they had a time part it would be UTC.
     * Without a time part, we assume the zone is local (or irrelevant).
     *
     * @param fieldSpec fully qualified field name
     *
     * @return expression
     */
    @NonNull
    private static String localDateTimeExpression(@NonNull final String fieldSpec) {
        return CASE_WHEN_ + fieldSpec + " GLOB '*-*-* *' "
               + " THEN datetime(" + fieldSpec + ", 'localtime')"
               + _ELSE_ + fieldSpec
               + _END;
    }

    /**
     * General remark on the use of GLOB instead of 'strftime(format, date)':
     * strftime() only works on full date(time) strings. i.e. 'YYYY-MM-DD*'
     * for all other formats, it will fail to extract the fields.
     * <p>
     * Create a GLOB expression to get the 'year' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers. We don't care about anything else.
     * <p>
     * See <a href="https://www.sqlitetutorial.net/sqlite-glob/">sqlite-glob</a>
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String year(@NonNull String fieldSpec,
                               final boolean toLocal) {

        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",1,4)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create a GLOB expression to get the 'month' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String month(@NonNull String fieldSpec,
                                final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        // YYYY-MM or YYYY-M
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",6,2)"
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",6,1)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create a GLOB expression to get the 'day' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit,
     * and then by '-' and 1 or two digits.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   if set, first convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private static String day(@NonNull String fieldSpec,
                              @SuppressWarnings("SameParameterValue") final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateTimeExpression(fieldSpec);
        }
        // Look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
        // YYYY-MM-DD or YYYY-M-DD or YYYY-MM-D or YYYY-M-D
        return CASE_WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",9,2)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",8,2)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",9,1)"
               //
               + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'"
               + " THEN SUBSTR(" + fieldSpec + ",8,1)"
               // invalid
               + " ELSE ''"
               + _END;
    }

    /**
     * Create/get a GroupKey. We create the keys only once and keep them in a static cache map.
     * This must be called <strong>after</strong> construction, i.e. from {@link #newInstance}.
     */
    @NonNull
    private GroupKey initGroupKey() {
        GroupKey key = GROUP_KEYS.get(id);
        if (key == null) {
            key = createGroupKey();
            GROUP_KEYS.put(id, key);
        }
        return key;
    }

    /**
     * GroupKey factory constructor. Called <strong>ONCE</strong> for each group
     * during the lifetime of the app.
     * <p>
     * Specialized classes override this method. e.g. {@link AuthorBooklistGroup}.
     *
     * @return new GroupKey instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public GroupKey createGroupKey() {
        // NEWTHINGS: BooklistGroup.KEY
        switch (id) {
            // Data without a linked table uses the display name as the key domain.
            case COLOR: {
                return new GroupKey(R.string.lbl_color, "col",
                                    new DomainExpression(DOM_BOOK_COLOR,
                                                         TBL_BOOKS.dot(DBKey.COLOR),
                                                         DomainExpression.Sort.Asc));
            }
            case FORMAT: {
                return new GroupKey(R.string.lbl_format, "fmt",
                                    new DomainExpression(DOM_BOOK_FORMAT,
                                                         TBL_BOOKS.dot(DBKey.FORMAT),
                                                         DomainExpression.Sort.Asc));
            }
            case GENRE: {
                return new GroupKey(R.string.lbl_genre, "g",
                                    new DomainExpression(DOM_BOOK_GENRE,
                                                         TBL_BOOKS.dot(DBKey.GENRE),
                                                         DomainExpression.Sort.Asc));
            }
            case LANGUAGE: {
                // Formatting is done after fetching.
                return new GroupKey(R.string.lbl_language, "lng",
                                    new DomainExpression(DOM_BOOK_LANGUAGE,
                                                         TBL_BOOKS.dot(DBKey.LANGUAGE),
                                                         DomainExpression.Sort.Asc));
            }
            case LOCATION: {
                return new GroupKey(R.string.lbl_location, "loc",
                                    new DomainExpression(DOM_BOOK_LOCATION,
                                                         TBL_BOOKS.dot(DBKey.LOCATION),
                                                         DomainExpression.Sort.Asc));
            }
            case CONDITION: {
                return new GroupKey(R.string.lbl_condition, "bk_cnd",
                                    new DomainExpression(DOM_BOOK_CONDITION,
                                                         TBL_BOOKS.dot(DBKey.BOOK_CONDITION),
                                                         DomainExpression.Sort.Desc));
            }
            case RATING: {
                // Formatting is done after fetching
                // Sort with highest rated first
                // The data is cast to an integer as a precaution.
                return new GroupKey(R.string.lbl_rating, "rt",
                                    new DomainExpression(DOM_BOOK_RATING,
                                                         "CAST(" + TBL_BOOKS.dot(DBKey.RATING)
                                                         + " AS INTEGER)",
                                                         DomainExpression.Sort.Desc));
            }
            case LENDING: {
                return new GroupKey(R.string.lbl_lend_out, "l",
                                    new DomainExpression(DOM_LOANEE,
                                                         "COALESCE(" + TBL_BOOK_LOANEE.dot(
                                                                 DBKey.LOANEE_NAME) + ",'')",
                                                         DomainExpression.Sort.Asc));
            }

            // the others here below are custom key domains
            case READ_STATUS: {
                // Formatting is done after fetching.
                return new GroupKey(R.string.lbl_read_and_unread, "r",
                                    new DomainExpression(
                                            new Domain.Builder("blg_rd_sts",
                                                               SqLiteDataType.Text)
                                                    .notNull()
                                                    .build(),
                                            TBL_BOOKS.dot(DBKey.READ__BOOL),
                                            DomainExpression.Sort.Asc));
            }
            case BOOK_TITLE_1ST_LETTER: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_tit_let", SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_BOOKS.dot(DBKey.TITLE_OB) + ",1,1))",
                        DomainExpression.Sort.Asc);
                return new GroupKey(R.string.style_builtin_first_letter_book_title, "t",
                                    keyDomain);
            }
            case SERIES_TITLE_1ST_LETTER: {
                // Uses the OrderBy column so we get the re-ordered version if applicable.
                // Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_ser_tit_let", SqLiteDataType.Text)
                                .notNull()
                                .build(),
                        "UPPER(SUBSTR(" + TBL_SERIES.dot(DBKey.SERIES_TITLE_OB) + ",1,1))",
                        DomainExpression.Sort.Asc);
                return new GroupKey(R.string.style_builtin_first_letter_series_title, "st",
                                    keyDomain);
            }

            case DATE_PUBLISHED_YEAR: {
                // UTC. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_pub_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.BOOK_PUBLICATION__DATE), false),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_publication_year, "yrp", keyDomain)
                        .addBaseDomain(DATE_PUBLISHED);
            }
            case DATE_PUBLISHED_MONTH: {
                // UTC. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_pub_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.BOOK_PUBLICATION__DATE), false),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_publication_month, "mp", keyDomain)
                        .addBaseDomain(DATE_PUBLISHED);
            }


            case DATE_FIRST_PUBLICATION_YEAR: {
                // UTC. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_1pub_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION__DATE), false),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_first_pub_year, "yfp", keyDomain)
                        .addBaseDomain(DATE_FIRST_PUBLICATION);
            }
            case DATE_FIRST_PUBLICATION_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_1pub_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION__DATE), false),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_first_pub_month, "mfp", keyDomain)
                        .addBaseDomain(DATE_FIRST_PUBLICATION);
            }


            case DATE_ACQUIRED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_acq_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_date_acquired_year, "yac", keyDomain)
                        .addBaseDomain(DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_acq_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_date_acquired_month, "mac", keyDomain)
                        .addBaseDomain(DATE_ACQUIRED);
            }
            case DATE_ACQUIRED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_acq_d", SqLiteDataType.Integer).build(),
                        day(TBL_BOOKS.dot(DBKey.DATE_ACQUIRED), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_date_acquired_day, "dac", keyDomain)
                        .addBaseDomain(DATE_ACQUIRED);
            }


            case DATE_ADDED_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_add_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_added_year, "ya", keyDomain)
                        .addBaseDomain(DATE_ADDED);
            }
            case DATE_ADDED_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_add_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_added_month, "ma", keyDomain)
                        .addBaseDomain(DATE_ADDED);
            }
            case DATE_ADDED_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_add_d", SqLiteDataType.Integer).build(),
                        day(TBL_BOOKS.dot(DBKey.DATE_ADDED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_added_day, "da", keyDomain)
                        .addBaseDomain(DATE_ADDED);
            }


            case DATE_LAST_UPDATE_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_upd_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_update_year, "yu", keyDomain)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_upd_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_update_month, "mu", keyDomain)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }
            case DATE_LAST_UPDATE_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_upd_d", SqLiteDataType.Integer).build(),
                        day(TBL_BOOKS.dot(DBKey.DATE_LAST_UPDATED__UTC), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_update_day, "du", keyDomain)
                        .addBaseDomain(DATE_LAST_UPDATED);
            }


            case DATE_READ_YEAR: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_rd_y", SqLiteDataType.Integer).build(),
                        year(TBL_BOOKS.dot(DBKey.READ_END__DATE), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_read_year, "yr", keyDomain)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }
            case DATE_READ_MONTH: {
                // Local for the user. Formatting is done after fetching.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_rd_m", SqLiteDataType.Integer).build(),
                        month(TBL_BOOKS.dot(DBKey.READ_END__DATE), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_read_month, "mr", keyDomain)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }
            case DATE_READ_DAY: {
                // Local for the user. Formatting is done in the sql expression.
                final DomainExpression keyDomain = new DomainExpression(
                        new Domain.Builder("blg_rd_d", SqLiteDataType.Integer).build(),
                        day(TBL_BOOKS.dot(DBKey.READ_END__DATE), true),
                        DomainExpression.Sort.Desc);
                return new GroupKey(R.string.lbl_read_day, "dr", keyDomain)
                        .addBaseDomain(DATE_READ_END)
                        .addGroupDomain(BOOK_IS_READ);
            }

            // The key domain for a book is not used for now, but using the title makes sense.
            // This prevents a potential null issue
            case BOOK: {
                return new GroupKey(R.string.lbl_book, "b", new DomainExpression(
                        DOM_TITLE, TBL_BOOKS.dot(DBKey.TITLE)));
            }
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
    }

    /**
     * Set the visibility of the list of the passed preferences.
     * When one preference is visible, make the category visible.
     *
     * @param category to set
     * @param keys     to set visibility on
     * @param visible  to set
     */
    void setPreferenceVisibility(@NonNull final PreferenceCategory category,
                                 @NonNull final String[] keys,
                                 final boolean visible) {

        for (final String key : keys) {
            final Preference preference = category.findPreference(key);
            if (preference != null) {
                preference.setVisible(visible);
            }
        }

        int i = 0;
        while (i < category.getPreferenceCount()) {
            if (category.getPreference(i).isVisible()) {
                category.setVisible(true);
                return;
            }
            i++;
        }
    }

    /**
     * Get the {@link GroupKey} id.
     *
     * @return id
     */
    @Id
    public int getId() {
        return id;
    }

    @VisibleForTesting
    @NonNull
    public GroupKey getGroupKey() {
        return groupKey;
    }

    /**
     * Get the {@link GroupKey} displayable name.
     *
     * @param context Current context
     *
     * @return name
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return groupKey.getLabel(context);
    }

    /**
     * Create the expression for the key column: "/key=value".
     * A {@code null} value is reformatted as an empty string
     *
     * @return column expression
     */
    @NonNull
    public String getNodeKeyExpression() {
        return groupKey.getNodeKeyExpression();
    }

    /**
     * Get the domain that contains the displayable data.
     * This is used to build the list table.
     * <p>
     * By default, this is the key domain.
     * Override as needed in subclasses.
     *
     * @return domain to display
     */
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return groupKey.getKeyDomainExpression();
    }

    /**
     * Get the domains represented by this group.
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @NonNull
    public ArrayList<DomainExpression> getGroupDomainExpressions() {
        return groupKey.getGroupDomainExpressions();
    }

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    @NonNull
    public ArrayList<DomainExpression> getBaseDomainExpressions() {
        return groupKey.getBaseDomainExpressions();
    }

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @NonNull
    public ArrayList<Domain> getAccumulatedDomains() {
        return Objects.requireNonNull(accumulatedDomains);
    }

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    public void setAccumulatedDomains(@NonNull final ArrayList<Domain> accumulatedDomains) {
        this.accumulatedDomains = accumulatedDomains;
    }

    /**
     * Preference UI support.
     * <p>
     * This method can be called multiple times.
     * Visibility of individual preferences should always be updated.
     *
     * @param screen  which hosts the prefs
     * @param visible whether to make the preferences visible
     */
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BooklistGroup that = (BooklistGroup) o;
        return id == that.id
               && Objects.equals(groupKey, that.groupKey)
               && Objects.equals(accumulatedDomains, that.accumulatedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, groupKey, accumulatedDomains);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistGroup{"
               + "id=" + id
               + ", groupKey=" + groupKey
               + ", accumulatedDomains=" + accumulatedDomains
               + '}';
    }

    @IntDef({BOOK,

             AUTHOR,
             SERIES,
             PUBLISHER,
             BOOKSHELF,
             READ_STATUS,

             LENDING,

             BOOK_TITLE_1ST_LETTER,
             SERIES_TITLE_1ST_LETTER,

             GENRE,
             FORMAT,
             COLOR,
             LOCATION,
             LANGUAGE,
             RATING,

             CONDITION,

             DATE_PUBLISHED_YEAR,
             DATE_PUBLISHED_MONTH,
             DATE_FIRST_PUBLICATION_YEAR,
             DATE_FIRST_PUBLICATION_MONTH,

             DATE_READ_YEAR,
             DATE_READ_MONTH,
             DATE_READ_DAY,

             DATE_ADDED_YEAR,
             DATE_ADDED_MONTH,
             DATE_ADDED_DAY,

             DATE_LAST_UPDATE_YEAR,
             DATE_LAST_UPDATE_MONTH,
             DATE_LAST_UPDATE_DAY,

             DATE_ACQUIRED_YEAR,
             DATE_ACQUIRED_MONTH,
             DATE_ACQUIRED_DAY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Id {

    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public static final class GroupKey {

        /** User displayable label resource id. */
        @StringRes
        private final int labelResId;
        /** Unique keyPrefix used to represent a key in the hierarchy. */
        @NonNull
        private final String keyPrefix;

        /** They key domain, which is by default also the display-domain. */
        @NonNull
        private final DomainExpression keyDomain;

        /**
         * Aside of the main display domain, a group can have extra domains that should
         * be fetched/sorted.
         */
        @NonNull
        private final ArrayList<DomainExpression> groupDomains = new ArrayList<>();

        /**
         * A group can add domains to the lowest level (the book).
         */
        @NonNull
        private final ArrayList<DomainExpression> baseDomains = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param labelResId          User displayable label resource id
         * @param keyPrefix           the key prefix (as short as possible)
         *                            to use for the compound key
         * @param keyDomainExpression the domain to get the actual data from the Cursor
         */
        GroupKey(@StringRes final int labelResId,
                 @NonNull final String keyPrefix,
                 @NonNull final DomainExpression keyDomainExpression) {
            this.labelResId = labelResId;
            this.keyPrefix = keyPrefix;
            keyDomain = keyDomainExpression;
        }

        @NonNull
        String getLabel(@NonNull final Context context) {
            return context.getString(labelResId);
        }

        @NonNull
        GroupKey addGroupDomain(@NonNull final DomainExpression domainExpression) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            groupDomains.add(domainExpression);
            return this;
        }

        @NonNull
        GroupKey addBaseDomain(@NonNull final DomainExpression domainExpression) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            baseDomains.add(domainExpression);
            return this;
        }

        /**
         * Get the unique keyPrefix used to represent a key in the hierarchy.
         *
         * @return keyPrefix, never {@code null} but will be empty for a BOOK.
         */
        @VisibleForTesting
        @NonNull
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * Create the expression for the node key column: "/key=value".
         * A {@code null} value is reformatted as an empty string.
         *
         * @return column expression
         */
        @NonNull
        String getNodeKeyExpression() {
            return "'/" + keyPrefix + "='||COALESCE(" + keyDomain.getExpression() + ",'')";
        }

        /**
         * Get the key domain.
         *
         * @return the key domain
         */
        @NonNull
        DomainExpression getKeyDomainExpression() {
            return keyDomain;
        }

        /**
         * Get the list of secondary domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<DomainExpression> getGroupDomainExpressions() {
            return groupDomains;
        }

        /**
         * Get the list of base (book) domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<DomainExpression> getBaseDomainExpressions() {
            return baseDomains;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final GroupKey groupKey = (GroupKey) o;
            return labelResId == groupKey.labelResId
                   && keyPrefix.equals(groupKey.keyPrefix)
                   && keyDomain.equals(groupKey.keyDomain)
                   && groupDomains.equals(groupKey.groupDomains)
                   && baseDomains.equals(groupKey.baseDomains);
        }

        @Override
        public int hashCode() {
            return Objects.hash(labelResId, keyPrefix, keyDomain, groupDomains, baseDomains);
        }

        @NonNull
        @Override
        public String toString() {
            return "GroupKey{"
                   + "label=`" + ServiceLocator.getAppContext().getString(labelResId) + '`'
                   + ", keyPrefix=`" + keyPrefix + '`'
                   + ", keyDomain=" + keyDomain
                   + ", groupDomains=" + groupDomains
                   + ", baseDomains=" + baseDomains
                   + '}';
        }
    }
}

