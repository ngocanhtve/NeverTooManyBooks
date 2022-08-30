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
package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import java.util.ArrayList;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LastDodoTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";
    private LastDodoSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (LastDodoSearchEngine) Site.Type.Data
                .getSite(EngineId.LastDodoNl).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01() {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.lastdodo.nl/nl/items/7323911-de-37ste-parallel";
        final String filename = "/lastdodo/7323911-de-37ste-parallel.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("De 37ste parallel", rawData.getString(DBKey.TITLE));
        assertEquals("9789463064385", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("2018", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("48", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Gekleurd", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(1);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(0);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

//        author = authors.get(2);
//        assertEquals("Sayago", author.getFamilyName());
//        assertEquals("Nuria", author.getGivenNames());
//        assertEquals(Author.TYPE_COLORIST, author.getType());
    }
}
