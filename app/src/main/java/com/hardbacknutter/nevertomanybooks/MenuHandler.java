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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Book;
import com.hardbacknutter.nevertomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.searches.isfdb.IsfdbManager;
import com.hardbacknutter.nevertomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertomanybooks.searches.openlibrary.OpenLibraryManager;

/**
 * Handles re-usable menu items; both to create and to handle.
 * <p>
 * Defines some menu 'order' variables, to ensure certain menu's have a fixed spot.
 */
public final class MenuHandler {

    public static final int ORDER_HIDE_KEYBOARD = 1;
    static final int ORDER_SAVE = 5;

    static final int ORDER_SEARCH_SITES = 20;

    static final int ORDER_UPDATE_FIELDS = 70;
    static final int ORDER_LENDING = 80;

    static final int ORDER_SHARE = 90;
    static final int ORDER_SEND_TO_GOODREADS = 92;
    static final int ORDER_VIEW_BOOK_AT_SITE = 97;
    private static final int ORDER_AMAZON = 99;

    private MenuHandler() {
    }

    /**
     * Add SubMenu for book creation.
     * <p>
     * Group: R.id.SUBMENU_BOOK_ADD
     *
     * @param menu Root menu
     */
    static void addCreateBookSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_BOOK_ADD, R.id.SUBMENU_BOOK_ADD,
                                          0, R.string.menu_add_book)
                              .setIcon(R.drawable.ic_add);

        subMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SCAN, 0,
                    R.string.menu_add_book_by_barcode_scan)
               .setIcon(R.drawable.ic_add_a_photo);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN, 0,
                    R.string.menu_add_book_by_isbn)
               .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT, 0,
                    R.string.menu_search_internet)
               .setIcon(R.drawable.ic_zoom_in);
        subMenu.add(R.id.SUBMENU_BOOK_ADD, R.id.MENU_BOOK_ADD_MANUALLY, 0,
                    R.string.menu_add_book_manually)
               .setIcon(R.drawable.ic_keyboard);
    }

    /**
     * Handle the menu items created by {@link #addCreateBookSubMenu(Menu)}.
     *
     * @param activity Calling activity
     * @param menuItem The item selected
     *
     * @return {@code true} if handled
     */
    static boolean handleBookSubMenu(@NonNull final Activity activity,
                                     @NonNull final MenuItem menuItem) {
        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_ADD_BY_SCAN:
                intent = new Intent(activity, BookSearchActivity.class)
                                 .putExtra(UniqueId.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                                 .putExtra(BookSearchByIsbnFragment.BKEY_IS_SCAN_MODE, true);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_BY_SEARCH_ISBN:
                intent = new Intent(activity, BookSearchActivity.class)
                                 .putExtra(UniqueId.BKEY_FRAGMENT_TAG,
                                           BookSearchByIsbnFragment.TAG);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_BY_SEARCH_TEXT:
                intent = new Intent(activity, BookSearchActivity.class)
                                 .putExtra(UniqueId.BKEY_FRAGMENT_TAG,
                                           BookSearchByTextFragment.TAG);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_SEARCH);
                return true;

            case R.id.MENU_BOOK_ADD_MANUALLY:
                intent = new Intent(activity, EditBookActivity.class);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;

            default:
                return false;
        }
    }

    static void addViewBookSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_VIEW_BOOK_AT_SITE,
                                          R.id.SUBMENU_VIEW_BOOK_AT_SITE,
                                          ORDER_VIEW_BOOK_AT_SITE,
                                          R.string.menu_view_book_at_ellipsis)
                              .setIcon(R.drawable.ic_link);
        subMenu.add(R.id.MENU_VIEW_BOOK_AT_GOODREADS,
                    R.id.MENU_VIEW_BOOK_AT_GOODREADS, 0,
                    R.string.goodreads);
        subMenu.add(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING,
                    R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING, 0,
                    R.string.library_thing);
        subMenu.add(R.id.MENU_VIEW_BOOK_AT_ISFDB,
                    R.id.MENU_VIEW_BOOK_AT_ISFDB, 0,
                    R.string.isfdb);
        subMenu.add(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY,
                    R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY, 0,
                    R.string.open_library);
    }

    static void prepareViewBookSubMenu(@NonNull final Menu menu,
                                       @NonNull final Book book) {
        menu.setGroupVisible(
                R.id.SUBMENU_VIEW_BOOK_AT_SITE,
                0 != book.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)
                || 0 != book.getLong(DBDefinitions.KEY_LIBRARY_THING_ID)
                || 0 != book.getLong(DBDefinitions.KEY_ISFDB_ID)
                || !book.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID).isEmpty());
    }

    static boolean handleViewBookSubMenu(@NonNull final Context context,
                                         @NonNull final MenuItem menuItem,
                                         @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_VIEW_BOOK_AT_SITE:
                // after the user selects the submenu, we make individual items visible/hidden.
                SubMenu menu = menuItem.getSubMenu();

                menu.setGroupVisible(R.id.MENU_VIEW_BOOK_AT_GOODREADS,
                                     0 != book.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID));
                menu.setGroupVisible(R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING,
                                     0 != book.getLong(DBDefinitions.KEY_LIBRARY_THING_ID));
                menu.setGroupVisible(R.id.MENU_VIEW_BOOK_AT_ISFDB,
                                     0 != book.getLong(DBDefinitions.KEY_ISFDB_ID));
                menu.setGroupVisible(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY,
                                     !book.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID).isEmpty());
                // let the normal call flow go on, it will display the submenu
                return false;

            case R.id.MENU_VIEW_BOOK_AT_ISFDB:
                IsfdbManager.openWebsite(context, book.getLong(DBDefinitions.KEY_ISFDB_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_GOODREADS:
                GoodreadsManager.openWebsite(context,
                                             book.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_LIBRARY_THING:
                LibraryThingManager.openWebsite(context,
                                                book.getLong(DBDefinitions.KEY_LIBRARY_THING_ID));
                return true;

            case R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY:
                OpenLibraryManager.openWebsite(context,
                                               book.getString(DBDefinitions.KEY_OPEN_LIBRARY_ID));
                return true;

            default:
                return false;
        }
    }

    /**
     * Add SubMenu for Amazon searches.
     * <p>
     * Normally called from your {@link Fragment#onCreateOptionsMenu}.
     *
     * @param menu Root menu
     *
     * @return the submenu for further manipulation if needed.
     */
    static SubMenu addAmazonSearchSubMenu(@NonNull final Menu menu) {
        SubMenu subMenu = menu.addSubMenu(R.id.SUBMENU_AMAZON_SEARCH, R.id.SUBMENU_AMAZON_SEARCH,
                                          ORDER_AMAZON, R.string.amazon_ellipsis)
                              .setIcon(R.drawable.ic_search);

        // we use the group to make the entry visible/invisible, hence it's == the actual id.
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR,
                    R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0,
                    R.string.menu_amazon_books_by_author);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                    R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0,
                    R.string.menu_amazon_books_by_author_in_series);
        subMenu.add(R.id.MENU_AMAZON_BOOKS_IN_SERIES,
                    R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0,
                    R.string.menu_amazon_books_in_series);

        return subMenu;
    }

    /**
     * Set visibility of the Amazon sub menu itself.
     * <p>
     * After the user selects the submenu, individual menu items are made visible/hidden in:
     * {@link #handleAmazonSearchSubMenu}.
     * <p>
     * Normally called from your {@link Fragment#onPrepareOptionsMenu(Menu)}.
     *
     * @param menu Root menu
     * @param book the current book
     */
    static void prepareAmazonSearchSubMenu(@NonNull final Menu menu,
                                           @NonNull final Book book) {
        boolean hasAuthor = !book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
        boolean hasSeries = !book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY).isEmpty();
        menu.setGroupVisible(R.id.SUBMENU_AMAZON_SEARCH, hasAuthor || hasSeries);
    }

    /**
     * Handle the menu items created by {@link #addAmazonSearchSubMenu(Menu)}.
     *
     * @param context  Current context
     * @param menuItem The item selected
     * @param book     the book upon to act
     *
     * @return {@code true} if handled
     */
    static boolean handleAmazonSearchSubMenu(@NonNull final Context context,
                                             @NonNull final MenuItem menuItem,
                                             @NonNull final Book book) {
        switch (menuItem.getItemId()) {
            case R.id.SUBMENU_AMAZON_SEARCH:
                // after the user selects the submenu, we make individual items visible/hidden.
                SubMenu menu = menuItem.getSubMenu();
                boolean hasAuthor = !book.getParcelableArrayList(
                        UniqueId.BKEY_AUTHOR_ARRAY).isEmpty();
                boolean hasSeries = !book.getParcelableArrayList(
                        UniqueId.BKEY_SERIES_ARRAY).isEmpty();

                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, hasAuthor);
                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                                     hasAuthor && hasSeries);
                menu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_IN_SERIES, hasSeries);
                // let the normal call flow go on, it will display the submenu
                return false;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonManager.openWebsite(context, book.getPrimaryAuthor(), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonManager.openWebsite(context, null, book.getPrimarySeries());
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonManager.openWebsite(context,
                                          book.getPrimaryAuthor(), book.getPrimarySeries());
                return true;

            default:
                return false;
        }
    }
}
