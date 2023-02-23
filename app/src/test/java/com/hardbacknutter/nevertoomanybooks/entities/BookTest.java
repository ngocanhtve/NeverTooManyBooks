/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookTest
        extends Base {

    private static final String INVALID_DEFAULT = "Invalid default";

    /** US english book, price in $. */
    @Test
    void preprocessPrices01() {
        setLocale(Locale.US);

        book.putString(DBKey.LANGUAGE, "eng");
        book.putMoney(DBKey.PRICE_LISTED, new Money(1.23d, "USD"));

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        // dump(book);

        assertEquals(1.23d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertEquals("USD", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    /** US english book, price set, currency not set. */
    @Test
    void preprocessPrices02() {
        setLocale(Locale.US);

        book.putString(DBKey.LANGUAGE, "eng");
        book.putMoney(DBKey.PRICE_LISTED, new Money(0d, ""));

        book.putDouble(DBKey.PRICE_PAID, 456.789d);
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        bdh.processPrice(DBKey.PRICE_PAID);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertNull(book.get(DBKey.PRICE_LISTED_CURRENCY, locales));

        assertEquals(456.789d, book.getDouble(DBKey.PRICE_PAID, locales));
        assertNull(book.get(DBKey.PRICE_PAID_CURRENCY, locales));
    }

    @Test
    void preprocessPrices03() {
        setLocale(Locale.FRANCE);

        book.putString(DBKey.LANGUAGE, "fra");
        // as a valid string
        book.putString(DBKey.PRICE_LISTED, "");
        book.putString(DBKey.PRICE_LISTED_CURRENCY, Money.EUR);
        // as an invalid string
        book.putString(DBKey.PRICE_PAID, "test");
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        bdh.processPrice(DBKey.PRICE_PAID);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertEquals(Money.EUR, book.get(DBKey.PRICE_LISTED_CURRENCY, locales));

        // "test" is correct as preprocessPrices should NOT change illegal values.
        assertEquals("test", book.get(DBKey.PRICE_PAID, locales));
        assertNull(book.get(DBKey.PRICE_PAID_CURRENCY, locales));
    }

    @Test
    void preprocessPrices04() {
        setLocale(Locale.FRANCE);

        book.putString(DBKey.LANGUAGE, "eng");
        book.putMoney(DBKey.PRICE_LISTED, new Money(List.of(Locale.ENGLISH),
                                                    "EUR 45"));

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        //dump(book);

        assertEquals(45d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertEquals(Money.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    @Test
    void preprocessExternalIdsForInsert() {

        // Long: valid number
        book.put(DBKey.SID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be removed
        book.put(DBKey.SID_ISFDB, 0L);
        // Long: null -> should be removed
        book.put(DBKey.SID_LAST_DODO_NL, null);
        // Long: blank string -> should be removed
        book.put(DBKey.SID_LIBRARY_THING, "");
        // Long: non-blank string -> should be removed
        book.put(DBKey.SID_STRIP_INFO, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(DBKey.BOOK_ISBN, "test");
        // blank string for a text field -> should be removed
        book.put(DBKey.SID_OPEN_LIBRARY, "");

        // Not tested: null string for a string field..

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processExternalIds(context);
        dump(book);

        assertEquals(2, book.getLong(DBKey.SID_GOODREADS_BOOK));
        assertFalse(book.contains(DBKey.SID_ISFDB));
        assertFalse(book.contains(DBKey.SID_LAST_DODO_NL));
        assertFalse(book.contains(DBKey.SID_LIBRARY_THING));
        assertFalse(book.contains(DBKey.SID_STRIP_INFO));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertFalse(book.contains(DBKey.SID_OPEN_LIBRARY));

        bdh.processNullsAndBlanks(context);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals(2, book.getLong(DBKey.SID_GOODREADS_BOOK));
        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
    }

    @Test
    void preprocessExternalIdsForUpdate() {

        // Long: valid number
        book.put(DBKey.SID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be defaulted to null
        book.put(DBKey.SID_ISFDB, 0L);
        // Long: null
        book.put(DBKey.SID_LAST_DODO_NL, null);
        // Long: blank string -> defaulted to null
        book.put(DBKey.SID_LIBRARY_THING, "");
        // Long: non-blank string -> defaulted to null
        book.put(DBKey.SID_STRIP_INFO, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(DBKey.BOOK_ISBN, "test");
        // blank string for a text field -> defaulted to null
        book.put(DBKey.SID_OPEN_LIBRARY, "");


        // Not tested: null string for a string field..


        final BookDaoHelper bdh = new BookDaoHelper(context, book, false);
        bdh.processExternalIds(context);
        dump(book);

        assertEquals(2, book.getLong(DBKey.SID_GOODREADS_BOOK));
        assertNull(book.get(DBKey.SID_ISFDB, locales));
        assertNull(book.get(DBKey.SID_LAST_DODO_NL, locales));
        assertNull(book.get(DBKey.SID_LIBRARY_THING, locales));
        assertNull(book.get(DBKey.SID_STRIP_INFO, locales));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertNull(book.get(DBKey.SID_OPEN_LIBRARY, locales));


        bdh.processNullsAndBlanks(context);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals(2, book.getLong(DBKey.SID_GOODREADS_BOOK));
        assertNull(book.get(DBKey.SID_ISFDB, locales));
        assertNull(book.get(DBKey.SID_LAST_DODO_NL, locales));
        assertNull(book.get(DBKey.SID_LIBRARY_THING, locales));
        assertNull(book.get(DBKey.SID_STRIP_INFO, locales));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertNull(book.get(DBKey.SID_OPEN_LIBRARY, locales));
    }

    /**
     * If a default was changed then one or more tests in this class will be invalid.
     */
    @Test
    void domainDefaults() {
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_ACQUIRED.getDefault(), INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_START.getDefault(), INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_END.getDefault(), INVALID_DEFAULT);

        assertEquals("0.0",
                     DBDefinitions.DOM_BOOK_PRICE_LISTED.getDefault(), INVALID_DEFAULT);
    }

    /** Domain: text, default "". */
    @Test
    void preprocessNullsAndBlanksForInsert() {
        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(context, book, true);
        bdh.processNullsAndBlanks(context);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is removed.
        assertFalse(book.contains(DBKey.READ_END__DATE));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID, locales));
    }

    @Test
    void preprocessNullsAndBlanksForUpdate() {
        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(context, book, false);
        bdh.processNullsAndBlanks(context);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is replaced by the default
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED, locales));
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID, locales));
    }

    private void dump(@NonNull final DataManager data) {
        for (final String key : data.keySet()) {
            final Object value = data.get(key, locales);
            System.out.println(key + "=" + value);
        }
    }
}
