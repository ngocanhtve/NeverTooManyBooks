package com.eleybourn.bookcatalogue.searches.amazon;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.utils.TaskManager;

public class SearchAmazonThread extends SearchThread {

    public SearchAmazonThread(TaskManager manager,
                              String author, String title, String isbn, boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
    }

    @Override
    protected void onRun() {
        this.doProgress(getString(R.string.searching_amazon_books), 0);

        try {
            AmazonManager.searchAmazon(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() == 0) {
                Logger.logError(new RuntimeException("No data found for " + mIsbn + "/" + mAuthor + "/" + mTitle));
            }
            // Look for series name and clear KEY_TITLE
            checkForSeriesName();
        } catch (Exception e) {
            Logger.logError(e);
            showException(R.string.searching_amazon_books, e);
        }
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_AMAZON;
    }

}
