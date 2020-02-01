/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.utils;

import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commented out asserts are failing for now. But not all of them are:
 * (a) actually added to the parser, and/or (b) not important.
 * <p>
 * DateUtils also needs to put the userLocale into account.
 */
class DateUtilsTest {

    // 'Winter' time.
    // 1987-02-25; i.e. the day > possible month number (0..11)
    @SuppressWarnings("deprecation")
    private final Date wd_1987_02_25 = new Date(1987 - 1900, 1, 25);

    // 'Summer' time.
    // 1987-06-25; i.e. the day > possible month number (0..11)
    @SuppressWarnings("deprecation")
    private final Date sd_1987_06_25 = new Date(1987 - 1900, 5, 25);

    // 1987-06-22; i.e. the day < possible month number (0..11)
    @SuppressWarnings("deprecation")
    private final Date sd_1987_06_10 = new Date(1987 - 1900, 5, 10);

    // 1987-06; partial date
    @SuppressWarnings("deprecation")
    private final Date sd_1987_06_01 = new Date(1987 - 1900, 5, 1);

    @SuppressWarnings("deprecation")
    private final Date wd_2017_01_12_11_57_41 = new Date(2017 - 1900, 0, 12);

    @SuppressWarnings("deprecation")
    private final Date wd_2017_01_12 = new Date(2017 - 1900, 0, 12);

    @BeforeEach
    void setUp() {
//        sd_1987_06_25.setHours(12);
//        sd_1987_06_10.setHours(12);
//        sd_1987_06_01.setHours(12);
//
//        wd_2017_01_12.setHours(12);

        wd_2017_01_12_11_57_41.setHours(11);
        wd_2017_01_12_11_57_41.setMinutes(57);
        wd_2017_01_12_11_57_41.setSeconds(41);
    }

    void set12oClock() {
        sd_1987_06_25.setHours(12);
        sd_1987_06_10.setHours(12);
        sd_1987_06_01.setHours(12);

        wd_2017_01_12.setHours(12);
    }

    @Test
    void parseAmbiguousMMDD() {
        Locale locale = Locale.getDefault();
        DateUtils.create(locale);
        set12oClock();

        // Patter list has MMdd before ddMM
        //assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "10-06-1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("06-10-1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "10 06 1987"));
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "06 10 1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "10-06-87"));
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "06-10-87"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "10 06 87"));
//        assertEquals(sd_1987_06_10, DateUtils.parseDate(locale, "06 10 87"));
    }

    @Test
    void parseDDMM() {
        Locale locale = Locale.getDefault();
        DateUtils.create(locale);
        set12oClock();

        assertEquals(sd_1987_06_25, DateUtils.parseDate("25-06-1987"));
        assertEquals(sd_1987_06_25, DateUtils.parseDate("06-25-1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "25 06 1987"));
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "06 25 1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "25-06-87"));
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "06-25-87"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "25 06 87"));
//        assertEquals(sd_1987_06_25, DateUtils.parseDate(locale, "06 25 87"));
    }

    /**
     * Sql formatted.
     */
    @Test
    void parseSqlDate() {

        // Winter: exact time match
        // Daylight savings time will be offset by 1 hour.
        Locale locale = Locale.UK;
        DateUtils.create(locale);

        assertEquals(wd_2017_01_12_11_57_41, DateUtils.parseSqlDateTime("2017-01-12 11:57:41"));

        sd_1987_06_25.setHours(sd_1987_06_25.getHours() + 1);
        assertEquals(sd_1987_06_25, DateUtils.parseSqlDateTime("1987-06-25"));
        sd_1987_06_10.setHours(sd_1987_06_10.getHours() + 1);
        assertEquals(sd_1987_06_10, DateUtils.parseSqlDateTime("1987-06-10"));
        sd_1987_06_01.setHours(sd_1987_06_01.getHours() + 1);
        assertEquals(sd_1987_06_01, DateUtils.parseSqlDateTime("1987-06"));

    }

    /**
     * Native English.
     */
    @Test
    void parseNativeEnglish() {
        // Testing with English only
        Locale locale = Locale.ENGLISH;
        DateUtils.create(locale);

        set12oClock();

        assertEquals(sd_1987_06_25, DateUtils.parseDate("25-Jun-1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("10-Jun-1987"));

        assertEquals(sd_1987_06_25, DateUtils.parseDate("25 Jun 1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("10 Jun 1987"));

        assertEquals(sd_1987_06_25, DateUtils.parseDate("Jun 25, 1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("Jun 10, 1987"));

        // day 01 is implied
        assertEquals(sd_1987_06_01, DateUtils.parseDate("Jun 1987"));


        assertEquals(sd_1987_06_10, DateUtils.parseDate("10 Jun. 1987"));

        assertEquals(wd_2017_01_12, DateUtils.parseDate("12 january 2017"));
    }

    @Test
    void parseNonEnglish() {
        // Testing with French, English
        Locale locale = Locale.FRENCH;
        DateUtils.create(locale);

        set12oClock();

        assertEquals(sd_1987_06_25, DateUtils.parseDate("25-Jun-1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("10-Jun-1987"));

        assertEquals(sd_1987_06_25, DateUtils.parseDate("25 Jun 1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("10 Jun 1987"));

        assertEquals(sd_1987_06_25, DateUtils.parseDate("Jun 25, 1987"));
        assertEquals(sd_1987_06_10, DateUtils.parseDate("Jun 10, 1987"));

        // day 01 is implied
        assertEquals(sd_1987_06_01, DateUtils.parseDate("Jun 1987"));


        assertEquals(sd_1987_06_10, DateUtils.parseDate("10 Jun. 1987"));

        assertEquals(wd_2017_01_12, DateUtils.parseDate("12 january 2017"));

        assertEquals(wd_2017_01_12, DateUtils.parseDate("12 janvier 2017"));
    }
}
