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
package com.hardbacknutter.nevertomanybooks.baseactivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Base class for editing a list of objects.
 * <p>
 * {@link #createListAdapter} needs to be implemented returning a suitable RecyclerView adapter.
 *
 * @param <T> the object type as used in the List
 */
public abstract class EditObjectListActivity<T extends Parcelable>
        extends BaseActivity {

    /** The key to use in the Bundle to get the array. */
    @NonNull
    private final String mBKey;

    /** Database Access. */
    protected DAO mDb;

    /** the rows. */
    protected ArrayList<T> mList;

    /** flag indicating global changes were made. Used in setResult. */
    protected boolean mGlobalReplacementsMade;

    /** AutoCompleteTextView adapter. */
    protected ArrayAdapter<String> mAutoCompleteAdapter;
    /** Main screen name field. */
    protected AutoCompleteTextView mAutoCompleteTextView;

    /** The adapter for the list. */
    protected RecyclerViewAdapterBase mListAdapter;
    /** Row ID... mainly used (if list is from a book) to know if the object is new. */
    protected long mRowId;
    /** Displayed for user reference only. */
    @Nullable
    private String mBookTitle;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * Constructor.
     *
     * @param bkey The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(@NonNull final String bkey) {
        mBKey = bkey;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        // Look for id and title
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mRowId = args.getLong(DBDefinitions.KEY_PK_ID);
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

            mList = args.getParcelableArrayList(mBKey);
        }

        // The View for the list.
        RecyclerView listView = findViewById(android.R.id.list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = createListAdapter(mList, vh -> mItemTouchHelper.startDrag(vh));
        listView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(listView);

        TextView titleView = findViewById(R.id.title);
        if (mBookTitle == null || mBookTitle.isEmpty()) {
            titleView.setVisibility(View.GONE);
        } else {
            titleView.setText(mBookTitle);
        }

        findViewById(R.id.btn_add).setOnClickListener(this::onAdd);
    }

    @Override
    public void onBackPressed() {

        Intent data = new Intent()
                              .putExtra(mBKey, mList)
                              .putExtra(UniqueId.BKEY_GLOBAL_CHANGES_MADE, mGlobalReplacementsMade);

        String name = mAutoCompleteTextView.getText().toString().trim();
        if (!name.isEmpty()) {

            // if the user had enter a (partial) new name, check if it's ok to leave
            StandardDialogs.showConfirmUnsavedEditsDialog(this, () -> {
                // runs when user clicks 'exit anyway'. The list itself IS saved.
                setResult(Activity.RESULT_OK, data);
                finish();
            });
        }

        // no current edit, so we're good to go.
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    /**
     * Get the specific adapter from the child class.
     *
     * @return adapter
     */
    protected abstract RecyclerViewAdapterBase
    createListAdapter(@NonNull ArrayList<T> list,
                      @NonNull StartDragListener dragStartListener);

    /**
     * The user entered new data in the edit field and clicked 'add'.
     *
     * @param target The view that was clicked ('add' button).
     */
    protected abstract void onAdd(@NonNull View target);

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Ensure that the list is saved.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList(mBKey, mList);
        outState.putLong(DBDefinitions.KEY_PK_ID, mRowId);
        outState.putString(DBDefinitions.KEY_TITLE, mBookTitle);
    }
}
