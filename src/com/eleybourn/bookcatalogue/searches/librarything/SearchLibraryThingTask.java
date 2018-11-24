package com.eleybourn.bookcatalogue.searches.librarything;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.SearchTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;


/**
 * LibraryThing SearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}
 *
 * We always contact LibraryThing because it is a good source of Series data and thumbnails.
 * But it does require an ISBN AND a developer key.
 */
public class SearchLibraryThingTask extends SearchTask {

    public SearchLibraryThingTask(final @NonNull String name,
                                  final @NonNull TaskManager manager) {
        super(name, manager);
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_LIBRARY_THING;
    }

    @Override
    protected void runTask() {
        final @StringRes int R_ID_SEARCHING = R.string.searching_library_thing;
        doProgress(getString(R_ID_SEARCHING), 0);

        LibraryThingManager ltm = new LibraryThingManager();
        if (!ltm.isAvailable()) {
            return;
        }

        try {
            // manager checks the arguments
            ltm.search(mIsbn, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name in the book title and clean KEY_TITLE
                checkForSeriesName();
            }
        } catch (java.net.SocketTimeoutException e) {
            showError(R_ID_SEARCHING, R.string.error_network_timeout);
        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            showError(R_ID_SEARCHING, R.string.error_search_configuration);

        } catch (IOException e) {
            showError(R_ID_SEARCHING, R.string.error_search_failed);
            Logger.error(e);

        } catch (Exception e) {
            Logger.error(e);
            showException(R_ID_SEARCHING, e);
        }

    }
}
