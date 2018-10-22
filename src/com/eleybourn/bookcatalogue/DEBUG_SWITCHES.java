package com.eleybourn.bookcatalogue;

/**
 * Global location where you can switch individual DEBUG options of/off
 *
 * When set to true, the global BuildConfig.DEBUG should still suppress them
 * Use something like this:
 *
 *    if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
 *          Logger.info("some debug info);
 *    }
 *
 *    RELEASE: set all to false ! in case you forgot to '&&' with BuildConfig.DEBUG anywhere...
 *
 *    The compiler should remove all code between dead if() blocks
 */
public final class DEBUG_SWITCHES {

    public static final boolean TMP_ANTHOLOGY = true;

    public static final boolean BOOKLIST_BUILDER = true;

    public static final boolean BOOKS_ON_BOOKSHELF = true;

    /** enable timers for performance measurements */
    public static final boolean TIMERS = false;

    /** dump the sql string to the log */
    public static final boolean SQL = true;

    /** {@link com.eleybourn.bookcatalogue.database.CatalogueDBAdapter} */
    public static final boolean DB_ADAPTER = false;

    /** {@link com.eleybourn.bookcatalogue.database.DbSync} */
    public static final boolean DB_SYNC = false;
    /** {@link com.eleybourn.bookcatalogue.database.DbSync} */
    public static final boolean DB_SYNC_QUERY_FOR_LONG = false;

    /** {@link com.eleybourn.bookcatalogue.database.cursors.TrackedCursor} */
    public static final boolean TRACKED_CURSOR = false;

    /** {@link com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager} */
    public static final boolean LIBRARY_THING_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.goodreads} */
    public static final boolean GOODREADS = false;

    /** {@link com.eleybourn.bookcatalogue.utils.ImageUtils} */
    public static final boolean IMAGE_UTILS = false;

    /** {@link com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment} */
    public static final boolean SQPFragment = false;

    /** {@link com.eleybourn.bookcatalogue.utils.StorageUtils} */
    public static final boolean STORAGE_UTILS = false;

    /** {@link com.eleybourn.bookcatalogue.tasks.TaskManager} */
    public static final boolean TASK_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.SearchManager} and related */
    public static final boolean SEARCH_INTERNET = false;

    /** all things related to sending messages around */
    public static final boolean MESSAGING = false;

    /** reading/writing a backup file */
    public static final boolean BACKUP = false;

    /** all things that can happen during startup only */
    public static final boolean STARTUP = false;
}
