/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * The preference key names here are the ones that define USER settings.
 * See {@link com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity} and children.
 * <p>
 * These keys *MUST* be kept in sync with "res/xml/preferences*.xml"
 * <p>
 * Application internal settings are done where they are needed/used.
 */
public final class Prefs {

    /** Global settings - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_search_site_order = "psk_search_site_order";

    /** Style setting - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_groupings = "psk_style_groupings";
    /** Style setting - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_filters = "psk_style_filters";
    /** Style setting - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_show_details = "psk_style_show_details";
    /** Style setting - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_author = "psk_style_author";
    /** Style setting - PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_style_series = "psk_style_series";


    /** PreferenceScreen/PreferenceCategory Key. */
    public static final String psk_barcode_scanner = "psk_barcode_scanner";


    /** Preference Key. */
    public static final String pk_ui_locale = "ui.locale";
    public static final String pk_ui_theme = "ui.theme";
    public static final String pk_ui_messages_use = "ui.messages.use";

    public static final String pk_network_allow_metered = "network.allow.metered";

    public static final String pk_scanner_preferred = "scanner.preferred";

    public static final String pk_search_form_advanced = "search.form.advanced";

    public static final String pk_sounds_scan_isbn_valid = "sounds.scan.isbn.valid";
    public static final String pk_sounds_scan_isbn_invalid = "sounds.scan.isbn.invalid";

    public static final String pk_reformat_titles_sort = "reformat.titles.sort";
    public static final String pk_reformat_titles_display = "reformat.titles.display";
    public static final String pk_reformat_format_update = "reformat.format.update";

    public static final String pk_camera_image_autorotate = "camera.image.autorotate";

    public static final String pk_image_cropper_external = "image.cropper.external";
    public static final String pk_image_cropper_frame_whole = "image.cropper.frame.whole";
    public static final String pk_image_cache_resized = "image.cache.resized";

    public static final String pk_bob_levels_rebuild_state = "style.booklist.levels.rebuild.state";
    public static final String pk_bob_levels_default = "style.booklist.levels.default";

    public static final String pk_bob_use_task_for_extras = "style.booklist.task.extras";

    public static final String pk_compat_booklist_mode = "compat.booklist.mode";
    public static final String pk_compat_image_cropper_layer_type =
            "compat.image.cropper.viewlayertype";


    public static final String pk_bob_style_name = "style.booklist.name";

    public static final String pk_bob_groups = "style.booklist.groups";
    public static final String pk_bob_preferred_style = "style.booklist.preferred";
    public static final String pk_bob_text_size = "style.booklist.scale.text";
    public static final String pk_bob_cover_size = "style.booklist.scale.thumbnails";

    public static final String pk_bob_books_under_multiple_series =
            "style.booklist.group.series.show.all";
    public static final String pk_bob_books_under_multiple_authors =
            "style.booklist.group.authors.show.all";
    public static final String pk_bob_format_author_name =
            "style.booklist.group.authors.show.first_last";

    public static final String pk_bob_sort_author_name =
            "style.booklist.sort.author.given_first";

    /** MultiSelectListPreference. */
    public static final String pk_bob_header = "style.booklist.show.header";
    /** Show the cover image for each book. */
    public static final String pk_bob_show_thumbnails = "style.booklist.show.thumbnails";
    /** Show list of bookshelves for each book. */
    public static final String pk_bob_show_bookshelves = "style.booklist.show.bookshelves";
    /** Show location for each book. */
    public static final String pk_bob_show_location = "style.booklist.show.location";
    /** Show author for each book. */
    public static final String pk_bob_show_author = "style.booklist.show.author";
    /** Show publisher for each book. */
    public static final String pk_bob_show_publisher = "style.booklist.show.publisher";
    /** Show publication date for each book. */
    public static final String pk_bob_show_pub_date = "style.booklist.show.publication.date";
    /** Show ISBN for each book. */
    public static final String pk_bob_show_isbn = "style.booklist.show.isbn";
    /** Show format for each book. */
    public static final String pk_bob_show_format = "style.booklist.show.format";

    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_read = "style.booklist.filter.read";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_signed = "style.booklist.filter.signed";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_loaned = "style.booklist.filter.loaned";
    /** Booklist Filter - ListPreference. */
    public static final String pk_bob_filter_anthology = "style.booklist.filter.anthology";
    /** Booklist Filter - MultiSelectListPreference. */
    public static final String pk_bob_filter_editions = "style.booklist.filter.editions";

