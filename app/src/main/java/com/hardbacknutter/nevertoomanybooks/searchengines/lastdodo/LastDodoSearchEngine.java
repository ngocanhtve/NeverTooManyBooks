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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Current hardcoded to only search comics; could be extended to also search generic books.
 */
public class LastDodoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId {

    /**
     * Param 1: external book ID; really a 'long'.
     * Param 2: 147==comics
     */
    private static final String BY_EXTERNAL_ID = "/nl/items/%1$s";
    /**
     * Param 1: ISBN. Must include the '-' characters! (2022-05-31)
     * Param 2: 147==comics.
     */
    private static final String BY_ISBN = "/nl/areas/search?q=%1$s&type_id=147";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public LastDodoSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(LastDodoSearchEngine.class,
                                              SearchSites.LAST_DODO,
                                              R.string.site_lastdodo_nl,
                                              "lastdodo",
                                              "https://www.lastdodo.nl")
                .setCountry("NL", "nl")
                .setFilenameSuffix("LDD")
                .setSearchPrefersIsbn10(true)

                .setDomainKey(DBKey.SID_LAST_DODO_NL)
                .setDomainViewId(R.id.site_last_dodo_nl)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LAST_DODO_NL)
                .build();
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
    }

    @NonNull
    public Bundle searchByExternalId(@NonNull final Context context,
                                     @NonNull final String externalId,
                                     @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Bundle bookData = ServiceLocator.newBundle();

        final String url = getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, bookData);
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Bundle bookData = ServiceLocator.newBundle();

        // This is silly...
        // 2022-05-31: searching the site with the ISBN now REQUIRES the dashes between
        // the digits.
        final String url = getSiteUrl() + String.format(BY_ISBN, ISBN.prettyPrint(validIsbn));
        final Document document = loadDocument(context, url);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, bookData);
        }
        return bookData;
    }


    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.card-body");
        // it will be null if there were no results.
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                String url = urlElement.attr("href");
                // sanity check - it normally does NOT have the protocol/site part
                if (url.startsWith("/")) {
                    url = getSiteUrl() + url;
                }
                final Document redirected = loadDocument(context, url);
                if (!isCancelled()) {
                    parse(context, redirected, fetchCovers, bookData);
                }
            }
        }
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws StorageException, SearchException {
        super.parse(context, document, fetchCovers, bookData);

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        final Elements sections = document.select("section.inner");
        if (sections.size() < 1) {
            return;
        }

        final Element sectionTitle = sections.get(0).selectFirst("h2.section-title");
        if (sectionTitle == null || !"Catalogusgegevens".equals(sectionTitle.text())) {
            return;
        }

        String tmpString;

        for (final Element divRows : sections.get(0).select("div.row-information")) {
            final Element th = divRows.selectFirst("div.label");
            final Element td = divRows.selectFirst("div.value");
            if (th != null && td != null) {

                switch (th.text()) {
                    case "LastDodo nummer":
                        processText(td, DBKey.SID_LAST_DODO_NL, bookData);
                        break;

                    case "Titel":
                        processText(td, DBKey.TITLE, bookData);
                        break;

                    case "Serie / held":
                        processSeries(td);
                        break;

                    case "Reeks":
                        processText(td.child(0), SiteField.REEKS, bookData);
                        break;

                    case "Nummer in reeks":
                        tmpSeriesNr = td.text().trim();
                        break;

                    case "Nummertoevoeging":
                        tmpString = td.text().trim();
                        if (!tmpString.isEmpty()) {
                            //noinspection StringConcatenationInLoop
                            tmpSeriesNr += '|' + tmpString;
                        }
                        break;

                    case "Tekenaar": {
                        processAuthor(td, Author.TYPE_ARTIST);
                        break;
                    }
                    case "Scenarist": {
                        processAuthor(td, Author.TYPE_WRITER);
                        break;
                    }
                    case "Uitgeverij":
                        processPublisher(td);
                        break;

                    case "Jaar":
                        processText(td, DBKey.BOOK_PUBLICATION__DATE, bookData);
                        break;

                    case "Cover":
                        processText(td, DBKey.FORMAT, bookData);
                        break;

                    case "Druk":
                        processText(td, SiteField.PRINTING, bookData);
                        break;

                    case "Inkleuring":
                        processText(td, DBKey.COLOR, bookData);
                        break;

                    case "ISBN":
                        tmpString = td.text();
                        if (!"Geen".equals(tmpString)) {
                            tmpString = ISBN.cleanText(tmpString);
                            if (!tmpString.isEmpty()) {
                                bookData.putString(DBKey.BOOK_ISBN, tmpString);
                            }
                        }
                        break;

                    case "Oplage":
                        processText(td, DBKey.PRINT_RUN, bookData);
                        break;

                    case "Aantal bladzijden":
                        processText(td, DBKey.PAGE_COUNT, bookData);
                        break;

                    case "Afmetingen":
                        if (!"? x ? cm".equals(td.text())) {
                            processText(td, SiteField.SIZE, bookData);
                        }
                        break;

                    case "Taal / dialect":
                        processLanguage(context, td, bookData);
                        break;

                    case "Soort":
                        processType(td, bookData);
                        break;

                    case "Bijzonderheden":
                        processText(td, DBKey.DESCRIPTION, bookData);
                        break;

                    default:
                        break;
                }
            }
        }

        // post-process all found data.

        // It seems the site only lists a single number, although a book can be in several
        // Series.
        if (tmpSeriesNr != null && !tmpSeriesNr.isEmpty()) {
            if (seriesList.size() == 1) {
                final Series series = seriesList.get(0);
                series.setNumber(tmpSeriesNr);
            } else if (seriesList.size() > 1) {
                // tricky.... add it to a single series ? which one ? or to all ? or none ?
                // Whatever we choose, it's probably wrong.
                // We'll arbitrarily go with a single one, the last one.
                final Series series = seriesList.get(seriesList.size() - 1);
                series.setNumber(tmpSeriesNr);
            }
        }

        // We DON'T store a toc with a single entry (i.e. the book title itself).
        parseToc(sections).ifPresent(toc -> {
            bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
            if (TocEntry.hasMultipleAuthors(toc)) {
                bookData.putLong(DBKey.TOC_TYPE__BITMASK, Book.ContentType.Anthology.getId());
            } else {
                bookData.putLong(DBKey.TOC_TYPE__BITMASK, Book.ContentType.Collection.getId());
            }
        });

        // store accumulated ArrayList's *after* we parsed the TOC
        if (!authorList.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        }
        if (!seriesList.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, seriesList);
        }
        if (!publisherList.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        }

        // It's extremely unlikely, but should the language be missing, add dutch.
        if (!bookData.containsKey(DBKey.LANGUAGE)) {
            bookData.putString(DBKey.LANGUAGE, "nld");
        }



        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0] || fetchCovers[1]) {
            final String isbn = bookData.getString(DBKey.BOOK_ISBN);
            parseCovers(document, isbn, fetchCovers, bookData);
        }
    }

    private void parseCovers(@NonNull final Document document,
                             @Nullable final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Bundle bookData)
            throws StorageException {
        final Element images = document.getElementById("images_container");
        if (images != null) {
            final Elements aas = images.select("a");
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                if (isCancelled()) {
                    return;
                }
                if (fetchCovers[cIdx] && aas.size() > cIdx) {
                    final String url = aas.get(cIdx).attr("href");
                    final String fileSpec = saveImage(url, isbn, cIdx, null);
                    if (fileSpec != null) {
                        final ArrayList<String> list = new ArrayList<>();
                        list.add(fileSpec);
                        bookData.putStringArrayList(
                                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[cIdx], list);
                    }
                }
            }
        }
    }

    /**
     * Extract the (optional) table of content from the header.
     * <p>
     * <strong>Note:</strong> should only be called <strong>AFTER</strong> we have processed
     * the authors as we use the first Author of the book for all TOCEntries.
     * <p>
     * This is likely not correct, but the alternative is to store each entry in a TOC
     * as an individual book, and declare a Book TOC as a list of books.
     * i.o.w. the database structure would need to become
     * table: titles (book and toc-entry titles) each entry referencing 1..n authors.
     * table: books, with a primary title, and a list of secondary titles (i.e the toc).
     * (All of which referencing the 'titles' table)
     * <p>
     * This is not practical in the scope of this application.
     *
     * @param sections to parse
     *
     * @return toc list with at least 2 entries
     */
    @NonNull
    private Optional<ArrayList<TocEntry>> parseToc(@NonNull final Collection<Element> sections) {

        // section 0 was the "Catalogusgegevens"; normally section 3 is the one we need here...
        Element tocSection = null;
        for (final Element section : sections) {
            final Element sectionTitle = section.selectFirst("h2.section-title");
            if (sectionTitle != null) {
                if ("Verhalen in dit album".equals(sectionTitle.text())) {
                    tocSection = section;
                    break;
                }
            }
        }


        if (tocSection != null) {
            final ArrayList<TocEntry> toc = new ArrayList<>();
            for (final Element divRows : tocSection.select("div.row-information")) {
                final Element th = divRows.selectFirst("div.label");
                final Element td = divRows.selectFirst("div.value");
                if (th != null && td != null) {
                    if ("Verhaaltitel".equals(th.text())) {
                        toc.add(new TocEntry(authorList.get(0), td.text()));
                    }
                }
            }

            if (toc.size() > 1) {
                return Optional.of(toc);
            }
        }
        return Optional.empty();
    }


    /**
     * Found an Author.
     *
     * @param td                data td
     * @param currentAuthorType of this entry
     */
    private void processAuthor(@NonNull final Element td,
                               @Author.Type final int currentAuthorType) {

        for (final Element a : td.select("a")) {
            final String name = a.text();
            final Author currentAuthor = Author.from(name);
            boolean add = true;
            // check if already present
            for (final Author author : authorList) {
                if (author.equals(currentAuthor)) {
                    // merge types.
                    author.addType(currentAuthorType);
                    add = false;
                    // keep looping
                }
            }

            if (add) {
                currentAuthor.setType(currentAuthorType);
                authorList.add(currentAuthor);
            }
        }
    }

    /**
     * Found a Series.
     *
     * @param td data td
     */
    private void processSeries(@NonNull final Element td) {
        for (final Element a : td.select("a")) {
            final String name = a.text();
            final Series currentSeries = Series.from(name);
            // check if already present
            if (seriesList.stream().anyMatch(series -> series.equals(currentSeries))) {
                return;
            }
            // just add
            seriesList.add(currentSeries);
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td data td
     */
    private void processPublisher(@NonNull final Element td) {
        for (final Element a : td.select("a")) {
            final String name = ParseUtils.cleanText(a.text());
            final Publisher currentPublisher = Publisher.from(name);
            // check if already present
            if (publisherList.stream().anyMatch(pub -> pub.equals(currentPublisher))) {
                return;
            }
            // just add
            publisherList.add(currentPublisher);
        }

    }

    private void processLanguage(@NonNull final Context context,
                                 @NonNull final Element td,
                                 @NonNull final Bundle bookData) {
        processText(td, DBKey.LANGUAGE, bookData);
        String lang = bookData.getString(DBKey.LANGUAGE);
        if (lang != null && !lang.isEmpty()) {
            lang = ServiceLocator.getInstance().getLanguages()
                                 .getISO3FromDisplayName(getLocale(context), lang);
            bookData.putString(DBKey.LANGUAGE, lang);
        }
    }

    private void processType(@NonNull final Element td,
                             @NonNull final Bundle bookData) {
        // there might be more than one; we only grab the first one here
        final Element a = td.child(0);
        bookData.putString(SiteField.TYPE, a.text());
    }

    /**
     * Process a td which is pure text.
     *
     * @param td       label td
     * @param key      for this field
     * @param bookData Bundle to update
     */
    private void processText(@Nullable final Element td,
                             @NonNull final String key,
                             @NonNull final Bundle bookData) {
        if (td != null) {
            final String text = ParseUtils.cleanText(td.text().trim());
            if (!text.isEmpty()) {
                bookData.putString(key, text);
            }
        }
    }

    /**
     * LastDodoField specific field names we add to the bundle based on parsed XML data.
     */
    public static final class SiteField {

        static final String PRINTING = "__printing";
        static final String SIZE = "__size";
        static final String TYPE = "__type";
        static final String REEKS = "__reeks";

        private SiteField() {
        }
    }
}
