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

package com.eleybourn.bookcatalogue.tasks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BindableItemListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsExportFailuresActivity;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.OnTaskChangeListener;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.TaskActions;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.taskqueue.Task;
import com.eleybourn.bookcatalogue.taskqueue.TasksCursor;
import com.eleybourn.bookcatalogue.taskqueue.TasksCursor.TaskCursorSubtype;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to display the available QueueManager Task object subclasses to the user.
 *
 * @author Philip Warner
 */
public class TaskListActivity extends BindableItemListActivity {
    /**
     * Listener to handle Event add/change/delete.
     */
    private final OnTaskChangeListener m_OnTaskChangeListener = new OnTaskChangeListener() {
        @Override
        public void onTaskChange(final @Nullable Task task, final @NonNull TaskActions action) {
            TaskListActivity.this.refreshData();
        }
    };

    private CatalogueDBAdapter mDb = null;
    private TasksCursor mCursor;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_task_list;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.background_tasks);

            mDb = new CatalogueDBAdapter(this)
                    .open();

            //When any Event is added/changed/deleted, update the list. Lazy, yes.
            BookCatalogueApp.getQueueManager().registerTaskListener(m_OnTaskChangeListener);

            View cleanupButton = this.findViewById(R.id.cleanup);
            cleanupButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    QueueManager.getQueueManager().cleanupOldTasks(7);
                }
            });

            if (savedInstanceState == null)
                HintManager.displayHint(this.getLayoutInflater(), R.string.hint_background_tasks, null);

        } catch (Exception e) {
            Logger.error(e);
        }

    }

    /**
     * Refresh data; some other activity may have changed relevant data (eg. a book)
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    /**
     * Build a context menu dialogue when an item is clicked.
     */
    @Override
    public void onListItemClick(@NonNull AdapterView<?> parent, final @NonNull View v, final int position, final long id) {
        Task task = ViewTagger.getTagOrThrow(v, R.id.TAG_TASK);
        List<ContextDialogItem> items = new ArrayList<>();

        items.add(new ContextDialogItem(getString(R.string.show_events_ellipsis), new Runnable() {
            @Override
            public void run() {
                doShowTaskEvents(id);
            }
        }));

        task.addContextMenuItems(this, parent, v, position, id, items, mDb);

        if (items.size() > 0) {
            showContextDialogue(R.string.select_an_action, items);
        }
    }

    private void doShowTaskEvents(final long taskId) {
        Intent intent = new Intent(this, GoodreadsExportFailuresActivity.class);
        intent.putExtra(GoodreadsExportFailuresActivity.REQUEST_BKEY_TASK_ID, taskId);
        startActivity(intent);
    }

    /**
     * Return the number of task types we might return. 50 is just paranoia.
     * RELEASE: Keep checking this value!
     */
    @Override
    public int getBindableItemTypeCount() {
        return 50;
    }

    /**
     * Pass binding off to the task object.
     */
    @Override
    public void bindViewToItem(final @NonNull Context context,
                               final @NonNull View view,
                               final @NonNull BindableItemCursor cursor,
                               final @NonNull BindableItemCursorAdapter.BindableItem bindable) {
        ViewTagger.setTag(view, R.id.TAG_TASK, bindable);
        bindable.bindView(view, context, cursor, mDb);
    }

    /**
     * Get a cursor returning the tasks we are interested in (in this case all tasks)
     */
    @NonNull
    @Override
    protected BindableItemCursor getBindableItemCursor(final @Nullable Bundle savedInstanceState) {
        mCursor = QueueManager.getQueueManager().getTasks(TaskCursorSubtype.all);
        return mCursor;
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);

        try {
            BookCatalogueApp.getQueueManager().unregisterTaskListener(m_OnTaskChangeListener);
        } catch (Exception ignore) {}

        try {
            if (mCursor != null)
                mCursor.close();
        } catch (Exception ignore) {}

        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }
}
