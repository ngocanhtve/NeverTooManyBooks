/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.entities;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents the underlying data for a book.
 *
 * @author pjw
 */
public class Book extends DataManager {

    /** Key for special field */
    public static final String IS_ANTHOLOGY = "+IsAnthology";
    /** Key for special field */
    private static final String BOOKSHELF_LIST = "+BookshelfList";
    /** Key for special field */
    private static final String BOOKSHELF_TEXT = "+BookshelfText";
    /** Row ID for book */
    private long mBookId;

    public Book() {
        this(0, null);
    }

    public Book(final long bookId) {
        this(bookId, null);
    }

    /**
     * Constructor
     *
     * @param bundle with book data (may be null). TOMF Lists in the Bundle must be put in there as String Encoded
     */
    public Book(@Nullable final Bundle bundle) {
        this(0L, bundle);
    }

    /**
     * Constructor
     *
     * @param cursor with book data
     */
    public Book(@NonNull final Cursor cursor) {
        putAll(cursor);
    }

    /**
     * Constructor
     *
     * @param bookId of book (may be 0 for new)
     * @param bundle Bundle with book data (may be null).  TOMF Lists in the Bundle must be put in there as String Encoded
     */
    public Book(final long bookId, @Nullable final Bundle bundle) {
        this.mBookId = bookId;

        // Load from bundle or database
        if (bundle != null) {
            putAll(bundle);
        } else if (this.mBookId > 0) {
            reload();
        }
        // Create special validators
        initValidators();
    }

    /**
     * Erase everything in this instance and reset the special handlers
     *
     * @return self, for chaining
     */
    @Override
    @NonNull
    public DataManager clear() {
        super.clear();
        // Create special validators
        initValidators();
        return this;
    }

    //TODO: can we simplify this ? not just a 'string' but structured data with proper ID's
    public String getBookshelfListAsEncodedString() {
        return getString(BOOKSHELF_LIST);
    }

    public void setBookshelfListAsEncodedString(@NonNull final String encodedList) {
        putString(BOOKSHELF_LIST, encodedList);
    }

    /**
     * @return a csv formatted list of bookshelves
     */
    @NonNull
    public String getBookshelfDisplayText() {
        final List<String> list = ArrayUtils.decodeList(Bookshelf.SEPARATOR, getString(BOOKSHELF_LIST));
        if (list.size() == 0)
            return "";

        final StringBuilder text = new StringBuilder(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            text.append(Bookshelf.SEPARATOR).append(" ").append(list.get(i));
        }
        return text.toString();
    }


    /**
     * Accessor
     */
    public long getBookId() {
        return mBookId;
    }

    /**
     * Load the book details from the database
     */
    public void reload() {
        // If ID = 0, no details in DB
        if (mBookId == 0)
            return;

        // Connect to DB and get cursor for book details
        CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();
        try (BooksCursor book = db.fetchBookById(mBookId)) {
            // Put all cursor fields in collection
            putAll(book);

            // Get author, series, bookshelf and anthology title lists
            setAuthorList(db.getBookAuthorList(mBookId));
            setSeriesList(db.getBookSeriesList(mBookId));
            setContentList(db.getBookAnthologyTitleList(mBookId));

            setBookshelfListAsEncodedString(db.getBookshelvesByBookIdAsStringList(mBookId));

        } catch (Exception e) {
            Logger.logError(e);
        } finally {
            db.close();
        }
    }

