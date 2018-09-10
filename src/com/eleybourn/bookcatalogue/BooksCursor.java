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

package com.eleybourn.bookcatalogue;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.TrackedCursor;

import java.util.Hashtable;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_ROWID;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns.
 * 
 * The cursor also simulates a 'selected' flag for each book based on a 
 * HashMap of book IDs.
 * 
 * @author Philip Warner
 *
 */
public class BooksCursor extends TrackedCursor implements AutoCloseable {

	/** HashMap of selected book IDs */
	private final Hashtable<Long,Boolean> mSelections = new Hashtable<>();

	/**
	 * Constructor
	 */
	public BooksCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query, Synchronizer sync) {
		super(driver, editTable, query, sync);
	}


	/**
	 * Fake attribute to handle multi-select ListViews. if we ever do them.
	 * 
	 * @return	Flag indicating if current row has been marked as 'selected'.
	 */
	public boolean getIsSelected() {
		Long id = getId();
		if (mSelections.containsKey(id)) {
			return mSelections.get(id);
		} else {
			return false;
		}
	}

	public void setIsSelected(boolean selected) {
		mSelections.put(getId(), selected);
	}
	
	/**
	 * Get the row ID; need a local implementation so that get/setSelected() works.
	 */
	private int mIdCol = -2;
	public final long getId() {
		if (mIdCol < 0) {
			mIdCol = getColumnIndex(KEY_ROWID);
			if (mIdCol < 0)
				throw new RuntimeException("ISBN column not in result set");
		}
		return getLong(mIdCol);// mCurrentRow[mIsbnCol];
	}

	/**
	 * Get a RowView
	 */
	private BooksRowView mView;
	public BooksRowView getRowView() {
		if (mView == null)
			mView = new BooksRowView(this);
		return mView;
	}

//	/**
//	 * Snapshot cursor to use with this cursor.
//	 *
//	 * NOT IMPLEMENTED: Android 1.6 SQLite interface does not support getting column types.
//	 *
//	 * @author Philip Warner
//	 */
//	public static class BooksSnapshotCursor extends CursorSnapshotCursor {
//		BooksRowView mView;
//
//		public BooksSnapshotCursor(SQLiteCursor source) {
//			super(source);
//		}
//
//		public BooksRowView getRowView() {
//			if (mView == null)
//				mView = new BooksRowView(this);
//			return mView;
//		}
//
//		/**
//		 * Clear the RowView and selections, if any
//		 */
//		@Override
//		public void close() {
//			super.close();
//			mView = null;
//		}
//	}

	/**
	 * Clear the RowView and selections, if any
	 */
	@Override
	public void close() {
		super.close();
		mSelections.clear();
		mView = null;
	}
}
