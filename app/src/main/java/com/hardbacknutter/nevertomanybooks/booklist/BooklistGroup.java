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
package com.hardbacknutter.nevertomanybooks.booklist;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.UniqueMap;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with the members of a {@link RowKind}.
 * <p>
 * IMPORTANT: The {@link #mDomains} must be set at runtime each time but that is ok as
 * they are only needed at list build time. They are NOT stored.
 * <p>
 * <b>Note:</b> the way preferences are implemented means that all groups will add their
 * properties to the persisted state of a style. Not just the groups which are active/present
 * for that state. This is fine, as they won't get used unless activated.
 */
public class BooklistGroup
        implements Serializable, Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BooklistGroup> CREATOR =
            new Creator<BooklistGroup>() {
                @Override
                public BooklistGroup createFromParcel(@NonNull final Parcel source) {
                    return new BooklistGroup(source);
                }

                @Override
                public BooklistGroup[] newArray(final int size) {
                    return new BooklistGroup[size];
                }
            };

    /** pre-v200 legacy support. DO NOT CHANGE. */
    private static final long serialVersionUID = 1012206875683862714L;
    /** Flag indicating the style is user-defined -> our prefs must be persisted. */
    final boolean mIsUserDefinedStyle;
    /**
     * the kind of row/group we represent, see {@link RowKind}.
     * <p>
     * Do not rename or move this variable, deserialization will break.
     */
    private final int kind;
    /** The name of the Preference file (comes from the style that contains this group. */
    @NonNull
    String mUuid;
    /**
     * The domains represented by this group.
     * Set at runtime by builder based on current group and outer groups
     */
    @Nullable
    private transient ArrayList<DomainDefinition> mDomains;

    /**
     * Constructor.
     *
     * @param kind               Kind of group to create
     * @param uuid               of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     */
    private BooklistGroup(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind,
                          @NonNull final String uuid,
                          final boolean isUserDefinedStyle) {
        this.kind = kind;
        mUuid = uuid;
        mIsUserDefinedStyle = isUserDefinedStyle;
        initPrefs();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BooklistGroup(@NonNull final Parcel in) {
        kind = in.readInt();
        //noinspection ConstantConditions
        mUuid = in.readString();
        mIsUserDefinedStyle = in.readInt() != 0;
        mDomains = new ArrayList<>();
        in.readList(mDomains, getClass().getClassLoader());
        // now the prefs
        initPrefs();
    }

    /**
     * Create a new BooklistGroup of the specified kind, creating any specific
     * subclasses as necessary.
     *
     * @param kind               Kind of group to create
     * @param uuid               of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     *
     * @return a group based on the passed in kind
     */
    @NonNull
    public static BooklistGroup newInstance(
            @IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind,
            @NonNull final String uuid,
            final boolean isUserDefinedStyle) {
        switch (kind) {
            case RowKind.AUTHOR:
                return new BooklistAuthorGroup(uuid, isUserDefinedStyle);
            case RowKind.SERIES:
                return new BooklistSeriesGroup(uuid, isUserDefinedStyle);
            default:
                return new BooklistGroup(kind, uuid, isUserDefinedStyle);
        }
    }

    /**
     * @param uuid               of the style
     * @param isUserDefinedStyle {@code true} if the group properties should be persisted
     *
     * @return a list of BooklistGroups, one for each defined RowKind.
     */
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final String uuid,
                                                   final boolean isUserDefinedStyle) {
        List<BooklistGroup> list = new ArrayList<>();
        //skip BOOK KIND
        for (int kind = 1; kind < RowKind.size(); kind++) {
            list.add(newInstance(kind, uuid, isUserDefinedStyle));
        }
        return list;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(kind);
        dest.writeString(mUuid);
        dest.writeInt(mIsUserDefinedStyle ? 1 : 0);
        dest.writeList(mDomains);
        // now the prefs for this class (none on this level for now)
    }

    @NonNull
    public String getUuid() {
        return mUuid;
    }

    /**
     * Limited use for de-serialisation from a pre-v200 archive support.
     * Once the groups are processed, the UUID needs to be set manually
     * during de-serialization of the Style itself.
     *
     * @param uuid to set (from the Style)
     */
    public void setUuid(@NonNull final String uuid) {
        mUuid = uuid;
    }

    public boolean isUserDefinedStyle() {
        return mIsUserDefinedStyle;
    }

    public int getKind() {
        return kind;
    }

    public String getName(@NonNull final Context context) {
        return RowKind.get(kind).getName(context);
    }

    @NonNull
    public DomainDefinition getDisplayDomain() {
        return RowKind.get(kind).getDisplayDomain();
    }

    @NonNull
    CompoundKey getCompoundKey() {
        return RowKind.get(kind).getCompoundKey();
    }

    @Nullable
    ArrayList<DomainDefinition> getDomains() {
        return mDomains;
    }

    void setDomains(@Nullable final ArrayList<DomainDefinition> domains) {
        mDomains = domains;
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     */
    void initPrefs() {
    }

    /**
     * @return the Preference objects that this group will contribute to a Style.
     */
    public Map<String, PPref> getPreferences() {
        return new LinkedHashMap<>();
    }

    /**
     * Preference UI support.
     * <p>
     * Add the Preference objects that this group will contribute to a Style.
     * TODO: could/should do this from xml instead I suppose.
     *
     * @param screen to add the prefs to
     */
    public void addPreferencesTo(@NonNull final PreferenceScreen screen) {
    }

    /**
     * Pre-v200 Legacy support for reading serialized styles from archives and database upgrade.
     * <p>
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(@NonNull final ObjectInputStream is)
            throws IOException, ClassNotFoundException {
        //use a temporary empty uuid so storage is memory only for the groups.
        // we'll set the real uuid at the end of the import and convert them.
        mUuid = "";
        initPrefs();
        is.defaultReadObject();
    }

    /**
     * Do not rename or move this class, deserialization will break.
     * <p>
     * Specialized BooklistGroup representing a Series group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistSeriesGroup
            extends BooklistGroup
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<BooklistSeriesGroup> CREATOR =
                new Creator<BooklistSeriesGroup>() {
                    @Override
                    public BooklistSeriesGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistSeriesGroup(source);
                    }

                    @Override
                    public BooklistSeriesGroup[] newArray(final int size) {
                        return new BooklistSeriesGroup[size];
                    }
                };

        /** pre-v200 legacy support. DO NOT CHANGE. */
        private static final long serialVersionUID = 9023218506278704155L;

        /** Show book under each series it appears in. */
        private transient PBoolean mAllSeries;

        /**
         * Constructor.
         *
         * @param uuid of the style
         */
        BooklistSeriesGroup(@NonNull final String uuid,
                            final boolean isUserDefinedStyle) {
            super(RowKind.SERIES, uuid, isUserDefinedStyle);
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private BooklistSeriesGroup(@NonNull final Parcel in) {
            super(in);
            initPrefs();
            mAllSeries.set(in);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllSeries.writeToParcel(dest);
        }

        /**
         * Only ever init the Preferences if you have a valid UUID.
         */
        @Override
        void initPrefs() {
            super.initPrefs();
            mAllSeries = new PBoolean(Prefs.pk_bob_books_under_multiple_series, mUuid,
                                      mIsUserDefinedStyle);
        }

        /**
         * Get the Preference objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllSeries.getKey(), mAllSeries);
            return map;
        }

        /**
         * Preference UI support.
         * <p>
         * Add the Preference objects that this group will contribute to a Style.
         * TODO: could/should do this from xml instead I suppose.
         *
         * @param screen to add the prefs to
         */
        @Override
        public void addPreferencesTo(@NonNull final PreferenceScreen screen) {
            Context context = screen.getContext();
            PreferenceCategory category =
                    screen.findPreference(context.getString(R.string.lbl_series));
            String description = context.getString(R.string.lbl_series);
            if (category != null) {
                category.setVisible(true);

                SwitchPreference pShowAll = new SwitchPreference(context);
                pShowAll.setTitle(R.string.pt_bob_books_under_multiple_series);
                pShowAll.setIcon(R.drawable.ic_functions);
                pShowAll.setKey(Prefs.pk_bob_books_under_multiple_series);
                pShowAll.setDefaultValue(false);

                pShowAll.setSummaryOn(context.getString(
                        R.string.pv_bob_books_under_multiple_each_1s, description));
                pShowAll.setSummaryOff(context.getString(
                        R.string.pv_bob_books_under_multiple_primary_1s_only, description));
                //pAllSeries.setHint(R.string.hint_series_book_may_appear_more_than_once);
                category.addPreference(pShowAll);
            }
        }

        boolean showAll() {
            return mAllSeries.isTrue();
        }

        /**
         * Pre-v200 Legacy support for reading serialized styles from archives and database upgrade.
         * <p>
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void readObject(@NonNull final ObjectInputStream is)
                throws IOException, ClassNotFoundException {
            super.readObject(is);
            mAllSeries.set((Boolean) is.readObject());
        }
    }

    /**
     * Do not rename or move this class, deserialization will break.
     * <p>
     * Specialized BooklistGroup representing an Author group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistAuthorGroup
            extends BooklistGroup
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<BooklistAuthorGroup> CREATOR =
                new Creator<BooklistAuthorGroup>() {
                    @Override
                    public BooklistAuthorGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistAuthorGroup(source);
                    }

                    @Override
                    public BooklistAuthorGroup[] newArray(final int size) {
                        return new BooklistAuthorGroup[size];
                    }
                };

        /** pre-v200 legacy support. DO NOT CHANGE. */
        private static final long serialVersionUID = -1984868877792780113L;
        /** Support for 'Show All Authors of Book' property. */
        private transient PBoolean mAllAuthors;
        /** Support for 'Show Given Name First' property. Default: false. */
        private transient PBoolean mGivenNameFirst;

        /**
         * Constructor.
         *
         * @param uuid of the style
         */
        BooklistAuthorGroup(@NonNull final String uuid,
                            final boolean isUserDefinedStyle) {
            super(RowKind.AUTHOR, uuid, isUserDefinedStyle);
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private BooklistAuthorGroup(@NonNull final Parcel in) {
            super(in);
            mAllAuthors.set(in);
            mGivenNameFirst.set(in);
        }

        /**
         * Get the global default for this preference.
         *
         * @return {@code true} if we want "given-names last-name" formatted authors.
         */
        static boolean globalShowGivenNameFirst(@NonNull final Context context) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(Prefs.pk_bob_format_author_name, false);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllAuthors.writeToParcel(dest);
            mGivenNameFirst.writeToParcel(dest);
        }

        /**
         * Only ever init the Preferences if you have a valid UUID.
         */
        @Override
        protected void initPrefs() {
            super.initPrefs();
            mAllAuthors = new PBoolean(Prefs.pk_bob_books_under_multiple_authors, mUuid,
                                       mIsUserDefinedStyle);
            mGivenNameFirst = new PBoolean(Prefs.pk_bob_format_author_name, mUuid,
                                           mIsUserDefinedStyle);
        }

        /**
         * Get the Preference objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllAuthors.getKey(), mAllAuthors);
            map.put(mGivenNameFirst.getKey(), mGivenNameFirst);
            return map;
        }

        /**
         * Preference UI support.
         * <p>
         * Add the Preference objects that this group will contribute to a Style.
         * TODO: could/should do this from xml instead I suppose.
         *
         * @param screen to add the prefs to
         */
        @Override
        public void addPreferencesTo(@NonNull final PreferenceScreen screen) {
            Context context = screen.getContext();
            PreferenceCategory category =
                    screen.findPreference(context.getString(R.string.lbl_author));
            String description = context.getString(R.string.lbl_author);
            if (category != null) {
                category.setVisible(true);

                SwitchPreference pShowAll = new SwitchPreference(context);
                pShowAll.setTitle(R.string.pt_bob_books_under_multiple_authors);
                pShowAll.setIcon(R.drawable.ic_functions);
                pShowAll.setKey(Prefs.pk_bob_books_under_multiple_authors);
                pShowAll.setDefaultValue(false);
                pShowAll.setSummaryOn(
                        context.getString(R.string.pv_bob_books_under_multiple_each_1s,
                                          description));
                pShowAll.setSummaryOff(
                        context.getString(R.string.pv_bob_books_under_multiple_primary_1s_only,
                                          description));
                //pAllAuthors.setHint(R.string.hint_authors_book_may_appear_more_than_once)
                category.addPreference(pShowAll);

                SwitchPreference pGivenNameFirst = new SwitchPreference(context);
                pGivenNameFirst.setTitle(R.string.pt_bob_format_author_name);
                pGivenNameFirst.setIcon(R.drawable.ic_title);
                pGivenNameFirst.setKey(Prefs.pk_bob_format_author_name);
                pGivenNameFirst.setDefaultValue(false);
                pGivenNameFirst.setSummaryOn(R.string.pv_bob_format_author_name_given_first);
                pGivenNameFirst.setSummaryOff(R.string.pv_bob_format_author_name_family_first);
                category.addPreference(pGivenNameFirst);
            }
        }

        boolean showAll() {
            return mAllAuthors.isTrue();
        }

        /**
         * @return {@code true} if we want "given-names last-name" formatted authors.
         */
        boolean showAuthorGivenNameFirst() {
            return mGivenNameFirst.isTrue();
        }

        /**
         * Pre-v200 Legacy support for reading serialized styles from archives and database upgrade.
         * <p>
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void readObject(@NonNull final ObjectInputStream is)
                throws IOException, ClassNotFoundException {
            super.readObject(is);

            mAllAuthors.set((Boolean) is.readObject());
            mGivenNameFirst.set((Boolean) is.readObject());
        }
    }

    /**
     * Get a RowKind with the static method: {@link #get(int kind)}.
     * <p>
     * We create them all once at startup and keep them cached,
     * so the RowKind class is for al intent and purpose static!
     */
    public static final class RowKind {

        // The code relies on BOOK being == 0
        public static final int BOOK = 0;
        public static final int AUTHOR = 1;
        public static final int SERIES = 2;
        public static final int GENRE = 3;
        public static final int PUBLISHER = 4;
        public static final int READ_STATUS = 5;
        public static final int LOANED = 6;
        public static final int DATE_PUBLISHED_YEAR = 7;
        public static final int DATE_PUBLISHED_MONTH = 8;
        public static final int TITLE_LETTER = 9;
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

        // NEWKIND: ROW_KIND_x
        // the highest valid index of kinds  ALWAYS update after adding a row kind...
        static final int ROW_KIND_MAX = 28;

        private static final Map<Integer, RowKind> ALL_KINDS = new UniqueMap<>();

        static {
            RowKind rowKind;

            // hardcoded BOOK construction.
            rowKind = new RowKind();
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(AUTHOR, R.string.lbl_author, "a",
                                  DBDefinitions.DOM_FK_AUTHOR);
            rowKind.setDisplayDomain(DBDefinitions.DOM_AUTHOR_FORMATTED);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(SERIES, R.string.lbl_series, "s",
                                  DBDefinitions.DOM_FK_SERIES);
            rowKind.setDisplayDomain(DBDefinitions.DOM_SERIES_TITLE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            //all others will use the underlying domain as the displayDomain
            rowKind = new RowKind(GENRE, R.string.lbl_genre, "g",
                                  DBDefinitions.DOM_BOOK_GENRE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(PUBLISHER, R.string.lbl_publisher, "p",
                                  DBDefinitions.DOM_BOOK_PUBLISHER);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(READ_STATUS, R.string.lbl_read_and_unread, "r",
                                  DBDefinitions.DOM_READ_STATUS);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LOANED, R.string.lbl_loaned, "l",
                                  DBDefinitions.DOM_LOANEE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_PUBLISHED_YEAR, R.string.lbl_publication_year, "yrp",
                                  DBDefinitions.DOM_DATE_PUBLISHED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_PUBLISHED_MONTH, R.string.lbl_publication_month, "mnp",
                                  DBDefinitions.DOM_DATE_PUBLISHED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(TITLE_LETTER, R.string.style_builtin_title_first_letter, "t",
                                  DBDefinitions.DOM_TITLE_LETTER);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_YEAR, R.string.lbl_added_year, "yra",
                                  DBDefinitions.DOM_DATE_ADDED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_MONTH, R.string.lbl_added_month, "mna",
                                  DBDefinitions.DOM_DATE_ADDED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_DAY, R.string.lbl_added_day, "dya",
                                  DBDefinitions.DOM_DATE_ADDED_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(FORMAT, R.string.lbl_format, "fmt",
                                  DBDefinitions.DOM_BOOK_FORMAT);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_YEAR, R.string.lbl_read_year, "yrr",
                                  DBDefinitions.DOM_DATE_READ_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_MONTH, R.string.lbl_read_month, "mnr",
                                  DBDefinitions.DOM_DATE_READ_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_DAY, R.string.lbl_read_day, "dyr",
                                  DBDefinitions.DOM_DATE_READ_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LOCATION, R.string.lbl_location, "loc",
                                  DBDefinitions.DOM_BOOK_LOCATION);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LANGUAGE, R.string.lbl_language, "lang",
                                  DBDefinitions.DOM_BOOK_LANGUAGE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_YEAR, R.string.lbl_update_year, "yru",
                                  DBDefinitions.DOM_DATE_LAST_UPDATE_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_MONTH, R.string.lbl_update_month, "mnu",
                                  DBDefinitions.DOM_DATE_UPDATE_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_DAY, R.string.lbl_update_day, "dyu",
                                  DBDefinitions.DOM_DATE_UPDATE_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(RATING, R.string.lbl_rating, "rat",
                                  DBDefinitions.DOM_BOOK_RATING);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(BOOKSHELF, R.string.lbl_bookshelf, "shelf",
                                  DBDefinitions.DOM_BOOKSHELF);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_YEAR, R.string.lbl_date_acquired_year, "yrac",
                                  DBDefinitions.DOM_DATE_ACQUIRED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_MONTH, R.string.lbl_date_acquired_month, "mnac",
                                  DBDefinitions.DOM_DATE_ACQUIRED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_DAY, R.string.lbl_date_acquired_day, "dyac",
                                  DBDefinitions.DOM_DATE_ACQUIRED_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUBLICATION_YEAR,
                                  R.string.lbl_first_publication_year, "yrfp",
                                  DBDefinitions.DOM_DATE_FIRST_PUBLICATION_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUBLICATION_MONTH,
                                  R.string.lbl_first_publication_month, "mnfp",
                                  DBDefinitions.DOM_DATE_FIRST_PUBLICATION_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            // NEWKIND: ROW_KIND_x

            // Developer sanity check (for() loop starting at 1)
            if (BOOK != 0) {
                throw new IllegalStateException("BOOK was " + BOOK);
            }

            // Developer sanity check
            Set<String> prefixes = new HashSet<>();
            for (int kind = 0; kind <= ROW_KIND_MAX; kind++) {
                if (!ALL_KINDS.containsKey(kind)) {
                    throw new IllegalStateException("Missing kind " + kind);
                }

                //noinspection ConstantConditions
                String prefix = ALL_KINDS.get(kind).mCompoundKey.prefix;
                if (!prefixes.add(prefix)) {
                    throw new IllegalStateException("Duplicate prefix " + prefix);
                }
            }

        }

        @IntRange(from = 0, to = RowKind.ROW_KIND_MAX)
        private final int mKind;

        @StringRes
        private final int mLabelId;

        @NonNull
        private final CompoundKey mCompoundKey;

        @SuppressWarnings("NullableProblems")
        @NonNull
        private DomainDefinition mDisplayDomain;

        /**
         * Hardcoded constructor for a BOOK
         * <p>
         * Note we suppress the {@code null} use.
         */
        @SuppressWarnings("ConstantConditions")
        private RowKind() {
            mKind = BOOK;
            mLabelId = R.string.lbl_book;
            mCompoundKey = new CompoundKey("", (DomainDefinition[]) null);
            mDisplayDomain = null;
        }

        /**
         * @param kind    1 to max. The kind==0 should be created with the no-args constructor.
         * @param domains all underlying domains.
         *                The first element will be used as the displayDomain.
         */
        private RowKind(@IntRange(from = 1, to = RowKind.ROW_KIND_MAX) final int kind,
                        @StringRes final int labelId,
                        @NonNull final String prefix,
                        @NonNull final DomainDefinition... domains) {
            mKind = kind;
            mLabelId = labelId;
            mCompoundKey = new CompoundKey(prefix, domains);
            if (domains.length > 0) {
                mDisplayDomain = domains[0];
            }
        }

        /**
         * Don't use {@link #ROW_KIND_MAX} for code. Use this method.
         */
        public static int size() {
            return ALL_KINDS.size();
        }

        /**
         * @param kind to create
         *
         * @return a cached instance of a RowKind
         */
        @NonNull
        public static RowKind get(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind) {
            //noinspection ConstantConditions
            return ALL_KINDS.get(kind);
        }

        /**
         * The display domain will never be {@code null}, except for a BOOK!
         */
        @NonNull
        public DomainDefinition getDisplayDomain() {
            return mDisplayDomain;
        }

        void setDisplayDomain(@NonNull final DomainDefinition displayDomain) {
            mDisplayDomain = displayDomain;
        }

        /**
         * Compound key of this RowKind ({@link BooklistGroup}).
         * <p>
         * The name will be of the form 'prefix/<n>' where 'prefix' is the prefix specific
         * to the RowKind, and <n> the id of the row, e.g. 's/18' for Series with id=18
         */
        @NonNull
        CompoundKey getCompoundKey() {
            return mCompoundKey;
        }

        @NonNull
        String getName(@NonNull final Context context) {
            return context.getString(mLabelId);
        }

        @Override
        @NonNull
        public String toString() {
            return "RowKind{"
                   + "name=" + App.getAppContext().getString(mLabelId)
                   + "mKind=" + mKind
                   + "=" + mDisplayDomain
                   + '}';
        }
    }

    /**
     * Represents a collection of domains that make a unique key for a given {@link RowKind}.
     */
    static class CompoundKey
            implements Parcelable {

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<CompoundKey> CREATOR =
                new Creator<CompoundKey>() {
                    @Override
                    public CompoundKey createFromParcel(@NonNull final Parcel source) {
                        return new CompoundKey(source);
                    }

                    @Override
                    public CompoundKey[] newArray(final int size) {
                        return new CompoundKey[size];
                    }
                };

        /** Unique prefix used to represent a key in the hierarchy. */
        @NonNull
        private final String prefix;

        /** List of domains in the key. */
        @NonNull
        private final DomainDefinition[] domains;

        /**
         * Constructor.
         *
         * @param prefix  Unique prefix used to represent a key in the hierarchy.
         * @param domains List of domains in the key
         */
        CompoundKey(@NonNull final String prefix,
                    @NonNull final DomainDefinition... domains) {
            this.prefix = prefix;
            this.domains = domains;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private CompoundKey(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            prefix = in.readString();
            //noinspection ConstantConditions
            domains = in.createTypedArray(DomainDefinition.CREATOR);
        }

        /**
         * Never {@code null} but can be empty (for a BOOK).
         *
         * @return Unique prefix used to represent a key in the hierarchy.
         */
        @NonNull
        String getPrefix() {
            return prefix;
        }

        @NonNull
        DomainDefinition[] getDomains() {
            return domains;
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(prefix);
            dest.writeTypedArray(domains, flags);
        }
    }
}