    private Prefs() {
    }

    /**
     * Convert a set where each element represents one bit to an int bitmask.
     *
     * @param set the set
     *
     * @return the value
     */
    @NonNull
    public static Integer toInteger(@NonNull final Set<String> set) {
        int tmp = 0;
        for (String s : set) {
            tmp += Integer.parseInt(s);
        }
        return tmp;
    }

    /**
     * Convert an int (bitmask) to a set where each element represents one bit.
     *
     * @param bitmask the value
     *
     * @return the set
     */
    @NonNull
    public static Set<String> toStringSet(@IntRange(from = 0, to = 0xFFFF)
                                          @NonNull final Integer bitmask) {
        // sanity check.
        if (bitmask < 0) {
            throw new IllegalArgumentException("bitmask=" + bitmask);
        }

        Set<String> set = new HashSet<>();
        int tmp = bitmask;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                set.add(String.valueOf(bit));
            }
            bit *= 2;
            tmp = tmp >> 1;
        }
        return set;
    }

    /**
     * Copy all preferences from source to destination.
     */
    @SuppressWarnings("unused")
    protected static void copyPrefs(@NonNull final Context context,
                                    @NonNull final String source,
                                    @NonNull final String destination,
                                    final boolean clearSource) {
        SharedPreferences sourcePrefs =
                context.getSharedPreferences(source, Context.MODE_PRIVATE);
        SharedPreferences destinationPrefs =
                context.getSharedPreferences(destination, Context.MODE_PRIVATE);

        SharedPreferences.Editor ed = destinationPrefs.edit();
        Map<String, ?> all = sourcePrefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {

            if (entry.getValue() instanceof Boolean) {
                ed.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                ed.putFloat(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                ed.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                ed.putLong(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                ed.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Set) {
                //noinspection unchecked
                ed.putStringSet(entry.getKey(), (Set<String>) entry.getValue());
            } else {
                Logger.warnWithStackTrace(context, Prefs.class,
                                          entry.getValue().getClass().getCanonicalName());
            }
        }
        ed.apply();
        if (clearSource) {
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(source);
            } else {
                sourcePrefs.edit().clear().apply();
            }
        }
    }

    /**
     * DEBUG method.
     */
    public static void dumpPreferences(@NonNull final Context context,
                                       @Nullable final String uuid) {
        if (BuildConfig.DEBUG /* always */) {
            Map<String, ?> map;
            if (uuid != null) {
                map = context.getSharedPreferences(uuid, Context.MODE_PRIVATE).getAll();
            } else {
                map = PreferenceManager.getDefaultSharedPreferences(context).getAll();
            }
            List<String> keyList = new ArrayList<>(map.keySet());
            String[] keys = keyList.toArray(new String[]{});
            Arrays.sort(keys);

            StringBuilder sb = new StringBuilder("\n\nSharedPreferences: "
                                                 + (uuid != null ? uuid : "global"));
            for (String key : keys) {
                Object value = map.get(key);
                sb.append('\n').append(key).append('=').append(value);
            }
            sb.append("\n\n");

            Logger.debug(App.class, "dumpPreferences", sb);
        }
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want "given-names last-name" formatted authors.
     */
    public static boolean displayAuthorGivenNameFirst(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_bob_format_author_name, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want "given-names last-name" sorted authors.
     */
    public static boolean sortAuthorGivenNameFirst(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_bob_sort_author_name, false);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if titles should be reordered. e.g. "The title" -> "title, The"
     */
    public static boolean reorderTitleForDisplaying(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_reformat_titles_display, true);
    }
}
