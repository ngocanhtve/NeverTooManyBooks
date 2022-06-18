/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * Supports all types from {@link DataManager#put} with the exception of {@link Serializable}.
 * <p>
 * {@link #encode} omits {@code null} values, numeric {@code 0} values and empty lists.
 */
public class BookCoder
        implements JsonCoder<Book> {

    private final JsonCoder<Author> mAuthorCoder = new AuthorCoder();
    private final JsonCoder<Bookshelf> mBookshelfCoder;
    private final JsonCoder<CalibreLibrary> mCalibreLibraryCoder;
    private final JsonCoder<Publisher> mPublisherCoder = new PublisherCoder();
    private final JsonCoder<Series> mSeriesCoder = new SeriesCoder();
    private final JsonCoder<TocEntry> mTocEntryCoder = new TocEntryCoder();

    /**
     * Constructor.
     */
    public BookCoder(@NonNull final Context context) {

        mBookshelfCoder = new BookshelfCoder(context);
        mCalibreLibraryCoder = new CalibreLibraryCoder(context);
    }

    @Override
    @NonNull
    public JSONObject encode(@NonNull final Book book)
            throws JSONException {
        final JSONObject out = new JSONObject();
        for (final String key : book.keySet()) {
            encode(out, book, key);
        }
        return out;
    }

    private void encode(@NonNull final JSONObject out,
                        @NonNull final Book book,
                        @NonNull final String key)
            throws JSONException {

        final Object element = book.get(key);

        // Special keys first.

        // The presence of FK_CALIBRE_LIBRARY (a row id) indicates there IS a calibre
        // library for this book but there is no other/more library data on the book itself.
        // We need to explicitly load the library and encode a reference for it.
        if (DBKey.FK_CALIBRE_LIBRARY.equals(key)) {
            final CalibreLibrary library = book.getCalibreLibrary();
            if (library != null) {
                // FK as it's a reference
                out.put(DBKey.FK_CALIBRE_LIBRARY,
                        mCalibreLibraryCoder.encodeReference(library));
            }

        } else if (element instanceof String) {
            if (!((String) element).isEmpty()) {
                out.put(key, element);
            }

        } else if (element instanceof Money) {
            // Only write the value. The currency will be covered as a plain String type key.
            // We could just treat Money as a Number (which it is) but JSONStringer uses
            // 'toString' which caused some issues... so keeping this as a reminder.
            final double d = ((Money) element).doubleValue();
            if (d != 0) {
                out.put(key, d);
            }

        } else if (element instanceof Number) {
            if (((Number) element).doubleValue() != 0) {
                out.put(key, element);
            }
        } else if (element instanceof Boolean) {
            // always write regardless of being 'false'
            out.put(key, element);

        } else if (element instanceof ArrayList) {
            switch (key) {
                case Book.BKEY_AUTHOR_LIST: {
                    final List<Author> list = book.getAuthors();
                    if (!list.isEmpty()) {
                        out.put(key, mAuthorCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_BOOKSHELF_LIST: {
                    final List<Bookshelf> list = book.getBookshelves();
                    if (!list.isEmpty()) {
                        // FK as it's a reference
                        out.put(DBKey.FK_BOOKSHELF, mBookshelfCoder.encodeReference(list));
                    }
                    break;
                }
                case Book.BKEY_PUBLISHER_LIST: {
                    final List<Publisher> list = book.getPublishers();
                    if (!list.isEmpty()) {
                        out.put(key, mPublisherCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_SERIES_LIST: {
                    final List<Series> list = book.getSeries();
                    if (!list.isEmpty()) {
                        out.put(key, mSeriesCoder.encode(list));
                    }
                    break;
                }
                case Book.BKEY_TOC_LIST: {
                    final List<TocEntry> list = book.getToc();
                    if (!list.isEmpty()) {
                        out.put(key, mTocEntryCoder.encode(list));
                    }
                    break;
                }

                default:
                    throw new IllegalArgumentException("key=" + key + "|: " + element);
            }

        } else if (element instanceof Parcelable) {
            // skip, 2021-02-13: the only one in use for now is the Calibre Library,
            // which is already handled - see above.

        } else if (element instanceof Serializable) {
            throw new IllegalArgumentException("Serializable not implemented for: " + element);

        } else if (element != null) {
            throw new IllegalArgumentException("key=" + key + "|o=" + element);
        }
    }

    @Override
    @NonNull
    public Book decode(@NonNull final JSONObject data)
            throws JSONException {
        final Book book = new Book();
        final Iterator<String> it = data.keys();
        while (it.hasNext()) {
            final String key = it.next();
            switch (key) {
                case Book.BKEY_BOOKSHELF_LIST:
                    // Full object
                    book.setBookshelves(mBookshelfCoder.decode(data.getJSONArray(key)));
                    break;

                case DBKey.FK_BOOKSHELF:
                    // Reference; if the reference is not found,
                    // the book will be put on the preferred (or default) Bookshelf.
                    book.setBookshelves(mBookshelfCoder.decodeReference(data.getJSONArray(key)));
                    break;

                case Book.BKEY_CALIBRE_LIBRARY:
                    // Full object
                    book.setCalibreLibrary(
                            mCalibreLibraryCoder.decode(data.getJSONObject(key)));
                    break;

                case DBKey.FK_CALIBRE_LIBRARY:
                    // Reference; if the reference is not found,
                    // the Calibre data is removed from the book
                    book.setCalibreLibrary(
                            mCalibreLibraryCoder.decodeReference(data.getJSONObject(key))
                                                .orElse(null));
                    break;



                case Book.BKEY_AUTHOR_LIST:
                    book.setAuthors(mAuthorCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_PUBLISHER_LIST:
                    book.setPublishers(mPublisherCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_SERIES_LIST:
                    book.setSeries(mSeriesCoder.decode(data.getJSONArray(key)));
                    break;

                case Book.BKEY_TOC_LIST:
                    book.setToc(mTocEntryCoder.decode(data.getJSONArray(key)));
                    break;


                default: {
                    final Object o = data.get(key);
                    if (o instanceof String) {
                        book.putString(key, (String) o);
                    } else if (o instanceof Long) {
                        book.putLong(key, (Long) o);
                    } else if (o instanceof Integer) {
                        book.putInt(key, (Integer) o);
                    } else if (o instanceof Double) {
                        // Double covers 'Money'. The currency is done with a separate String type.
                        book.putDouble(key, (Double) o);
                    } else if (o instanceof Float) {
                        book.putFloat(key, (Float) o);
                    } else if (o instanceof Boolean) {
                        book.putBoolean(key, (Boolean) o);

                    } else if (o instanceof BigInteger) {
                        // added since org.json:json:20201115
                        book.putLong(key, ((BigInteger) o).longValue());
                    } else if (o instanceof BigDecimal) {
                        // added since org.json:json:20201115
                        book.putDouble(key, ((BigDecimal) o).doubleValue());

                    } else {
                        throw new IllegalArgumentException("key=" + key
                                                           + "|type=" + o.getClass().getName()
                                                           + "|o=" + o);
                    }
                }
            }
        }

        return book;
    }
}
