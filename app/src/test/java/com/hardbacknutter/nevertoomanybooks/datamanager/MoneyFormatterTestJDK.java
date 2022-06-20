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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the variations of currency location (before/after), currency symbol/code,
 * decimal separator and thousands separator.
 */
class MoneyFormatterTestJDK
        extends Base {

    @Test
    void formatUS() {
        setLocale(Locale.US);
        //noinspection ConstantConditions
        final FieldFormatter<Money> f = new MoneyFormatter(locale0);
        assertEquals("$1,234.50", f.format(context, new Money(1234.50d, Money.USD)));
        assertEquals("£1,234.50", f.format(context, new Money(1234.50d, Money.GBP)));
        assertEquals("€1,234.50", f.format(context, new Money(1234.50d, Money.EUR)));
    }

    @Test
    void formatUK() {
        setLocale(Locale.UK);
        //noinspection ConstantConditions
        final FieldFormatter<Money> f = new MoneyFormatter(locale0);
        assertEquals("US$1,234.50", f.format(context, new Money(1234.50d, Money.USD)));
        assertEquals("£1,234.50", f.format(context, new Money(1234.50d, Money.GBP)));
        assertEquals("€1,234.50", f.format(context, new Money(1234.50d, Money.EUR)));
    }

    @Test
    void formatGERMANY() {
        setLocale(Locale.GERMANY);
        //noinspection ConstantConditions
        final FieldFormatter<Money> f = new MoneyFormatter(locale0);
        assertEquals("1.234,50 $", f.format(context, new Money(1234.50d, Money.USD)));
        assertEquals("1.234,50 £", f.format(context, new Money(1234.50d, Money.GBP)));
        assertEquals("1.234,50 €", f.format(context, new Money(1234.50d, Money.EUR)));
    }
}
