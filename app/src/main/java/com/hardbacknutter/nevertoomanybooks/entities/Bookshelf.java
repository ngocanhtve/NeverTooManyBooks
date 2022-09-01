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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents a Bookshelf.
 *
 * <strong>Warning:</strong> the {@link Style} association is LAZY.
 * i.o.w. the stored style UUID will/must always be validated before being used.
 * See {@link #getStyle(Context)}.
 */
public class Bookshelf
        implements ParcelableEntity, Mergeable {

    /** Log tag. */
    public static final String TAG = "Bookshelf";

    /** {@link Parcelable}. */
    public static final Creator<Bookshelf> CREATOR = new Creator<>() {
        @Override
        public Bookshelf createFromParcel(@NonNull final Parcel source) {
            return new Bookshelf(source);
        }

        @Override
        public Bookshelf[] newArray(final int size) {
            return new Bookshelf[size];
        }
    };
    /** the 'first' bookshelf created at install time. We allow renaming it, but not deleting. */
    public static final int DEFAULT = 1;

    /**
     * the virtual 'All Books' representing our complete library.
     * Note we use -1, as {@code 0} is generally used for a 'new' item.
     * i.e. when the user creates a new shelf, it has id==0 before it's saved.
     */
    public static final int ALL_BOOKS = -1;
    /** The user preferred shelf as stored in preferences. */
    public static final int PREFERRED = -2;

    /**
     * Preference name - the bookshelf to load next time we startup.
     * Storing the name and not the id. If you export/import... the id will be different.
     */
    private static final String PK_BOOKSHELF_CURRENT = "Bookshelf.CurrentBookshelf";
    @SuppressWarnings("FieldNotUsedInToString")
    private final List<PFilter<?>> filters = new ArrayList<>();

    /** Row ID. */
    private long id;
    /** Bookshelf name. */
    @NonNull
    private String name;
    /**
     * the style uuid. Should never be exposed as it's not validated on its own.
     * Always call {@link #getStyle}}
     */
    @NonNull
    private String styleUuid;

    /**
     * Saved adapter position of top row.
     * See {@link BooksOnBookshelf}#displayList}
     */
    private int firstVisibleItemPosition = RecyclerView.NO_POSITION;

    /**
     * Saved view offset of top row.
     * See {@link BooksOnBookshelf}#displayList}
     */
    private int firstVisibleViewOffset;

    /**
     * Constructor without ID.
     *
     * @param name  for the Bookshelf
     * @param style the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final Style style) {
        this.name = name.trim();
        styleUuid = style.getUuid();
    }

    /**
     * Constructor without ID.
     *
     * @param name      for the Bookshelf
     * @param styleUuid the UUID of the style to apply to this shelf
     */
    public Bookshelf(@NonNull final String name,
                     @NonNull final String styleUuid) {
        this.name = name.trim();
        this.styleUuid = styleUuid;
    }

    /**
     * Full constructor.
     *
     * @param id      the Bookshelf id
     * @param rowData with data
     */
    public Bookshelf(final long id,
                     @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.BOOKSHELF_NAME);
        styleUuid = rowData.getString(DBKey.STYLE_UUID);

        firstVisibleItemPosition = rowData.getInt(DBKey.BOOKSHELF_BL_TOP_POS);
        firstVisibleViewOffset = rowData.getInt(DBKey.BOOKSHELF_BL_TOP_OFFSET);

        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(this.id));
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Bookshelf(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection ConstantConditions
        name = in.readString();
        //noinspection ConstantConditions
        styleUuid = in.readString();

        //ENHANCE: Filters not parcelled, just restore from database
        filters.addAll(ServiceLocator.getInstance().getBookshelfDao().getFilters(id));

        firstVisibleItemPosition = in.readInt();
        firstVisibleViewOffset = in.readInt();
    }

    /**
     * Get the specified bookshelf.
     * <p>
     * The caller MUST be sure the 'fallbackId' is an existing shelf!
     *
     * @param context    Current context
     * @param id         of bookshelf to get
     * @param fallbackId to use if the bookshelf does not exist
     *
     * @return the bookshelf.
     */
    @NonNull
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         final long id,
                                         final long fallbackId) {

        final Bookshelf bookshelf = getBookshelf(context, id);
        if (bookshelf != null) {
            return bookshelf;
        }

        return Objects.requireNonNull(getBookshelf(context, fallbackId));
    }

    /**
     * Get the specified bookshelf.
     *
     * @param context Current context
     * @param id      of bookshelf to get
     *
     * @return the bookshelf, or {@code null} if not found
     */
    @Nullable
    public static Bookshelf getBookshelf(@NonNull final Context context,
                                         final long id) {
        if (id == ALL_BOOKS) {
            final Bookshelf bookshelf = new Bookshelf(
                    context.getString(R.string.bookshelf_all_books),
                    ServiceLocator.getInstance().getStyles().getDefault(context));
            bookshelf.setId(ALL_BOOKS);
            return bookshelf;

        } else if (id == DEFAULT) {
            final Bookshelf bookshelf = new Bookshelf(
                    context.getString(R.string.bookshelf_my_books),
                    ServiceLocator.getInstance().getStyles().getDefault(context));
            bookshelf.setId(DEFAULT);
            return bookshelf;

        } else if (id == PREFERRED) {
            final String name = PreferenceManager.getDefaultSharedPreferences(context)
                                                 .getString(PK_BOOKSHELF_CURRENT, null);
            if (name != null && !name.isEmpty()) {
                return ServiceLocator.getInstance().getBookshelfDao().findByName(name);
            }
            return null;

        } else {
            return ServiceLocator.getInstance().getBookshelfDao().getById(id);
        }
    }

    /**
     * Set this bookshelf as the current/preferred.
     */
    public void setAsPreferred() {
        ServiceLocator.getPreferences().edit().putString(PK_BOOKSHELF_CURRENT, name).apply();
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return name;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    /**
     * Set the style for this bookshelf.
     *
     * @param context Current context
     * @param style   to set; must already exist (id != 0)
     *
     * @throws SanityCheck.MissingValueException if the style is 'new' (id==0)
     */
    public void setStyle(@NonNull final Context context,
                         @NonNull final Style style) {
        SanityCheck.requireNonZero(style.getId(), "style.getId()");

        styleUuid = style.getUuid();

        ServiceLocator.getInstance().getBookshelfDao().update(context, this);

    }

    /**
     * Returns a valid style for this bookshelf.
     *
     * @param context Current context
     *
     * @return the style associated with this bookshelf.
     */
    @NonNull
    public Style getStyle(@NonNull final Context context) {

        // Always validate first
        final Style style = ServiceLocator.getInstance().getStyles()
                                          .getStyleOrDefault(context, styleUuid);
        // the previous uuid might have been overruled so we always refresh it
        styleUuid = style.getUuid();
        return style;
    }

    /**
     * Get the list of filters.
     *
     * @return a new list
     */
    @NonNull
    public List<PFilter<?>> getFilters() {
        return new ArrayList<>(filters);
    }

    public void setFilters(@Nullable final List<PFilter<?>> list) {
        filters.clear();
        if (list != null) {
            filters.addAll(list);
        }
    }

    /**
     * Prune the filters so we only keep the active ones.
     *
     * @param context Current context
     *
     * @return list of active filters
     */
    @NonNull
    public List<PFilter<?>> pruneFilters(@NonNull final Context context) {
        final List<PFilter<?>> list = filters.stream()
                                             .filter(f -> f.isActive(context))
                                             .collect(Collectors.toList());
        filters.clear();
        filters.addAll(list);
        return getFilters();
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     * Normally in the range 0..x, but can theoretically be {@link RecyclerView#NO_POSITION} !
     *
     * @return value for {@link LinearLayoutManager#scrollToPosition(int)}
     *         or {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    @IntRange(from = RecyclerView.NO_POSITION)
    public int getFirstVisibleItemPosition() {
        return firstVisibleItemPosition;
    }

    /**
     * Get the stored position to use for re-displaying this bookshelf's booklist.
     * Due to CoordinatorLayout behaviour, the returned value
     * <strong>can be negative, this is NORMAL</strong>.
     *
     * @return value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    public int getFirstVisibleItemViewOffset() {
        return firstVisibleViewOffset;
    }

    /**
     * Store the current position of the booklist displaying this bookshelf.
     *
     * @param context    Current context
     * @param position   Value of {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * @param viewOffset Value of {@link RecyclerView#getChildAt(int)} #getTop()
     */
    public void setFirstVisibleItemPosition(@NonNull final Context context,
                                            final int position,
                                            final int viewOffset) {
        firstVisibleItemPosition = position;
        firstVisibleViewOffset = viewOffset;

        ServiceLocator.getInstance().getBookshelfDao().update(context, this);
    }

    /**
     * Check the current style and if it had to be corrected, update this shelf in the database.
     *
     * @param context Current context
     */
    public void validateStyle(@NonNull final Context context) {
        final String uuid = styleUuid;
        final Style style = getStyle(context);
        if (!uuid.equals(style.getUuid())) {
            ServiceLocator.getInstance().getBookshelfDao().update(context, this);
        }
    }

    /**
     * Replace local details from another Bookshelf.
     *
     * @param source Bookshelf to copy from
     */
    public void copyFrom(@NonNull final Bookshelf source) {
        name = source.name;
        styleUuid = source.styleUuid;
        filters.clear();
        filters.addAll(source.filters);
        // don't copy the 'top' values.
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeString(styleUuid);

        dest.writeInt(firstVisibleItemPosition);
        dest.writeInt(firstVisibleViewOffset);
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @return the unvalidated style uuid
     */
    @NonNull
    public String getStyleUuid() {
        return styleUuid;
    }

    /**
     * WARNING: exposed ONLY for backup reasons. Do NOT use elsewhere!
     *
     * @param styleUuid the unvalidated style uuid
     */
    public void setStyleUuid(@NonNull final String styleUuid) {
        this.styleUuid = styleUuid;
    }

    /**
     * Check if this is a regular shelf, or if this one represents our complete library.
     *
     * @return {@code true} for ALL books.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAllBooks() {
        return id == ALL_BOOKS;
    }


    /**
     * Diacritic neutral version of {@link  #hashCode()} without id.
     *
     * @return hashcode
     */
    @Override
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(name));
    }


    /**
     * Equality: <strong>id, name</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Equality.
     * <ol>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     *     AND their names are equal</li>
     * <li>Style and positions are ignored</li>
     * </ol>
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
        final Bookshelf that = (Bookshelf) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    @NonNull
    public String toString() {
        return "Bookshelf{"
               + "id=" + id
               + ", name=`" + name + '`'
               + ", firstVisibleItemPosition=" + firstVisibleItemPosition
               + ", firstVisibleViewOffset=" + firstVisibleViewOffset
               + ", styleUuid=" + styleUuid
               + '}';
    }
}
