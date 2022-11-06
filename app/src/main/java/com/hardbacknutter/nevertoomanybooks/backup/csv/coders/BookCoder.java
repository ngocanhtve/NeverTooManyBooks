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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.org.json.JSONException;

/**
 * Note: the keys for the CSV columns are not the same as the internal Book keys
 * due to backward compatibility.
 * TODO: make the current ones LEGACY, and start using the Books keys, but still support reading
 * the old ones.
 * <p>
 * <strong>LIMITATIONS:</strong> Calibre book data is handled, but Calibre library is NOT.
 * The Calibre native string-id is written out with the book.
 * <p>
 * When reading, the Calibre native string-id is checked against already existing data,
 * but if there is no match all Calibre data for the book is discarded.
 * <p>
 * In other words: this coder is NOT a full backup/restore!
 */
public class BookCoder {

    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_TOC = "anthology_titles";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_SERIES = "series_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_AUTHORS = "author_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_PUBLISHERS = "publisher";

    private static final String EMPTY_QUOTED_STRING = "\"\"";
    private static final String COMMA = ",";

    /** Obsolete/alternative header: full given+family author name. */
    private static final String LEGACY_AUTHOR_NAME = "author_name";
    /** Obsolete/alternative header: bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT = "bookshelf_text";
    /** Obsolete, not used. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** Obsolete/alternative header: bookshelf name. Used by pre-1.2 versions. */
    private static final String LEGACY_BOOKSHELF_1_1_x = "bookshelf";

    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?).
     * <p>
     * The fields CSV_COLUMN_* are {@link StringList} encoded
     * <p>
     * External id columns will be added to the end before writing starts.
     */
    private static final String EXPORT_FIELD_HEADERS_BASE =
            '"' + DBKey.PK_ID + '"'
            + COMMA + '"' + DBKey.BOOK_UUID + '"'
            + COMMA + '"' + DBKey.DATE_LAST_UPDATED__UTC + '"'
            + COMMA + '"' + CSV_COLUMN_AUTHORS + '"'
            + COMMA + '"' + DBKey.TITLE + '"'
            + COMMA + '"' + DBKey.BOOK_ISBN + '"'
            + COMMA + '"' + CSV_COLUMN_PUBLISHERS + '"'
            + COMMA + '"' + DBKey.PRINT_RUN + '"'
            + COMMA + '"' + DBKey.BOOK_PUBLICATION__DATE + '"'
            + COMMA + '"' + DBKey.FIRST_PUBLICATION__DATE + '"'
            + COMMA + '"' + DBKey.EDITION__BITMASK + '"'
            + COMMA + '"' + DBKey.RATING + '"'
            + COMMA + '"' + DBKey.BOOKSHELF_NAME + '"'
            + COMMA + '"' + DBKey.READ__BOOL + '"'
            + COMMA + '"' + CSV_COLUMN_SERIES + '"'
            + COMMA + '"' + DBKey.PAGE_COUNT + '"'
            + COMMA + '"' + DBKey.PERSONAL_NOTES + '"'
            + COMMA + '"' + DBKey.BOOK_CONDITION + '"'
            + COMMA + '"' + DBKey.BOOK_CONDITION_COVER + '"'

            + COMMA + '"' + DBKey.PRICE_LISTED + '"'
            + COMMA + '"' + DBKey.PRICE_LISTED_CURRENCY + '"'
            + COMMA + '"' + DBKey.PRICE_PAID + '"'
            + COMMA + '"' + DBKey.PRICE_PAID_CURRENCY + '"'
            + COMMA + '"' + DBKey.DATE_ACQUIRED + '"'

            + COMMA + '"' + DBKey.TOC_TYPE__BITMASK + '"'
            + COMMA + '"' + DBKey.LOCATION + '"'
            + COMMA + '"' + DBKey.READ_START__DATE + '"'
            + COMMA + '"' + DBKey.READ_END__DATE + '"'
            + COMMA + '"' + DBKey.FORMAT + '"'
            + COMMA + '"' + DBKey.COLOR + '"'
            + COMMA + '"' + DBKey.SIGNED__BOOL + '"'
            + COMMA + '"' + DBKey.LOANEE_NAME + '"'
            + COMMA + '"' + CSV_COLUMN_TOC + '"'
            + COMMA + '"' + DBKey.DESCRIPTION + '"'
            + COMMA + '"' + DBKey.GENRE + '"'
            + COMMA + '"' + DBKey.LANGUAGE + '"'
            + COMMA + '"' + DBKey.DATE_ADDED__UTC + '"'

            // the Calibre book ID/UUID as they define the book on the Calibre Server
            + COMMA + '"' + DBKey.CALIBRE_BOOK_ID + '"'
            + COMMA + '"' + DBKey.CALIBRE_BOOK_UUID + '"'
            + COMMA + '"' + DBKey.CALIBRE_BOOK_MAIN_FORMAT + '"'
            // we write the String ID! not the internal row id
            + COMMA + '"' + DBKey.CALIBRE_LIBRARY_STRING_ID + '"';

    private final StringList<Author> authorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> seriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> publisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> tocCoder = new StringList<>(new TocEntryCoder());
    private final StringList<Bookshelf> bookshelfCoder;

    @NonNull
    private final List<Domain> externalIdDomains;

    private final ServiceLocator serviceLocator;
    @Nullable
    private Map<Long, String> calibreLibraryId2StrMap;
    @Nullable
    private Map<String, Long> calibreLibraryStr2IdMap;

    public BookCoder(@NonNull final Context context) {
        serviceLocator = ServiceLocator.getInstance();

        bookshelfCoder = new StringList<>(new BookshelfCoder(context));
        externalIdDomains = SearchEngineConfig.getExternalIdDomains();
    }

    private void buildCalibreMappings() {
        calibreLibraryId2StrMap = new HashMap<>();
        calibreLibraryStr2IdMap = new HashMap<>();

        ServiceLocator.getInstance().getCalibreLibraryDao().getAllLibraries()
                      .forEach(library -> {
                          calibreLibraryId2StrMap.put(library.getId(),
                                                      library.getLibraryStringId());
                          calibreLibraryStr2IdMap.put(library.getLibraryStringId(),
                                                      library.getId());
                      });

    }

    @NonNull
    public String createHeader() {
        // row 0 with the column labels
        final StringBuilder columnLabels = new StringBuilder(EXPORT_FIELD_HEADERS_BASE);
        for (final Domain domain : externalIdDomains) {
            columnLabels.append(COMMA).append('"').append(domain.getName()).append('"');
        }
        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        return columnLabels.toString();
    }

    @NonNull
    public String encode(@NonNull final Book book)
            throws JSONException {
        final StringJoiner line = new StringJoiner(",");

        line.add(escapeLong(book.getLong(DBKey.PK_ID)));
        line.add(escape(book.getString(DBKey.BOOK_UUID)));
        line.add(escape(book.getString(DBKey.DATE_LAST_UPDATED__UTC)));
        line.add(escape(authorCoder.encodeList(book.getAuthors())));
        line.add(escape(book.getString(DBKey.TITLE)));
        line.add(escape(book.getString(DBKey.BOOK_ISBN)));
        line.add(escape(publisherCoder.encodeList(book.getPublishers())));
        line.add(escape(book.getString(DBKey.PRINT_RUN)));
        line.add(escape(book.getString(DBKey.BOOK_PUBLICATION__DATE)));
        line.add(escape(book.getString(DBKey.FIRST_PUBLICATION__DATE)));
        line.add(escapeLong(book.getLong(DBKey.EDITION__BITMASK)));
        line.add(escapeDouble(book.getFloat(DBKey.RATING)));
        line.add(escape(bookshelfCoder.encodeList(book.getBookshelves())));
        line.add(escapeLong(book.getInt(DBKey.READ__BOOL)));
        line.add(escape(seriesCoder.encodeList(book.getSeries())));
        line.add(escape(book.getString(DBKey.PAGE_COUNT)));
        line.add(escape(book.getString(DBKey.PERSONAL_NOTES)));
        line.add(escapeLong(book.getInt(DBKey.BOOK_CONDITION)));
        line.add(escapeLong(book.getInt(DBKey.BOOK_CONDITION_COVER)));
        line.add(escapeDouble(book.getDouble(DBKey.PRICE_LISTED)));
        line.add(escape(book.getString(DBKey.PRICE_LISTED_CURRENCY)));
        line.add(escapeDouble(book.getDouble(DBKey.PRICE_PAID)));
        line.add(escape(book.getString(DBKey.PRICE_PAID_CURRENCY)));
        line.add(escape(book.getString(DBKey.DATE_ACQUIRED)));
        line.add(escapeLong(book.getLong(DBKey.TOC_TYPE__BITMASK)));
        line.add(escape(book.getString(DBKey.LOCATION)));
        line.add(escape(book.getString(DBKey.READ_START__DATE)));
        line.add(escape(book.getString(DBKey.READ_END__DATE)));
        line.add(escape(book.getString(DBKey.FORMAT)));
        line.add(escape(book.getString(DBKey.COLOR)));
        line.add(escapeLong(book.getInt(DBKey.SIGNED__BOOL)));
        line.add(escape(book.getString(DBKey.LOANEE_NAME)));
        line.add(escape(tocCoder.encodeList(book.getToc())));
        line.add(escape(book.getString(DBKey.DESCRIPTION)));
        line.add(escape(book.getString(DBKey.GENRE)));
        line.add(escape(book.getString(DBKey.LANGUAGE)));
        line.add(escape(book.getString(DBKey.DATE_ADDED__UTC)));

        line.add(escapeLong(book.getInt(DBKey.CALIBRE_BOOK_ID)));
        line.add(escape(book.getString(DBKey.CALIBRE_BOOK_UUID)));
        line.add(escape(book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT)));

        // we write the Calibre String ID! not the internal row id
        final long clbRowId = book.getLong(DBKey.FK_CALIBRE_LIBRARY);
        if (clbRowId != 0) {
            if (calibreLibraryId2StrMap == null) {
                buildCalibreMappings();
            }
            final String clbStrId = calibreLibraryId2StrMap.get(clbRowId);
            // Guard against obsolete libraries (not actually sure this is needed... paranoia)
            if (clbStrId != null && !clbStrId.isEmpty()) {
                line.add(escape(clbStrId));
            } else {
                line.add("");
            }
        } else {
            line.add("");
        }

        // external ID's
        for (final Domain domain : externalIdDomains) {
            line.add(escape(book.getString(domain.getName())));
        }
        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        return line.toString();
    }

    @NonNull
    private CharSequence escapeLong(final long cell) {
        return escape(String.valueOf(cell));
    }

    @NonNull
    private CharSequence escapeDouble(final double cell) {
        return escape(String.valueOf(cell));
    }

    /**
     * Double quote all "'s and remove all newlines.
     *
     * @param source to escape
     *
     * @return The encoded cell enclosed in escaped quotes
     */
    @NonNull
    private CharSequence escape(@Nullable final String source) {
        if (source == null || "null".equalsIgnoreCase(source) || source.trim().isEmpty()) {
            return EMPTY_QUOTED_STRING;
        }

        final StringBuilder sb = new StringBuilder("\"");
        final int endPos = source.length() - 1;
        int pos = 0;

        try {
            while (pos <= endPos) {
                final char c = source.charAt(pos);
                switch (c) {
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;

                    case '"':
                        // quotes are escaped by doubling them
                        sb.append(EMPTY_QUOTED_STRING);
                        break;

                    default:
                        sb.append(c);
                        break;
                }
                pos++;
            }
            return sb.append('"').toString();

        } catch (@NonNull final Exception e) {
            return EMPTY_QUOTED_STRING;
        }
    }


    /**
     * Database access is strictly limited to fetching ID's for the list elements.
     *
     * @param context Current context
     *
     * @return the decoded book
     */
    public Book decode(@NonNull final Context context,
                       @NonNull final String[] csvColumnNames,
                       @NonNull final String[] csvDataRow) {
        final Book book = new Book();

        // Read all columns of the current row into the Bundle.
        // Note that some of them require further processing before being valid.
        for (int i = 0; i < csvColumnNames.length; i++) {
            book.putString(csvColumnNames[i], csvDataRow[i]);
        }

        // check/add a title
        if (book.getTitle().isEmpty()) {
            book.putString(DBKey.TITLE, context.getString(R.string.unknown_title));
        }

        // check/fix the language
        final Locale bookLocale = book.getLocale(context);

        // Database access is strictly limited to fetching ID's for the list elements.
        decodeAuthors(context, book, bookLocale);
        decodeSeries(context, book, bookLocale);
        decodePublishers(context, book, bookLocale);
        decodeToc(context, book, bookLocale);
        decodeBookshelves(book);
        decodeCalibreData(book);

        //FIXME: implement full parsing/formatting of incoming dates for validity
        //verifyDates(context, bookDao, book);

        return book;
    }

    private void decodeCalibreData(@NonNull final Book /* in/out */ book) {
        // we need to convert the string id to the row id.
        final String stringId = book.getString(DBKey.CALIBRE_LIBRARY_STRING_ID);
        // and discard the string-id
        book.remove(DBKey.CALIBRE_LIBRARY_STRING_ID);

        if (!stringId.isEmpty()) {
            if (calibreLibraryStr2IdMap == null) {
                buildCalibreMappings();
            }
            final Long id = calibreLibraryStr2IdMap.get(stringId);
            if (id != null) {
                book.putLong(DBKey.FK_CALIBRE_LIBRARY, id);
            } else {
                // Don't try to recover; just remove all calibre keys from this book.
                book.setCalibreLibrary(null);
            }
        }
    }

    /**
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param book the book
     */
    private void decodeBookshelves(@NonNull final Book /* in/out */ book) {

        String encodedList = null;

        if (book.contains(DBKey.BOOKSHELF_NAME)) {
            // current version
            encodedList = book.getString(DBKey.BOOKSHELF_NAME);

        } else if (book.contains(LEGACY_BOOKSHELF_1_1_x)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_1_1_x);

        } else if (book.contains(LEGACY_BOOKSHELF_TEXT)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_TEXT);
        }

        if (encodedList != null && !encodedList.isEmpty()) {
            final ArrayList<Bookshelf> bookshelves = bookshelfCoder.decodeList(encodedList);
            if (!bookshelves.isEmpty()) {
                serviceLocator.getBookshelfDao().pruneList(bookshelves);
                book.setBookshelves(bookshelves);
            }
        }

        book.remove(LEGACY_BOOKSHELF_ID);
        book.remove(LEGACY_BOOKSHELF_TEXT);
        book.remove(LEGACY_BOOKSHELF_1_1_x);
        book.remove(DBKey.BOOKSHELF_NAME);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeAuthors(@NonNull final Context context,
                               @NonNull final Book /* in/out */ book,
                               @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_AUTHORS);
        book.remove(CSV_COLUMN_AUTHORS);

        final ArrayList<Author> list;
        if (encodedList.isEmpty()) {
            // check for individual author (full/family/given) fields in the input
            list = new ArrayList<>();
            if (book.contains(DBKey.AUTHOR_FORMATTED)) {
                final String name = book.getString(DBKey.AUTHOR_FORMATTED);
                if (!name.isEmpty()) {
                    list.add(Author.from(name));
                }
                book.remove(DBKey.AUTHOR_FORMATTED);

            } else if (book.contains(DBKey.AUTHOR_FAMILY_NAME)) {
                final String family = book.getString(DBKey.AUTHOR_FAMILY_NAME);
                if (!family.isEmpty()) {
                    // given will be "" if it's not present
                    final String given = book.getString(DBKey.AUTHOR_GIVEN_NAMES);
                    list.add(new Author(family, given));
                }
                book.remove(DBKey.AUTHOR_FAMILY_NAME);
                book.remove(DBKey.AUTHOR_GIVEN_NAMES);

            } else if (book.contains(LEGACY_AUTHOR_NAME)) {
                final String a = book.getString(LEGACY_AUTHOR_NAME);
                if (!a.isEmpty()) {
                    list.add(Author.from(a));
                }
                book.remove(LEGACY_AUTHOR_NAME);
            }
        } else {
            list = authorCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getAuthorDao().pruneList(context, list, false, bookLocale);
            }
        }

        // we MUST have an author.
        if (list.isEmpty()) {
            list.add(Author.createUnknownAuthor(context));
        }
        book.setAuthors(list);
    }

    /**
     * Process the list of Series.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeSeries(@NonNull final Context context,
                              @NonNull final Book /* in/out */ book,
                              @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_SERIES);
        book.remove(CSV_COLUMN_SERIES);

        if (encodedList.isEmpty()) {
            // check for individual series title/number fields in the input
            if (book.contains(DBKey.SERIES_TITLE)) {
                final String title = book.getString(DBKey.SERIES_TITLE);
                if (!title.isEmpty()) {
                    final Series series = new Series(title);
                    // number will be "" if it's not present
                    series.setNumber(book.getString(DBKey.SERIES_BOOK_NUMBER));
                    final List<Series> list = new ArrayList<>();
                    list.add(series);
                    book.setSeries(list);
                }
                book.remove(DBKey.SERIES_TITLE);
                book.remove(DBKey.SERIES_BOOK_NUMBER);
            }
        } else {
            final ArrayList<Series> list = seriesCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getSeriesDao().pruneList(context, list, false, bookLocale);
                book.setSeries(list);
            }
        }
    }

    /**
     * Process the list of Publishers.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodePublishers(@NonNull final Context context,
                                  @NonNull final Book /* in/out */ book,
                                  @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_PUBLISHERS);
        book.remove(CSV_COLUMN_PUBLISHERS);

        if (!encodedList.isEmpty()) {
            final ArrayList<Publisher> list = publisherCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getPublisherDao().pruneList(context, list, false, bookLocale);
                book.setPublishers(list);
            }
        }
    }

    /**
     * Process the list of Toc entries.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignores the actual value of the {@link DBDefinitions#DOM_BOOK_TOC_TYPE}.
     * It will be computed when storing the book data.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeToc(@NonNull final Context context,
                           @NonNull final Book /* in/out */ book,
                           @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_TOC);
        book.remove(CSV_COLUMN_TOC);

        if (!encodedList.isEmpty()) {
            final ArrayList<TocEntry> list = tocCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getTocEntryDao().pruneList(context, list, false, bookLocale);
                book.setToc(list);
            }
        }
    }

}
