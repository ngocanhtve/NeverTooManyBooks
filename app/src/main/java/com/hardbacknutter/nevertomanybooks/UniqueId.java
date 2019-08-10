/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks;

import com.hardbacknutter.nevertomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.settings.PreferredStylesActivity;
import com.hardbacknutter.nevertomanybooks.settings.StyleSettingsFragment;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * Global String constants.
 */
@SuppressWarnings("WeakerAccess")
public final class UniqueId {

    /** Request code: navigation panel. */
    public static final int REQ_NAV_PANEL_EDIT_BOOKSHELVES = 1_001;
    /** Request code: navigation panel. */
    public static final int REQ_NAV_PANEL_EDIT_STYLES = 1_002;
    /** Request code: navigation panel. */
    public static final int REQ_NAV_PANEL_ADMIN = 1_003;
    /** Request code: navigation panel. */
    public static final int REQ_NAV_PANEL_SETTINGS = 1_004;

    /** Request code: edit a Style. */
    public static final int REQ_EDIT_STYLE = 1_100;
    /** Request code: edit the Groups of a Style. */
    public static final int REQ_EDIT_STYLE_GROUPS = 1_101;

    /** request code: open the book view screen. */
    public static final int REQ_BOOK_VIEW = 2_001;
    /** Request code: open the book edit screen. */
    public static final int REQ_BOOK_EDIT = 2_002;
    /** Request code: open the book edit screen with a new, duplicate, book. */
    public static final int REQ_BOOK_DUPLICATE = 2_003;
    /** Request code: open the book 'update-from-internet' screen. */
    public static final int REQ_UPDATE_FIELDS_FROM_INTERNET = 2_004;

    /** Request code: open the book internet-search screen. */
    public static final int REQ_BOOK_SEARCH = 2_101;
    /** Request code: open the advanced (FTS) local search screen. */
    public static final int REQ_ADVANCED_LOCAL_SEARCH = 2_102;
    /** Request code: open the scanner. */
    public static final int REQ_IMAGE_FROM_SCANNER = 2_103;

    /** Request code: open the admin page to order and en/disable sites. */
    public static final int REQ_PREFERRED_SEARCH_SITES = 2_201;

    /** Request code: ask the CoverBrowserFragment to get an alternative edition cover. */
    public static final int REQ_ALT_EDITION = 3_001;
    /** Request code: use internal routines for cropping images. */
    public static final int REQ_CROP_IMAGE_INTERNAL = 3_002;
    /** Request code: start an intent for an external application to do the cropping. */
    public static final int REQ_CROP_IMAGE_EXTERNAL = 3_003;
    /** Request code: start an intent to get an image from the Camera. */
    public static final int REQ_ACTION_IMAGE_CAPTURE = 3_004;
    /** Request code: start an intent to get an image from the an app that provides content. */
    public static final int REQ_ACTION_GET_CONTENT = 3_005;

    /** Request code: open the author "all works" screen. */
    public static final int REQ_AUTHOR_WORKS = 4_001;

    /** Request code: system request to ask the user for permissions. */
    public static final int REQ_ANDROID_PERMISSIONS = 9_000;

    /** The activity changed something that warrants a recreation of the caller to be needed. */
    public static final int ACTIVITY_RESULT_RECREATE_NEEDED = 10_000;

    /** The activity deleted something. */
    public static final int ACTIVITY_RESULT_DELETED_SOMETHING = 10_001;

    /** {@link PreferredStylesActivity}. The preferred styles were modified somehow. */
    public static final int ACTIVITY_RESULT_MODIFIED_BOOKLIST_PREFERRED_STYLES = 10_101;

    /** {@link StyleSettingsFragment}. A style was modified. */
    public static final int ACTIVITY_RESULT_MODIFIED_BOOKLIST_STYLE = 10_102;

    /**
     * Bundle keys for {@code ParcelableArrayList<Entity>}.
     */
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";
    public static final String BKEY_TOC_ENTRY_ARRAY = "toc_titles_array";
    public static final String BKEY_BOOKSHELF_ARRAY = "bookshelf_array";

    /** generic search text. */
    public static final String BKEY_SEARCH_TEXT = "searchText";
    /** author search text. */
    public static final String BKEY_SEARCH_AUTHOR = "searchAuthor";
    /** title search text. */
    public static final String BKEY_SEARCH_TITLE = "searchTitle";
    /** isbn search text. */
    public static final String BKEY_SEARCH_ISBN = "searchIsbn";

    /**
     * Search site flags as in {@link com.hardbacknutter.nevertomanybooks.searches.SearchSites}.
     * <p>
     * <br>type: {@code int} (bitmask)
     */
    public static final String BKEY_SEARCH_SITES = "searchFlags";

    /**
     * Single fileSpecs or uri.
     * <p>
     * <br>type: {@code String}
     */
    public static final String BKEY_FILE_SPEC = "fileSpec";

    /**
     * List of fileSpecs or uri.
     * <p>
     * <br>type: {@code ArrayList<String>}
     */
    public static final String BKEY_FILE_SPEC_ARRAY = "fileSpec_array";

    /**
     * The title to be used by generic Dialogs.
     * <p>
     * <br>type: {@code int} (stringId)
     */
    public static final String BKEY_DIALOG_TITLE = "dialogTitle";

    /**
     * Identifier of the field we want the generic Dialog to handle.
     * <p>
     * <br>type: {@code int} (resource id)
     */
    public static final String BKEY_FIELD_ID = "fieldId";

    /**
     * Bundle key to pass a Bundle with book data around.
     * i.e. before the data becomes an actual {@link Book}.
     * <p>
     * <br>type: {@code Bundle}
     */
    public static final String BKEY_BOOK_DATA = "bookData";

    /**
     * Bundle key to pass a generic {@code ArrayList<Long>} around.
     * <p>
     * <br>type: {@code Serializable}
     */
    public static final String BKEY_ID_LIST = "idList";

    /**
     * 3 uses:
     * <ul>
     * <li>Indicate if we 'have' a thumbnail (in which case {@link StorageUtils#getTempCoverFile()}
     * will point to that image.</li>
     * <li>Flag to indicate we 'want' a thumbnail when downloading book information
     * from search sites.</li>
     * <li>User Visibility flag</li>
     * </ul>
     * <br>type: {@code boolean}
     */
    public static final String BKEY_IMAGE = "thumbnail";

    /**
     * A BooklistStyle.
     * <p>
     * <br>type: {@link com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle}
     */
    public static final String BKEY_STYLE = "style";

    /**
     * tag of fragment to display if an Activity supports multiple.
     * <p>
     * <br>type: {@code String}
     */
    public static final String BKEY_FRAGMENT_TAG = "fragment";

    /**
     * The resulting {@link ImportOptions#what} flags after an import.
     * <p>
     * <br>type: {@code int} (bitmask)
     * setResult
     */
    public static final String BKEY_IMPORT_RESULT = "importResult";

    /**
     * The resulting {@link ExportOptions#what} flags after an export.
     * <p>
     * <br>type: {@code int} (bitmask)
     * setResult
     */
    public static final String BKEY_EXPORT_RESULT = "exportResult";

    /**
     * {@link ExportOptions} or {@link ImportOptions}.
     * <p>
     * <br>type: {@code Parcel}
     */
    public static final String BKEY_IMPORT_EXPORT_OPTIONS = "importExportSettings";

    /**
     * Indicate the called activity made global changes.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_GLOBAL_CHANGES_MADE = "globalChanges";

    /**
     * Return the status of a startActivityForResult when a task was 'isCancelled'.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_CANCELED = "cancelled";

    private UniqueId() {
    }
}
