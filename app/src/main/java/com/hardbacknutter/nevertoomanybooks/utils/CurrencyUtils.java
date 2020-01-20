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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;

public final class CurrencyUtils {

    /**
     * Prices are split into currency and actual amount.
     * Split on first digit, but leave it in the second part.
     */
    private static final Pattern SPLIT_PRICE_CURRENCY_AMOUNT_PATTERN = Pattern.compile("(?=\\d)");

    private static final Pattern SHILLING_PENCE_PATTERN = Pattern.compile("(\\d*|-?)/(\\d*|-?)");

    /**
     * A Map to translate currency symbols to their official ISO code.
     * <p>
     * ENHANCE: surely this can be done more intelligently ?
     */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();

    private CurrencyUtils() {
    }

    /**
     * Populate CURRENCY_MAP.
     * The key in the map <strong>MUST be lowercase</strong>.
     * The value in the map <strong>MUST be uppercase</strong>.
     *
     * <a href="https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language>English</a>
     */
    private static void createCurrencyMap() {
        // allow re-creating
        CURRENCY_MAP.clear();

        CURRENCY_MAP.put("", "");
        CURRENCY_MAP.put("€", "EUR");

        // English
        CURRENCY_MAP.put("a$", "AUD"); // Australian Dollar
        CURRENCY_MAP.put("nz$", "NZD"); // New Zealand Dollar
        CURRENCY_MAP.put("£", "GBP"); // British Pound
        CURRENCY_MAP.put("$", "USD"); // Trump Disney's

        CURRENCY_MAP.put("c$", "CAD"); // Canadian Dollar
        CURRENCY_MAP.put("ir£", "IEP"); // Irish Punt
        CURRENCY_MAP.put("s$", "SGD"); // Singapore dollar

        // supported locales (including pre-euro)
        CURRENCY_MAP.put("br", "RUB"); // Russian Rouble
        CURRENCY_MAP.put("zł", "PLN"); // Polish Zloty
        CURRENCY_MAP.put("kč", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("kc", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("dm", "DEM"); //german marks
        CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
        CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("f", "FRF"); // French Franc
        CURRENCY_MAP.put("ff", "FRF"); // French Franc
        CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
        CURRENCY_MAP.put("L", "ITL"); // Italian Lira
        CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
        CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

        // some others as seen on ISFDB site
        CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
        CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
        CURRENCY_MAP.put("Ft", "HUF"); // Hungarian Forint
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit, into the ISO code
     * for that currency.
     *
     * @param currency to convert
     *
     * @return ISO code.
     */
    @Nullable
    private static String currencyToISO(@NonNull final String currency) {
        if (CURRENCY_MAP.isEmpty()) {
            createCurrencyMap();
        }
        String key = currency.trim().toLowerCase(App.getSystemLocale());
        return CURRENCY_MAP.get(key);
    }

    /**
     * Takes a combined price field, and returns the value/currency in the Bundle.
     *
     * <strong>Note:</strong>
     * The UK (GBP), pre-decimal 1971, had Shilling/Pence as subdivisions of the pound.
     * UK Shilling was written as "1/-", for example:
     * three shillings and six pence => 3/6
     * It's used on the ISFDB web site. We convert it to modern GBP.
     * <a href="https://en.wikipedia.org/wiki/Decimal_Day">Decimal_Day</a>
     *
     * @param locale            Locale to use for parsing the price string
     * @param priceWithCurrency price to decode, e.g. "Bf459", "$9.99", "EUR 66", ...
     *                          The currency part <strong>must</strong> be a prefix.
     *                          We do not support it as a suffix.
     * @param keyOutputPrice    bundle key for the value
     * @param keyOutputCurrency bundle key for the currency
     * @param result            bundle to add the two keys to.
     */
    public static void splitPrice(@NonNull final Locale locale,
                                  @NonNull final CharSequence priceWithCurrency,
                                  @NonNull final String keyOutputPrice,
                                  @NonNull final String keyOutputCurrency,
                                  @NonNull final Bundle result) {
        String[] data = SPLIT_PRICE_CURRENCY_AMOUNT_PATTERN.split(priceWithCurrency, 2);
        if (data.length > 1) {
            String currencyCode = data[0].trim().toUpperCase(locale);
            // if we don't have a normalized ISO3 code, see if we can convert it to one.
            if (currencyCode.length() != 3) {
                currencyCode = currencyToISO(data[0]);
            }
            // if we do have an ISO3 code, split the data as required.
            if (currencyCode != null && currencyCode.length() == 3) {
                try {
                    double amount = ParseUtils.parseDouble(data[1], locale);
                    result.putDouble(keyOutputPrice, amount);
                    // re-get the code just in case it used a recognised but non-standard string
                    java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
                    result.putString(keyOutputCurrency, currency.getCurrencyCode());
                    return;

                } catch (@NonNull final IllegalArgumentException ignore) {
                }
            }
        }

        // let's see if this was UK shillings/pence
        Matcher m = SHILLING_PENCE_PATTERN.matcher(priceWithCurrency);
        if (m.find()) {
            try {
                int shillings = 0;
                int pence = 0;
                String tmp;

                tmp = m.group(1);
                if (tmp != null && !tmp.isEmpty() && !"-".equals(tmp)) {
                    shillings = Integer.parseInt(tmp);
                }
                tmp = m.group(2);
                if (tmp != null && !tmp.isEmpty() && !"-".equals(tmp)) {
                    pence = Integer.parseInt(tmp);
                }

                // the British pound was made up of 20 shillings, each of which was
                // made up of 12 pence, a total of 240 pence.
                double pounds = ((shillings * 12) + pence) / 240;
                result.putDouble(keyOutputPrice, pounds);
                result.putString(keyOutputCurrency, "GBP");
                //noinspection UnnecessaryReturnStatement
                return;

            } catch (@NonNull final NumberFormatException ignore) {
            }
        }
    }
}