    /**
     * Special Accessor
     */
    public void setAuthorList(@NonNull final ArrayList<Author> list) {
        putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Special Accessor
     */
    public void setSeriesList(@NonNull final ArrayList<Series> list) {
        putSerializable(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Special Accessor
     */
    public void setContentList(@NonNull final ArrayList<AnthologyTitle> list) {
        putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, list);
    }

    /**
     * Special Accessor.
     *
     * Build a formatted string for author list.
     */
    @Nullable
    public String getAuthorTextShort() {
        String newText;
        List<Author> list = getAuthorsList();
        if (list.size() == 0) {
            return null;
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
            }
            return newText;
        }
    }



//    /**
//     * Special Accessor.
//     *
//     * Build a formatted string for series list.
//     */
//    public String getSeriesTextShort() {
//        String newText;
//        ArrayList<Series> list = getSeriesList();
//        if (list.size() == 0) {
//            newText = null;
//        } else {
//            newText = list.get(0).getDisplayName();
//            if (list.size() > 1) {
//                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
//            }
//        }
//        return newText;
//    }

    /**
     * Utility routine to get an author list from a data manager
     *
     * @return List of authors
     */
    @NonNull
    public ArrayList<Author> getAuthorsList() {
        ArrayList<Author> list = (ArrayList<Author>) getSerializable(UniqueId.BKEY_AUTHOR_ARRAY);
        return list != null ? list : new ArrayList<Author>();
    }

    /**
     * Utility routine to get an series list from a data manager
     *
     * @return List of series
     */
    @NonNull
    public ArrayList<Series> getSeriesList() {
        ArrayList<Series> list = (ArrayList<Series>) getSerializable(UniqueId.BKEY_SERIES_ARRAY);
        return list != null ? list : new ArrayList<Series>();
    }

    /**
     * Utility routine to get a Content (an AnthologyTitle list) from a data manager
     *
     * @return List of anthology titles
     */
    @NonNull
    public ArrayList<AnthologyTitle> getContentList() {
        ArrayList<AnthologyTitle> list = (ArrayList<AnthologyTitle>) getSerializable(UniqueId.BKEY_ANTHOLOGY_DETAILS);
        return list != null ? list : new ArrayList<AnthologyTitle>();
    }

    /**
     * Convenience Accessor
     */
    public boolean isRead() {
        return getInt(UniqueId.KEY_BOOK_READ) != 0;
    }

    /**
     * Convenience Accessor
     */
    public boolean isSigned() {
        return getInt(UniqueId.KEY_BOOK_SIGNED) != 0;
    }

    /**
     * Update author details from DB
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final CatalogueDBAdapter db) {
        ArrayList<Author> list = getAuthorsList();
        for (Author a : list) {
            db.refreshAuthor(a);
        }
        setAuthorList(list);
    }

    /**
     * Cleanup thumbnails from underlying data
     */
    public void cleanupThumbnails() {
        ImageUtils.cleanupThumbnails(mBundle);
    }

    /**
     * Get the underlying raw data.
     * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
     * 2018-09-29: so we clone it before.
     */
    @NonNull
    public Bundle getRawData() {
        return (Bundle) mBundle.clone();
    }

    /**
     * Build any special purpose validators
     */
    private void initValidators() {
        addValidator(UniqueId.KEY_TITLE, nonBlankValidator);
        addValidator(UniqueId.KEY_ANTHOLOGY_MASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_LIST_PRICE, blankOrFloatValidator);
        addValidator(UniqueId.KEY_BOOK_PAGES, blankOrIntegerValidator);

        /* Anthology needs special handling, and we use a formatter to do this. If the original
         * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
         * However...if if the original value was 2, then we want setting/clearing to alternate
         * between 2 and 0, not 1 and 0.
         * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
         * We also store it in the tag field so that it is automatically serialized with the
         * activity. */
        addAccessor(IS_ANTHOLOGY, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                Integer mask = data.getInt(UniqueId.KEY_ANTHOLOGY_MASK);
                return mask != 0 ? "1" : "0";
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                Integer mask = getInt(UniqueId.KEY_ANTHOLOGY_MASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    mask |= 1;
                } else {
                    mask &= 0xFFFFFFFE;
                }
                putInt(UniqueId.KEY_ANTHOLOGY_MASK, mask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_ANTHOLOGY_MASK);
            }

        });

        /* Make a csv formatted list of bookshelves */
        addAccessor(BOOKSHELF_TEXT, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return getBookshelfDisplayText();
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                throw new IllegalStateException("Bookshelf Text can not be set");
            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return !getBookshelfDisplayText().isEmpty();
            }
        });

        // Whenever the row ID is written, make sure mBookId is updated.
        addAccessor(UniqueId.KEY_ID, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return Datum.toLong(rawData.get(datum.getKey()));
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                rawData.putLong(datum.getKey(), Datum.toLong(value));
                mBookId = rawData.getLong(datum.getKey());
            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return true;
            }
        });
    }
}
