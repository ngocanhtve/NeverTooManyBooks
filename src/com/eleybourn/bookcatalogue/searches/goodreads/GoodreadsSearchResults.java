/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueListActivity;
import com.eleybourn.bookcatalogue.searches.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.utils.BCBackground;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * Search goodreads for a book and display the list of results. Use background tasks to get thumbnails and update when retrieved.
 * 
 * @author Philip Warner
 */
public class GoodreadsSearchResults extends BookCatalogueListActivity {
	//private static Integer mIdCounter = 0;
	//private int mId = 0;

	public static final String SEARCH_CRITERIA = "criteria";

	private CatalogueDBAdapter mDbHelper;
	private ArrayList<GoodreadsWork> mList = new ArrayList<>();
    private String mCriteria;
	private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("gr-covers");

	@Override
	protected int getLayoutId() {
		return R.layout.goodreads_work_list;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//synchronized(mIdCounter) {
		//	mId = ++mIdCounter;
		//}
		
		// Basic setup
		mDbHelper = new CatalogueDBAdapter(this);
		mDbHelper.open();

		// Look for search criteria
		Bundle extras = this.getIntent().getExtras();

		if (extras != null && extras.containsKey(SEARCH_CRITERIA)) {
			mCriteria = extras.getString(SEARCH_CRITERIA).trim();
		}

		// If we have criteria, do a search. Otherwise complain and finish.
		if (!mCriteria.isEmpty()) {
			doSearch();
		} else {
			Toast.makeText(this, getString(R.string.please_enter_search_criteria), Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		BCBackground.init(this);
	}

	/**
	 * Fix background
	 */
	@Override 
	public void onResume() {
		super.onResume();
		BCBackground.init(this);
	}

	/**
	 * Perform the search.
	 */
	private void doSearch() {
		// Get the GR stuff we need
		GoodreadsManager grMgr = new GoodreadsManager();
		SearchBooksApiHandler searcher = new SearchBooksApiHandler(grMgr);

		// Run the search
		ArrayList<GoodreadsWork> works;
		try {
			works = searcher.search(mCriteria);
		} catch (Exception e) {
			Logger.logError(e, "Failed when searching goodreads");
			Toast.makeText(this, getString(R.string.error_while_searching) + " " + getString(R.string.if_the_problem_persists), Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// Finish if no results, otherwise display them
		if (works == null || works.size() == 0) {
			Toast.makeText(this, getString(R.string.no_matching_book_found), Toast.LENGTH_LONG).show();			
			finish();
			return;
		}

		mList = works;
        ArrayAdapter<GoodreadsWork> mAdapter = new ResultsAdapter();
		setListAdapter(mAdapter);
	}

	/**
	 * Class used in implementing holder pattern for search results.
	 * 
	 * @author Philip Warner
	 */
	private class ListHolder {
		GoodreadsWork work;
		TextView title;
		TextView author;
		ImageView cover;
	}

	/**
	 * Handle user clicking on a book. This should show editions and allow the user to select a specific edition.
	 * Waiting on approval for API access.
	 * 
	 * @param v		View that was clicked.
	 */
	private void doItemClick(View v) {
		ListHolder holder = (ListHolder)ViewTagger.getTag(v);
		// TODO: Implement edition lookup - requires access to work.editions API from GR
		Toast.makeText(this, "Not implemented: see " + holder.title + " by " + holder.author, Toast.LENGTH_LONG).show();			
		//Intent i = new Intent(this, GoodreadsW)
	}

	/**
	 * ArrayAdapter that uses holder pattern to display goodreads books and allows for background image retrieval.
	 * 
	 * @author Philip Warner
	 *
	 */
	private class ResultsAdapter extends ArrayAdapter<GoodreadsWork> {
		/** Used in building views when needed */
        final LayoutInflater mInflater;

		ResultsAdapter() {
			super(GoodreadsSearchResults.this, 0, mList);
			// Save Inflater for later use
			mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		@NonNull
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			ListHolder holder;
			if(convertView == null) {
				// Not recycling
				try {
					// Get a new View and make the holder for it.
					convertView = mInflater.inflate(R.layout.goodreads_work_item, parent, false);
					holder = new ListHolder();
					holder.author = convertView.findViewById(R.id.author);
					holder.title = convertView.findViewById(R.id.title);
					holder.cover = convertView.findViewById(R.id.cover);

					// Save the holder
					ViewTagger.setTag(convertView, holder);

					// Set the click listener
					convertView.setOnClickListener(new OnClickListener(){
						@Override
						public void onClick(View v) {
							doItemClick(v);
						}});

				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException(e);
				}
	        } else {
	        	// Recycling: just get the holder
	        	holder = (ListHolder)ViewTagger.getTag(convertView);
	        }

			synchronized(convertView){
				synchronized(holder.cover) {
					// Save the work details
					holder.work = mList.get(position);
					// get the cover (or put it in background task)
					holder.work.fillImageView(mTaskQueue, holder.cover);

					// Update the views based on the work
					holder.author.setText(holder.work.authorName);
					holder.title.setText(holder.work.title);					
				}
			}

			return convertView;
		}
	}

	/**
	 * Cleanup
	 */
	@Override 
	public void onDestroy() {
		super.onDestroy();
		if (mDbHelper != null)
			mDbHelper.close();
	}

}
