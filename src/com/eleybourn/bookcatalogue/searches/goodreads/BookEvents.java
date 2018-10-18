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
import android.content.Intent;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.taskqueue.BindableItemCursor;
import com.eleybourn.bookcatalogue.taskqueue.Event;
import com.eleybourn.bookcatalogue.taskqueue.EventsCursor;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to define all book-related events that may be stored in the QueueManager.
 *
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 * NOTE: If internal data or data types need to change in this class after the application
 * has been deployed, CREATE A NEW CLASS OR EVENT. Otherwise, deployed serialized objects
 * may fail to reload. This is not a big issue (it's what LegacyTask and LegacyEvent are
 * for), but it is better to present data to the users cleanly.
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 *
 * @author Philip Warner
 */
class BookEvents {
    private BookEvents() {
    }

    /**
     * Method to retry sending a book to goodreads.
     */
    @Nullable
    private static final OnClickListener mRetryButtonListener = new OnClickListener() {
        @Override
        public void onClick(@NonNull View view) {
            BookEvent.BookEventHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_BOOK_EVENT_HOLDER);
            ((GrSendBookEvent) holder.event).retry();
        }
    };

    /**
     * Base class for all book-related events. Has a book ID and is capable of
     * displaying basic book data in a View.
     *
     * @author Philip Warner
     */
    private static class BookEvent extends Event {
        private static final long serialVersionUID = 74746691665235897L;

        final long mBookId;

        /**
         * Constructor
         *
         * @param bookId      ID of related book.
         * @param description Description of this event.
         */
        BookEvent(final long bookId, @NonNull final String description) {
            super(description);
            mBookId = bookId;
        }

        /**
         * Get the related Book ID.
         *
         * @return ID of related book.
         */
        public long getBookId() {
            return mBookId;
        }

        /**
         * Return a view capable of displaying basic book event details, ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         */
        @Override
        public View newListItemView(@NonNull final LayoutInflater inflater,
                                    @NonNull final Context context,
                                    @NonNull final BindableItemCursor cursor,
                                    @NonNull final ViewGroup parent) {
            View view = inflater.inflate(R.layout.row_book_event_info, parent, false);
            ViewTagger.setTag(view, R.id.TAG_EVENT, this);
            BookEventHolder holder = new BookEventHolder();
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.author = view.findViewById(R.id.author);
            holder.checkbox = view.findViewById(R.id.checked);
            holder.date = view.findViewById(R.id.date);
            holder.error = view.findViewById(R.id.error);
            holder.retry = view.findViewById(R.id.retry);
            holder.title = view.findViewById(R.id.title);

            ViewTagger.setTag(view, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.checkbox, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.retry, R.id.TAG_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        /**
         * Display the related book details in the passed View object.
         */
        @Override
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor bindableCursor,
                             @NonNull final Object appInfo) {
            final EventsCursor cursor = (EventsCursor) bindableCursor;

            // Update event info binding; the Views in the holder are unchanged, but when it is reused
            // the Event and ID will change.
            BookEventHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_BOOK_EVENT_HOLDER);
            holder.event = this;
            holder.rowId = cursor.getId();

            CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;
            ArrayList<Author> authors = db.getBookAuthorList(mBookId);
            String author;
            if (authors.size() > 0) {
                author = authors.get(0).getDisplayName();
                if (authors.size() > 1)
                    author = author + " " + BookCatalogueApp.getResourceString(R.string.and_others);
            } else {
                author = context.getString(R.string.unknown_uc);
            }

            String title = db.getBookTitle(mBookId);
            if (title==null) {
                title = context.getString(R.string.this_book_deleted_uc);
            }

            holder.title.setText(title);
            holder.author.setText(String.format(context.getString(R.string.by), author));
            holder.error.setText(this.getDescription());

            String date = String.format("(" + context.getString(R.string.occurred_at) + ")",
                    DateFormat.getDateTimeInstance().format(cursor.getEventDate()));
            holder.date.setText(date);

            holder.retry.setVisibility(View.GONE);

            holder.checkbox.setChecked(cursor.getIsSelected());
            holder.checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                    BookEventHolder holder = ViewTagger.getTagOrThrow(buttonView, R.id.TAG_BOOK_EVENT_HOLDER);
                    cursor.setIsSelected(holder.rowId, isChecked);
                }
            });

        }

        /**
         * Add ContextDialogItems relevant for the specific book the selected View is associated with.
         * Subclass can override this and add items at end/start or just replace these completely.
         */
        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final AdapterView<?> parent,
                                        @NonNull final View view,
                                        final int position,
                                        final long eventId,
                                        @NonNull final List<ContextDialogItem> items,
                                        @NonNull final Object appInfo) {

            // EDIT BOOK
            items.add(new ContextDialogItem(context.getString(R.string.edit_book), new Runnable() {
                @Override
                public void run() {
                    try {
                        GrSendBookEvent event = ViewTagger.getTagOrThrow(view, R.id.TAG_EVENT);
                            Intent intent = new Intent(context, EditBookActivity.class);
                            intent.putExtra(UniqueId.KEY_ID, event.getBookId());
                            intent.putExtra(EditBookActivity.REQUEST_KEY_TAB, EditBookActivity.TAB_EDIT);
                            context.startActivity(intent);
                    } catch (Exception ignore) {
                        // not a book event?
                    }
                }
            }));

            // ENHANCE Reinstate goodreads search when goodreads work.editions API is available
            //// SEARCH GOODREADS
//            items.add(new ContextDialogItem(context.getString(R.string.gr_search_goodreads), new Runnable() {
//            	@Override
//            	public void run() {
//            		BookEventHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_BOOK_EVENT_HOLDER);
//            		Intent intent = new Intent(context, GoodreadsSearchCriteria.class);
//            		intent.putExtra(GoodreadsSearchCriteria.REQUEST_EXTRA_BOOK_ID, holder.event.getBookId());
//            		context.startActivity(intent);
//            	}}));

            // DELETE EVENT
            items.add(new ContextDialogItem(context.getString(R.string.delete_event), new Runnable() {
                @Override
                public void run() {
                    BookCatalogueApp.getQueueManager().deleteEvent(eventId);
                }
            }));
        }

        /**
         * Class to implement the 'holder' model for view we create.
         *
         * @author Philip Warner
         */
        protected class BookEventHolder {
            long rowId;
            BookEvent event;
            TextView title;
            TextView author;
            TextView error;
            TextView date;
            Button retry;
            CompoundButton checkbox;
        }

    }

    /**
     * Subclass of BookEvent that is the base class for all Event objects resulting from
     * sending books to goodreads.
     *
     * @author Philip Warner
     */
    private static class GrSendBookEvent extends BookEvent {
        private static final long serialVersionUID = 1L;

        GrSendBookEvent(long bookId, @NonNull String message) {
            super(bookId, message);
        }

        /**
         * Resubmit this book and delete this event.
         */
        public void retry() {
            QueueManager qm = BookCatalogueApp.getQueueManager();
            SendOneBookTask task = new SendOneBookTask(mBookId);
            // TODO: MAKE IT USE THE SAME QUEUE? Why????
            qm.enqueueTask(task, BCQueueManager.QUEUE_SMALL_JOBS);
            qm.deleteEvent(this.getId());
        }

        /**
         * Override to allow modification of view.
         */
        @Override
        @CallSuper
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor bindableCursor,
                             @NonNull final Object appInfo) {
            // Get the 'standard' view.
            super.bindView(view, context, bindableCursor, appInfo);

            // get book details
            final BookEventHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_BOOK_EVENT_HOLDER);
            final CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;

            try (BooksCursor booksCursor = db.getBookForGoodreadsCursor(mBookId)) {
                // Hide parts of view based on current book details.
                if (booksCursor.moveToFirst()) {
                    final BookRowView bookRowView = booksCursor.getRowView();
                    if (bookRowView.getIsbn().isEmpty()) {
                        holder.retry.setVisibility(View.GONE);
                    } else {
                        holder.retry.setVisibility(View.VISIBLE);
                        ViewTagger.setTag(holder.retry, this);
                        holder.retry.setOnClickListener(mRetryButtonListener);
                    }
                } else {
                    holder.retry.setVisibility(View.GONE);
                }
            }

        }

        /**
         * Override to allow a new context menu item.
         */
        @Override
        @CallSuper
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final AdapterView<?> parent,
                                        @NonNull final View view,
                                        final int position, final long eventId,
                                        @NonNull List<ContextDialogItem> items,
                                        @NonNull Object appInfo) {
            super.addContextMenuItems(context, parent, view, position, eventId, items, appInfo);

            final CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;
            try (BooksCursor booksCursor = db.getBookForGoodreadsCursor(mBookId)) {
                final BookRowView bookRowView = booksCursor.getRowView();
                if (booksCursor.moveToFirst()) {
                    if (!bookRowView.getIsbn().isEmpty()) {
                        items.add(new ContextDialogItem(context.getString(R.string.retry_task), new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    GrSendBookEvent event = ViewTagger.getTagOrThrow(view, R.id.TAG_EVENT);
                                        event.retry();
                                        QueueManager.getQueueManager().deleteEvent(eventId);
                                } catch (Exception ignore) {
                                    // not a book event?
                                }
                            }
                        }));
                    }
                }
            }
        }
    }

    /**
     * Exception indicating the book's ISBN could not be found at GoodReads
     *
     * @author Philip Warner
     */
    public static class GrNoMatchEvent extends GrSendBookEvent implements HintOwner {
        private static final long serialVersionUID = -7684121345325648066L;

        public GrNoMatchEvent(final long bookId) {
            super(bookId, BookCatalogueApp.getResourceString(R.string.no_matching_book_found));
        }

        @Override
        public int getHint() {
            return R.string.gr_explain_goodreads_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank
     *
     * @author Philip Warner
     */
    public static class GrNoIsbnEvent extends GrSendBookEvent implements HintOwner {
        private static final long serialVersionUID = 7260496259505914311L;

        public GrNoIsbnEvent(final long bookId) {
            super(bookId, BookCatalogueApp.getResourceString(R.string.no_isbn_stored_for_book));
        }

        @Override
        public int getHint() {
            return R.string.gr_explain_goodreads_no_isbn;
        }

    }
}
