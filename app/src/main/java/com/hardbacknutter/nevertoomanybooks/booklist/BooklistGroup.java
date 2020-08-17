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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_PUBLISHER_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
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
 *      <li>modify {@link BooklistBuilder#build} to add the necessary grouped/sorted domains</li>
 *      <li>modify {@link BooklistAdapter#onCreateViewHolder} ; If it is just a string field it can
 *          use a {@link BooklistAdapter.GenericStringHolder} otherwise add a new holder</li>
 * </ol>
 */
public class BooklistGroup
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BooklistGroup> CREATOR = new Creator<BooklistGroup>() {
        @Override
        public BooklistGroup createFromParcel(@NonNull final Parcel source) {
            return new BooklistGroup(source);
        }

        @Override
        public BooklistGroup[] newArray(final int size) {
            return new BooklistGroup[size];
        }
    };

    /**
     * The ID's for the groups. <strong>Never change these</strong>,
     * they get stored in prefs and styles.
     * <p>
     * Also: the code relies on BOOK being == 0
     */
    public static final int BOOK = 0;
    public static final int AUTHOR = 1;
    public static final int SERIES = 2;
    public static final int GENRE = 3;
    public static final int PUBLISHER = 4;
    public static final int READ_STATUS = 5;
    public static final int ON_LOAN = 6;
    public static final int DATE_PUBLISHED_YEAR = 7;
    public static final int DATE_PUBLISHED_MONTH = 8;
    public static final int BOOK_TITLE_LETTER = 9;
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
    public static final int BOOKSHELF = 23;
    public static final int DATE_ACQUIRED_YEAR = 24;
    public static final int DATE_ACQUIRED_MONTH = 25;
    public static final int DATE_ACQUIRED_DAY = 26;
    public static final int DATE_FIRST_PUBLICATION_YEAR = 27;
    public static final int DATE_FIRST_PUBLICATION_MONTH = 28;
    public static final int COLOR = 29;
    public static final int SERIES_TITLE_LETTER = 30;
    public static final int CONDITION = 31;

    /**
     * NEWTHINGS: BooklistGroup.KEY
     * The highest valid index of GroupKey - ALWAYS to be updated after adding a group key.
     */
    @VisibleForTesting
    static final int GROUP_KEY_MAX = 31;

    /** The UUID for the style. Needed to reconstruct the {@link #mStylePrefs} after parcelling. */
    @NonNull
    private final String mUuid;
    /** Flag: is the style user-defined. */
    final boolean mIsUserDefinedStyle;
    /** The type of row/group we represent, see {@link GroupKey}. */
    @Id
    private final int mId;
    /** The underlying group key object. */
    @NonNull
    private final GroupKey mGroupKey;
    /**
     * The domains represented by this group.
     * Set at runtime by builder based on current group <strong>and its outer groups</strong>
     */
    @Nullable
    private ArrayList<Domain> mAccumulatedDomains;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    SharedPreferences mStylePrefs;

    /**
     * Constructor.
     *
     * <strong>Dev. note:</strong> do not store the style to parcel it... it would recurse.
     *
     * @param id    of group to create
     * @param style the style
     */
    BooklistGroup(@Id final int id,
                  @NonNull final BooklistStyle style) {
        mId = id;
        mGroupKey = GroupKey.getGroupKey(mId);
        mUuid = style.getUuid();
        mStylePrefs = style.getStyleSharedPreferences();

        mIsUserDefinedStyle = style.isUserDefined();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    BooklistGroup(@NonNull final Parcel in) {
        mId = in.readInt();
        mGroupKey = GroupKey.getGroupKey(mId);
        //noinspection ConstantConditions
        mUuid = in.readString();
        // reconstruct the preference reference.
        //noinspection ConstantConditions
        if (!mUuid.isEmpty()) {
            mStylePrefs = App.getAppContext().getSharedPreferences(mUuid, Context.MODE_PRIVATE);
        } else {
            mStylePrefs = PreferenceManager.getDefaultSharedPreferences(App.getAppContext());
        }

        mIsUserDefinedStyle = in.readInt() != 0;
        mAccumulatedDomains = new ArrayList<>();
        in.readList(mAccumulatedDomains, getClass().getClassLoader());
    }

    /**
     * Create a new BooklistGroup of the specified id, creating any specific
     * subclasses as necessary.
     *
     * @param context Current context
     * @param id      of group to create
     * @param style   the style
     *
     * @return instance
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public static BooklistGroup newInstance(@NonNull final Context context,
                                            @Id final int id,
                                            @NonNull final BooklistStyle style) {
        switch (id) {
            case AUTHOR:
                return new AuthorBooklistGroup(context, style);
            case SERIES:
                return new SeriesBooklistGroup(style);
            case PUBLISHER:
                return new PublisherBooklistGroup(style);

            default:
                return new BooklistGroup(id, style);
        }
    }

    /**
     * Get a list of BooklistGroup's, one for each defined {@link GroupKey}'s.
     *
     * @param context Current context
     * @param style   the style
     *
     * @return the list
     */
    @SuppressLint("WrongConstant")
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final Context context,
                                                   @NonNull final BooklistStyle style) {
        final List<BooklistGroup> list = new ArrayList<>();
        // Get the set of all valid <strong>Group</strong> values.
        // In other words: all valid groups, <strong>except</strong> the BOOK.
        for (int id = 1; id <= GROUP_KEY_MAX; id++) {
            list.add(newInstance(context, id, style));
        }
        return list;
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

        for (String key : keys) {
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
        return mId;
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
        return mGroupKey.getLabel(context);
    }

    /**
     * Create the expression for the key column: "/key=value".
     * A {@code null} value is reformatted as an empty string
     *
     * @return column expression
     */
    @NonNull
    String getNodeKeyExpression() {
        return mGroupKey.getNodeKeyExpression();
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
    VirtualDomain getDisplayDomain() {
        return mGroupKey.getKeyDomain();
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
    ArrayList<VirtualDomain> getGroupDomains() {
        return mGroupKey.getGroupDomains();
    }

    /**
     * Get the domains that this group adds to the lowest level (book).
     * This is used to build the list table.
     * <p>
     * Override as needed.
     *
     * @return list
     */
    ArrayList<VirtualDomain> getBaseDomains() {
        return mGroupKey.getBaseDomains();
    }

    /**
     * Get the domains for this group <strong>and its outer groups</strong>
     * This is used to build the triggers.
     *
     * @return list
     */
    @Nullable
    ArrayList<Domain> getAccumulatedDomains() {
        return mAccumulatedDomains;
    }

    /**
     * Set the accumulated domains represented by this group <strong>and its outer groups</strong>.
     *
     * @param accumulatedDomains list of domains.
     */
    void setAccumulatedDomains(@Nullable final ArrayList<Domain> accumulatedDomains) {
        mAccumulatedDomains = accumulatedDomains;
    }

    /**
     * Get the Preference objects that this group will contribute to a Style.
     *
     * @return a map with the prefs
     */
    @NonNull
    public Map<String, PPref> getPreferences() {
        return new LinkedHashMap<>();
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

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mId);
        dest.writeString(mUuid);
        dest.writeInt(mIsUserDefinedStyle ? 1 : 0);
        dest.writeList(mAccumulatedDomains);
        // now the prefs for this class (none on this level for now)
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistGroup{"
               + "mUuid=`'`" + mUuid + '`'
               + ", mIsUserDefinedStyle=" + mIsUserDefinedStyle
               + ", mId=" + mId
               + ", mGroupKey=" + mGroupKey
               + ", mAccumulatedDomains=" + mAccumulatedDomains
               + '}';
    }

    @IntDef({BOOK,

             AUTHOR,
             SERIES,
             PUBLISHER,
             BOOKSHELF,
             READ_STATUS,

             ON_LOAN,

             BOOK_TITLE_LETTER,
             SERIES_TITLE_LETTER,

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
    @interface Id {

    }

    /**
     * Specialized BooklistGroup representing an {@link Author} group.
     * Includes extra attributes based on preferences.
     * <p>
     * {@link #getDisplayDomain()} returns a customized display domain
     * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
     */
    public static class AuthorBooklistGroup
            extends BooklistGroup
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<AuthorBooklistGroup> CREATOR =
                new Creator<AuthorBooklistGroup>() {
                    @Override
                    public AuthorBooklistGroup createFromParcel(@NonNull final Parcel source) {
                        return new AuthorBooklistGroup(source);
                    }

                    @Override
                    public AuthorBooklistGroup[] newArray(final int size) {
                        return new AuthorBooklistGroup[size];
                    }
                };

        private static final String PK_PRIMARY_TYPE =
                "style.booklist.group.authors.primary.type";
        private static final String PK_SHOW_BOOKS_UNDER_EACH =
                "style.booklist.group.authors.show.all";

        /** Customized domain with display data. */
        @NonNull
        private final VirtualDomain mDisplayDomain;
        /** Customized domain with sorted data. */
        @NonNull
        private final VirtualDomain mSortedDomain;
        /** We cannot parcel the style here, so keep a local copy of this preference. */
        private final boolean mShowAuthorWithGivenNameFirst;
        /** We cannot parcel the style here, so keep a local copy of this preference. */
        private final boolean mSortAuthorByGivenNameFirst;

        /** Support for 'Show All Authors of Book' property. */
        private PBoolean mAllAuthors;
        /** The primary author type the user prefers. */
        private PBitmask mPrimaryType;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param style   the style
         */
        AuthorBooklistGroup(@NonNull final Context context,
                            @NonNull final BooklistStyle style) {
            super(AUTHOR, style);
            initPrefs();

            mShowAuthorWithGivenNameFirst = style.isShowAuthorByGivenName(context);
            mSortAuthorByGivenNameFirst = style.isSortAuthorByGivenName(context);
            mDisplayDomain = createDisplayDomain();
            mSortedDomain = createSortDomain();
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        AuthorBooklistGroup(@NonNull final Parcel in) {
            super(in);
            initPrefs();
            mAllAuthors.set(in);

            mShowAuthorWithGivenNameFirst = in.readInt() != 0;
            mSortAuthorByGivenNameFirst = in.readInt() != 0;
            mDisplayDomain = createDisplayDomain();
            mSortedDomain = createSortDomain();
        }

        /**
         * Get the global default for this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Authors.
         */
        static boolean showBooksUnderEachDefault(@NonNull final Context context) {
            return PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
        }

        /**
         * Get the global default for this preference.
         *
         * @param context Current context
         *
         * @return the type of author we consider the primary author
         */
        static int getPrimaryTypeGlobalDefault(@NonNull final Context context) {
            return PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getInt(PK_PRIMARY_TYPE, Author.TYPE_UNKNOWN);
        }

        @NonNull
        private VirtualDomain createDisplayDomain() {
            // Not sorted; sort as defined in #createSortDomain
            return new VirtualDomain(DOM_AUTHOR_FORMATTED, DAO.SqlColumns
                    .getDisplayAuthor(TBL_AUTHORS.getAlias(), mShowAuthorWithGivenNameFirst));
        }

        @NonNull
        private VirtualDomain createSortDomain() {
            // Sorting depends on user preference
            return new VirtualDomain(DOM_BL_AUTHOR_SORT,
                                     DAO.SqlColumns.getSortAuthor(mSortAuthorByGivenNameFirst),
                                     VirtualDomain.SORT_ASC);
        }

        @NonNull
        @Override
        VirtualDomain getDisplayDomain() {
            return mDisplayDomain;
        }

        @NonNull
        @Override
        ArrayList<VirtualDomain> getGroupDomains() {
            // We need to inject the mSortedDomain as first in the list.
            final ArrayList<VirtualDomain> list = new ArrayList<>();
            list.add(0, mSortedDomain);
            list.addAll(super.getGroupDomains());
            return list;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllAuthors.writeToParcel(dest);

            dest.writeInt(mShowAuthorWithGivenNameFirst ? 1 : 0);
            dest.writeInt(mSortAuthorByGivenNameFirst ? 1 : 0);
        }

        void initPrefs() {
            mAllAuthors = new PBoolean(mStylePrefs, PK_SHOW_BOOKS_UNDER_EACH,
                                       mIsUserDefinedStyle);

            mPrimaryType = new PBitmask(mStylePrefs, PK_PRIMARY_TYPE,
                                        mIsUserDefinedStyle,
                                        Author.TYPE_UNKNOWN, Author.TYPE_BITMASK_ALL);
        }

        @NonNull
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            final Map<String, PPref> map = super.getPreferences();
            map.put(mAllAuthors.getKey(), mAllAuthors);
            map.put(mPrimaryType.getKey(), mPrimaryType);
            return map;
        }

        @Override
        public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                          final boolean visible) {

            final PreferenceCategory category = screen.findPreference(Prefs.PSK_STYLE_AUTHOR);
            if (category != null) {
                final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH,
                                       PK_PRIMARY_TYPE};

                setPreferenceVisibility(category, keys, visible);
            }
        }

        /**
         * Get this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Authors.
         */
        boolean showBooksUnderEach(@NonNull final Context context) {
            return mAllAuthors.isTrue(context);
        }

        /**
         * Get this preference.
         *
         * @param context Current context
         *
         * @return the type of author we consider the primary author
         */
        @Author.Type
        int getPrimaryType(@NonNull final Context context) {
            return mPrimaryType.getValue(context);
        }

        @Override
        @NonNull
        public String toString() {
            return "AuthorBooklistGroup{"
                   + super.toString()
                   + ", mDisplayDomain=" + mDisplayDomain
                   + ", mSortedDomain=" + mSortedDomain
                   + ", mShowAuthorWithGivenNameFirst=" + mShowAuthorWithGivenNameFirst
                   + ", mSortAuthorByGivenNameFirst=" + mSortAuthorByGivenNameFirst
                   + ", mAllAuthors=" + mAllAuthors
                   + ", mPrimaryType=" + mPrimaryType
                   + '}';
        }
    }

    /**
     * Specialized BooklistGroup representing a {@link Series} group.
     * Includes extra attributes based on preferences.
     * <p>
     * {@link #getDisplayDomain()} returns a customized display domain
     * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
     */
    public static class SeriesBooklistGroup
            extends BooklistGroup
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<SeriesBooklistGroup> CREATOR =
                new Creator<SeriesBooklistGroup>() {
                    @Override
                    public SeriesBooklistGroup createFromParcel(@NonNull final Parcel source) {
                        return new SeriesBooklistGroup(source);
                    }

                    @Override
                    public SeriesBooklistGroup[] newArray(final int size) {
                        return new SeriesBooklistGroup[size];
                    }
                };

        private static final String PK_SHOW_BOOKS_UNDER_EACH =
                "style.booklist.group.series.show.all";

        /** Customized domain with display data. */
        @NonNull
        private final VirtualDomain mDisplayDomain;
        /** Show a book under each Series it appears in. */
        private PBoolean mAllSeries;

        /**
         * Constructor.
         *
         * @param style the style
         */
        SeriesBooklistGroup(@NonNull final BooklistStyle style) {
            super(SERIES, style);
            mDisplayDomain = createDisplayDomain();

            initPrefs();
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        SeriesBooklistGroup(@NonNull final Parcel in) {
            super(in);
            mDisplayDomain = createDisplayDomain();

            initPrefs();
            mAllSeries.set(in);
        }

        /**
         * Get the global default for this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Series.
         */
        static boolean showBooksUnderEachDefault(@NonNull final Context context) {
            return PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(PK_SHOW_BOOKS_UNDER_EACH, false);
        }

        private void initPrefs() {
            mAllSeries = new PBoolean(mStylePrefs, PK_SHOW_BOOKS_UNDER_EACH,
                                      mIsUserDefinedStyle);
        }

        @NonNull
        private VirtualDomain createDisplayDomain() {
            // Not sorted; we sort on the OB domain as defined in the GroupKey.
            return new VirtualDomain(DOM_SERIES_TITLE, TBL_SERIES.dot(KEY_SERIES_TITLE));
        }

        @NonNull
        @Override
        VirtualDomain getDisplayDomain() {
            return mDisplayDomain;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllSeries.writeToParcel(dest);
        }


        @NonNull
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            final Map<String, PPref> map = super.getPreferences();
            map.put(mAllSeries.getKey(), mAllSeries);
            return map;
        }

        @Override
        public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                          final boolean visible) {

            final PreferenceCategory category = screen.findPreference(Prefs.PSK_STYLE_SERIES);
            if (category != null) {
                final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH};
                setPreferenceVisibility(category, keys, visible);
            }
        }

        /**
         * Get this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Series.
         */
        boolean showBooksUnderEach(@NonNull final Context context) {
            return mAllSeries.isTrue(context);
        }

        @Override
        @NonNull
        public String toString() {
            return "SeriesBooklistGroup{"
                   + super.toString()
                   + ", mDisplayDomain=" + mDisplayDomain
                   + ", mAllSeries=" + mAllSeries
                   + '}';
        }
    }

    /**
     * Specialized BooklistGroup representing a Series group.
     * Includes extra attributes based on preferences.
     * <p>
     * {@link #getDisplayDomain()} returns a customized display domain
     * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
     */
    public static class PublisherBooklistGroup
            extends BooklistGroup
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<PublisherBooklistGroup> CREATOR =
                new Creator<PublisherBooklistGroup>() {
                    @Override
                    public PublisherBooklistGroup createFromParcel(@NonNull final Parcel source) {
                        return new PublisherBooklistGroup(source);
                    }

                    @Override
                    public PublisherBooklistGroup[] newArray(final int size) {
                        return new PublisherBooklistGroup[size];
                    }
                };

        private static final String PK_SHOW_BOOKS_UNDER_EACH =
                "style.booklist.group.publisher.show.all";

        /** Customized domain with display data. */
        @NonNull
        private final VirtualDomain mDisplayDomain;
        /** Show a book under each Publisher it appears in. */
        private PBoolean mAllPublishers;

        /**
         * Constructor.
         *
         * @param style the style
         */
        PublisherBooklistGroup(@NonNull final BooklistStyle style) {
            super(PUBLISHER, style);
            mDisplayDomain = createDisplayDomain();

            initPrefs();
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        PublisherBooklistGroup(@NonNull final Parcel in) {
            super(in);
            mDisplayDomain = createDisplayDomain();

            initPrefs();
            mAllPublishers.set(in);
        }

        /**
         * Get the global default for this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Publishers.
         */
        static boolean showBooksUnderEachDefault(@NonNull final Context context) {
            return PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(PK_SHOW_BOOKS_UNDER_EACH,
                                false);
        }

        private void initPrefs() {
            mAllPublishers = new PBoolean(
                    mStylePrefs, PK_SHOW_BOOKS_UNDER_EACH,
                    mIsUserDefinedStyle);
        }

        @NonNull
        private VirtualDomain createDisplayDomain() {
            // Not sorted; we sort on the OB domain as defined in the GroupKey.
            return new VirtualDomain(DOM_PUBLISHER_NAME, TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME));
        }

        @NonNull
        @Override
        VirtualDomain getDisplayDomain() {
            return mDisplayDomain;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllPublishers.writeToParcel(dest);
        }


        @NonNull
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            final Map<String, PPref> map = super.getPreferences();
            map.put(mAllPublishers.getKey(), mAllPublishers);
            return map;
        }

        @Override
        public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                          final boolean visible) {

            final PreferenceCategory category = screen.findPreference(Prefs.PSK_STYLE_PUBLISHER);
            if (category != null) {
                final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH};
                setPreferenceVisibility(category, keys, visible);
            }
        }

        /**
         * Get this preference.
         *
         * @param context Current context
         *
         * @return {@code true} if we want to show a book under each of its Publishers.
         */
        boolean showBooksUnderEach(@NonNull final Context context) {
            return mAllPublishers.isTrue(context);
        }

        @Override
        @NonNull
        public String toString() {
            return "PublisherBooklistGroup{"
                   + super.toString()
                   + ", mDisplayDomain=" + mDisplayDomain
                   + ", mAllPublishers=" + mAllPublishers
                   + '}';
        }
    }

    /**
     * No need to make this Parcelable, it's encapsulated in the BooklistGroup,
     * but always reconstructed based on the ID alone.
     */
    static final class GroupKey {

        // Date based groups have to sort on the full date for cases
        // where we don't have all separate year,month,day fields.
        private static final VirtualDomain DATE_PUBLISHED =
                new VirtualDomain(DOM_DATE_PUBLISHED, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain DATE_FIRST_PUBLICATION =
                new VirtualDomain(DOM_DATE_FIRST_PUBLICATION, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain BOOK_IS_READ =
                new VirtualDomain(DOM_BOOK_READ, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain DATE_READ_END =
                new VirtualDomain(DOM_BOOK_DATE_READ_END, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain DATE_ADDED =
                new VirtualDomain(DOM_UTC_ADDED, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain DATE_LAST_UPDATED =
                new VirtualDomain(DOM_UTC_LAST_UPDATED, null, VirtualDomain.SORT_DESC);
        private static final VirtualDomain DATE_ACQUIRED =
                new VirtualDomain(DOM_BOOK_DATE_ACQUIRED, null, VirtualDomain.SORT_DESC);

        /** Cache for the static GroupKey instances. */
        private static final Map<Integer, GroupKey> ALL = new UniqueMap<>();

        /** User displayable label resource id. */
        @StringRes
        private final int mLabelId;
        /** Unique keyPrefix used to represent a key in the hierarchy. */
        @NonNull
        private final String mKeyPrefix;

        /** They key domain, which is by default also the display-domain. */
        @NonNull
        private final VirtualDomain mKeyDomain;

        /**
         * Aside of the main display domain, a group can have extra domains that should
         * be fetched/sorted.
         */
        @NonNull
        private final ArrayList<VirtualDomain> mGroupDomains = new ArrayList<>();

        /**
         * A group can add domains to the lowest level (the book).
         */
        @NonNull
        private final ArrayList<VirtualDomain> mBaseDomains = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param labelId    User displayable label resource id
         * @param keyPrefix  the key prefix (as short as possible) to use for the compound key
         * @param domain     the domain to get the actual data from the Cursor
         * @param expression sql column expression for constructing the Cursor
         * @param sorted     optional sorting
         */
        private GroupKey(@StringRes final int labelId,
                         @NonNull final String keyPrefix,
                         @NonNull final Domain domain,
                         @NonNull final String expression,
                         @VirtualDomain.Sorting final int sorted) {
            mLabelId = labelId;
            mKeyPrefix = keyPrefix;
            mKeyDomain = new VirtualDomain(domain, expression, sorted);
        }

        /**
         * GroupKey factory constructor.
         *
         * @param id for the desired group key
         *
         * @return new GroupKey instance
         */
        private static GroupKey createGroupKey(@Id final int id) {
            // NEWTHINGS: BooklistGroup.KEY
            switch (id) {
                // The key domain for a book is not used for now, but using the title makes sense.
                case BOOK: {
                    return new GroupKey(R.string.lbl_book, "b",
                                        DOM_TITLE, TBL_BOOKS.dot(KEY_TITLE),
                                        VirtualDomain.SORT_UNSORTED);
                }

                // Data with a linked table use the foreign key ID as the key domain.
                case AUTHOR: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    // We do not sort on the key domain but add the OB column in the BooklistGroup.
                    return new GroupKey(R.string.lbl_author, "a",
                                        DOM_FK_AUTHOR, TBL_AUTHORS.dot(KEY_PK_ID),
                                        VirtualDomain.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Authors will have the same name)
                                    new VirtualDomain(DOM_FK_AUTHOR,
                                                      TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR)))
                            .addGroupDomain(
                                    // Group by complete-flag
                                    new VirtualDomain(DOM_AUTHOR_IS_COMPLETE,
                                                      TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE)));
                }
                case SERIES: {
                    // We use the foreign ID to create the key.
                    // We override the display domain in the BooklistGroup.
                    // We do not sort on the key domain but add the OB column instead
                    return new GroupKey(R.string.lbl_series, "s",
                                        DOM_FK_SERIES, TBL_SERIES.dot(KEY_PK_ID),
                                        VirtualDomain.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group and sort by the OB column
                                    new VirtualDomain(DOM_BL_SERIES_SORT,
                                                      TBL_SERIES.dot(KEY_SERIES_TITLE_OB),
                                                      VirtualDomain.SORT_ASC))
                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Series will have the same name)
                                    new VirtualDomain(DOM_FK_SERIES,
                                                      TBL_BOOK_SERIES.dot(KEY_FK_SERIES)))
                            .addGroupDomain(
                                    // Group by complete-flag
                                    new VirtualDomain(DOM_SERIES_IS_COMPLETE,
                                                      TBL_SERIES.dot(KEY_SERIES_IS_COMPLETE)))
                            .addBaseDomain(
                                    // The series number in the base data in sorted order.
                                    // This field is NOT displayed.
                                    // Casting it as a float allows for the possibility of 3.1,
                                    // or even 3.1|Omnibus 3-10" as a series number.
                                    new VirtualDomain(DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT,
                                                      "CAST(" + TBL_BOOK_SERIES
                                                              .dot(KEY_BOOK_NUM_IN_SERIES)
                                                      + " AS REAL)",
                                                      VirtualDomain.SORT_ASC))
                            .addBaseDomain(
                                    // The series number in the base data in sorted order.
                                    // This field is displayed.
                                    // Covers non-numeric data (where the above float would fail)
                                    new VirtualDomain(DOM_BOOK_NUM_IN_SERIES,
                                                      TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES),
                                                      VirtualDomain.SORT_ASC));

                }
                case PUBLISHER: {
                    // We do NOT use the foreign ID to create the key.
                    // The linked data is used directly to display.
                    // If we ever create a dedicated BooklistGroup for Publisher,
                    // this can/should be changed.
                    return new GroupKey(R.string.lbl_publisher, "p",
                                        DOM_FK_PUBLISHER, TBL_PUBLISHERS.dot(KEY_PK_ID),
                                        VirtualDomain.SORT_UNSORTED)

                            .addGroupDomain(
                                    // Group and sort by the OB column
                                    new VirtualDomain(DOM_BL_PUBLISHER_SORT,
                                                      TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME_OB),
                                                      VirtualDomain.SORT_ASC)
                                           )
                            .addGroupDomain(
                                    // Group by id (we want the id available and there is
                                    // a chance two Publishers will have the same name)
                                    new VirtualDomain(DOM_FK_PUBLISHER,
                                                      TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER)));
                }
                case BOOKSHELF: {
                    // We do NOT use the foreign ID to create the key.
                    // The linked data is used directly to display.
                    // If we ever create a dedicated BooklistGroup for Bookshelf,
                    // this can/should be changed.
                    return new GroupKey(R.string.lbl_bookshelf, "shelf",
                                        DOM_BOOKSHELF_NAME, TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME),
                                        VirtualDomain.SORT_ASC);
                }

                // Data without a linked table use the display name as the key domain.
                case COLOR: {
                    return new GroupKey(R.string.lbl_color, "col",
                                        DOM_BOOK_COLOR, TBL_BOOKS.dot(KEY_COLOR),
                                        VirtualDomain.SORT_ASC);
                }
                case FORMAT: {
                    return new GroupKey(R.string.lbl_format, "fmt",
                                        DOM_BOOK_FORMAT, TBL_BOOKS.dot(KEY_FORMAT),
                                        VirtualDomain.SORT_ASC);
                }
                case GENRE: {
                    return new GroupKey(R.string.lbl_genre, "g",
                                        DOM_BOOK_GENRE, TBL_BOOKS.dot(KEY_GENRE),
                                        VirtualDomain.SORT_ASC);
                }
                case LANGUAGE: {
                    // Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_language, "lng",
                                        DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(KEY_LANGUAGE),
                                        VirtualDomain.SORT_ASC);
                }
                case LOCATION: {
                    return new GroupKey(R.string.lbl_location, "loc",
                                        DOM_BOOK_LOCATION, TBL_BOOKS.dot(KEY_LOCATION),
                                        VirtualDomain.SORT_ASC);
                }
                case CONDITION: {
                    return new GroupKey(R.string.lbl_condition, "bk_cnd",
                                        DOM_BOOK_CONDITION, TBL_BOOKS.dot(KEY_BOOK_CONDITION),
                                        VirtualDomain.SORT_DESC);
                }
                case RATING: {
                    // Formatting is done after fetching
                    // Sort with highest rated first
                    // The data is cast to an integer as a precaution.
                    return new GroupKey(R.string.lbl_rating, "rt",
                                        DOM_BOOK_RATING,
                                        "CAST(" + TBL_BOOKS.dot(KEY_RATING) + " AS INTEGER)",
                                        VirtualDomain.SORT_DESC);
                }

                // the others here below are custom key domains
                case ON_LOAN: {
                    return new GroupKey(R.string.lbl_loaned, "l",
                                        DOM_LOANEE, DAO.SqlColumns.EXP_BOOK_LOANEE_OR_EMPTY,
                                        VirtualDomain.SORT_ASC);
                }
                case READ_STATUS: {
                    // Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_read_and_unread, "r",
                                        new Domain.Builder("blg_rd_sts", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        TBL_BOOKS.dot(KEY_READ),
                                        VirtualDomain.SORT_ASC);
                }
                case BOOK_TITLE_LETTER: {
                    // Uses the OrderBy column so we get the re-ordered version if applicable.
                    // Formatting is done in the sql expression.
                    return new GroupKey(R.string.style_builtin_first_letter_book_title, "t",
                                        new Domain.Builder("blg_tit_let", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        "upper(SUBSTR(" + TBL_BOOKS.dot(KEY_TITLE_OB) + ",1,1))",
                                        VirtualDomain.SORT_ASC);
                }
                case SERIES_TITLE_LETTER: {
                    // Uses the OrderBy column so we get the re-ordered version if applicable.
                    // Formatting is done in the sql expression.
                    return new GroupKey(R.string.style_builtin_first_letter_series_title, "st",
                                        new Domain.Builder("blg_ser_tit_let", ColumnInfo.TYPE_TEXT)
                                                .notNull()
                                                .build(),
                                        "upper(SUBSTR(" + TBL_SERIES.dot(KEY_SERIES_TITLE_OB)
                                        + ",1,1))",
                                        VirtualDomain.SORT_ASC);
                }

                case DATE_PUBLISHED_YEAR: {
                    // UTC. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_publication_year, "yrp",
                                        new Domain.Builder("blg_pub_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_DATE_PUBLISHED), false),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_PUBLISHED);
                }
                case DATE_PUBLISHED_MONTH: {
                    // UTC. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_publication_month, "mp",
                                        new Domain.Builder("blg_pub_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_DATE_PUBLISHED), false),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_PUBLISHED);
                }

                case DATE_FIRST_PUBLICATION_YEAR: {
                    // UTC. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_first_pub_year, "yfp",
                                        new Domain.Builder("blg_1pub_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                      false),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_FIRST_PUBLICATION);
                }
                case DATE_FIRST_PUBLICATION_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_first_pub_month, "mfp",
                                        new Domain.Builder("blg_1pub_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION),
                                                       false),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_FIRST_PUBLICATION);
                }

                case DATE_ACQUIRED_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_date_acquired_year, "yac",
                                        new Domain.Builder("blg_acq_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }
                case DATE_ACQUIRED_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_date_acquired_month, "mac",
                                        new Domain.Builder("blg_acq_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }
                case DATE_ACQUIRED_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_date_acquired_day, "dac",
                                        new Domain.Builder("blg_acq_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_DATE_ACQUIRED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ACQUIRED);
                }


                case DATE_ADDED_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_added_year, "ya",
                                        new Domain.Builder("blg_add_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }
                case DATE_ADDED_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_added_month, "ma",
                                        new Domain.Builder("blg_add_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }
                case DATE_ADDED_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_added_day, "da",
                                        new Domain.Builder("blg_add_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_UTC_ADDED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_ADDED);
                }

                case DATE_LAST_UPDATE_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_update_year, "yu",
                                        new Domain.Builder("blg_upd_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .year(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }
                case DATE_LAST_UPDATE_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_update_month, "mu",
                                        new Domain.Builder("blg_upd_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .month(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }
                case DATE_LAST_UPDATE_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_update_day, "du",
                                        new Domain.Builder("blg_upd_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns
                                                .day(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_LAST_UPDATED);
                }

                case DATE_READ_YEAR: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_read_year, "yr",
                                        new Domain.Builder("blg_rd_y", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.year(TBL_BOOKS.dot(KEY_READ_END), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }
                case DATE_READ_MONTH: {
                    // Local for the user. Formatting is done after fetching.
                    return new GroupKey(R.string.lbl_read_month, "mr",
                                        new Domain.Builder("blg_rd_m", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.month(TBL_BOOKS.dot(KEY_READ_END), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }
                case DATE_READ_DAY: {
                    // Local for the user. Formatting is done in the sql expression.
                    return new GroupKey(R.string.lbl_read_day, "dr",
                                        new Domain.Builder("blg_rd_d", ColumnInfo.TYPE_INTEGER)
                                                .build(),
                                        DAO.SqlColumns.day(TBL_BOOKS.dot(KEY_READ_END), true),
                                        VirtualDomain.SORT_DESC)
                            .addBaseDomain(DATE_READ_END)
                            .addGroupDomain(BOOK_IS_READ);
                }

                default:
                    throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + id);
            }
        }

        /**
         * External entry point to create/get a GroupKey.
         *
         * @param id of group to get
         *
         * @return instance
         */
        @NonNull
        static GroupKey getGroupKey(@Id final int id) {
            GroupKey groupKey = ALL.get(id);
            if (groupKey == null) {
                groupKey = createGroupKey(id);
                ALL.put(id, groupKey);
            }
            return groupKey;
        }

        @NonNull
        String getLabel(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        @NonNull
        private GroupKey addGroupDomain(@NonNull final VirtualDomain vDomain) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            mGroupDomains.add(vDomain);
            return this;
        }

        @NonNull
        private GroupKey addBaseDomain(@NonNull final VirtualDomain vDomain) {
            // this is a static setup. We don't check on developer mistakenly adding duplicates!
            mBaseDomains.add(vDomain);
            return this;
        }

        /**
         * Get the unique keyPrefix used to represent a key in the hierarchy.
         *
         * @return keyPrefix, never {@code null} but will be empty for a BOOK.
         */
        @VisibleForTesting
        @NonNull
        String getKeyPrefix() {
            return mKeyPrefix;
        }

        /**
         * Create the expression for the node key column: "/key=value".
         * A {@code null} value is reformatted as an empty string.
         *
         * @return column expression
         */
        @NonNull
        String getNodeKeyExpression() {
            return "'/" + mKeyPrefix + "='||COALESCE(" + mKeyDomain.getExpression() + ",'')";
        }

        /**
         * Get the key domain.
         *
         * @return the key domain
         */
        @NonNull
        VirtualDomain getKeyDomain() {
            return mKeyDomain;
        }

        /**
         * Get the list of secondary domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<VirtualDomain> getGroupDomains() {
            return mGroupDomains;
        }

        /**
         * Get the list of base (book) domains.
         * <p>
         * Override in the {@link BooklistGroup} as needed.
         *
         * @return the list, can be empty.
         */
        @NonNull
        ArrayList<VirtualDomain> getBaseDomains() {
            return mBaseDomains;
        }

        @NonNull
        @Override
        public String toString() {
            return "GroupKey{"
                   + "mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
                   + ", mKeyPrefix=`" + mKeyPrefix + '`'
                   + ", mKeyDomain=" + mKeyDomain
                   + ", mSecondaryDomains=" + mGroupDomains
                   + ", mBookDomains=" + mBaseDomains
                   + '}';
        }
    }
}

