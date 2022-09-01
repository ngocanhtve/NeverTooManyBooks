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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.EOFException;
import java.io.IOException;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

import static com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine.PK_SERIES_FROM_TOC;
import static org.junit.jupiter.api.Assertions.assertTrue;


class IsfdbXmlPublicationTest
        extends Base {

    private IsfdbSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (IsfdbSearchEngine) Site.Type.Data
                .getSite(EngineId.Isfdb).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());

        // Override the default 'false'
        mockPreferences.edit().putBoolean(PK_SERIES_FROM_TOC, true).apply();

        final boolean b = PreferenceManager.getDefaultSharedPreferences(context)
                                           .getBoolean(PK_SERIES_FROM_TOC, false);
        assertTrue(b);
    }

    @Test
    void singleByExtId()
            throws ParserConfigurationException, IOException, SAXException {
        setLocale(Locale.UK);
        final String filename = "/isfdb/425189.xml";

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(searchEngine,
                                                new boolean[]{false, false},
                                                1,
                                                searchEngine.getLocale(context));

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        try {
            parser.parse(this.getClass().getResourceAsStream(filename), listHandler);
        } catch (@NonNull final SAXException e) {
            if (!(e.getCause() instanceof EOFException)) {
                throw e;
            }
        }

        System.out.println(listHandler.getResult());
    }
}
