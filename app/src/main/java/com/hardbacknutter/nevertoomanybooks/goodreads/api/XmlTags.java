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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

/**
 * Tag names for the Goodreads responses.
 */
public final class XmlTags {

    public static final String XML_GOODREADS_RESPONSE = "GoodreadsResponse";

    public static final String XML_ID = "id";
    public static final String XML_NAME = "name";

    public static final String XML_WORK = "work";
    static final String XML_BODY = "body";

    public static final String XML_SEARCH = "search";
    public static final String XML_RESULTS = "results";

    /** Used in paging. */
    static final String XML_START = "start";
    static final String XML_END = "end";
    static final String XML_TOTAL = "total";

    static final String XML_REVIEWS = "reviews";
    static final String XML_REVIEW = "review";
    static final String XML_MY_REVIEW = "my_review";
    static final String XML_REVIEW_ID = "review-id";

    static final String XML_SHELVES = "shelves";
    static final String XML_SHELF = "shelf";
    static final String XML_USER_SHELF = "user_shelf";
    static final String XML_POPULAR_SHELVES = "popular_shelves";
    static final String XML_EXCLUSIVE_FLAG = "exclusive_flag";

    public static final String XML_TITLE = "title";
    static final String XML_TITLE_WITHOUT_SERIES = "title_without_series";
    static final String XML_ORIGINAL_TITLE = "original_title";

    static final String XML_ISBN_13 = "isbn13";
    static final String XML_ISBN = "isbn";
    static final String XML_ASIN = "asin";

    static final String XML_AUTHORS = "authors";
    public static final String XML_AUTHOR = "author";
    public static final String XML_ROLE = "role";

    static final String XML_SERIES = "series";
    static final String XML_SERIES_WORK = "series_work";
    static final String XML_SERIES_WORKS = "series_works";

    static final String XML_PUBLISHER = "publisher";
    static final String XML_PUBLICATION_YEAR = "publication_year";
    static final String XML_PUBLICATION_MONTH = "publication_month";
    static final String XML_PUBLICATION_DAY = "publication_day";
    public static final String XML_ORIGINAL_PUBLICATION_YEAR = "original_publication_year";
    public static final String XML_ORIGINAL_PUBLICATION_MONTH = "original_publication_month";
    public static final String XML_ORIGINAL_PUBLICATION_DAY = "original_publication_day";

    static final String XML_BOOK = "book";
    public static final String XML_BEST_BOOK = "best_book";
    static final String XML_NUM_PAGES = "num_pages";
    static final String XML_DESCRIPTION = "description";
    static final String XML_COUNTRY_CODE = "country_code";
    static final String XML_DATE_ADDED = "date_added";
    static final String XML_DATE_UPDATED = "date_updated";
    static final String XML_RATING = "rating";
    static final String XML_AVERAGE_RATING = "average_rating";
    static final String XML_USER_POSITION = "user_position";
    public static final String XML_SMALL_IMAGE_URL = "small_image_url";
    public static final String XML_IMAGE_URL = "image_url";
    static final String XML_STARTED_AT = "started_at";
    static final String XML_READ_AT = "read_at";

    /**
     * Language: we've seen two formats.
     *
     * <language_code>en-GB</language_code>
     * <language_code>eng</language_code>
     */
    static final String XML_LANGUAGE = "language_code";

    /**
     * Book formats; not a complete list.
     *
     * <format><![CDATA[Mass Market Paperback]]></format>
     * <format><![CDATA[ Hardcover ]]></format>
     * <format><![CDATA[ Paperback ]]></format>
     * <format><![CDATA[ Audio CD ]]></format>
     */
    static final String XML_FORMAT = "format";
    /** <is_ebook>false</is_ebook>. */
    static final String XML_IS_EBOOK = "is_ebook";

    /**
     * Media types; not a complete list.
     *
     * <media_type>book</media_type>
     */
    static final String XML_MEDIA_TYPE = "media_type";


    private XmlTags() {
    }
}
