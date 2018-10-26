/*
 * @copyright 2011 Philip Warner
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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.UpdateFromInternetThread;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.LinkedHashMap;

/**
 * NEWKIND must stay in sync with {@link UpdateFromInternetThread}
 *
 * FIXME ... re-test and see why the progress stops. Seems we hit some limit in number of HTTP connections (server imposed ?)
 */
public class UpdateFromInternetActivity extends BaseActivityWithTasks {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_UPDATE_FROM_INTERNET;

    private final FieldUsages mFieldUsages = new FieldUsages();

    private long mBookId = 0;

    private LinearLayout mListContainer;

    private long mUpdateSenderId = 0;

    /** this is where the results can be 'consumed' before finishing this activity */
    private final ManagedTask.TaskListener mSearchTaskListener = new ManagedTask.TaskListener() {
        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            mUpdateSenderId = 0;
            Intent data = new Intent();
            // 0 if we did 'all books' or the id of the (hopefully) updated book.
            data.putExtra(UniqueId.KEY_ID, mBookId);
            data.putExtra(UniqueId.BKEY_CANCELED, task.isCancelled());
            setResult(Activity.RESULT_OK, data); /* 98a6d1eb-4df5-4893-9aaf-fac0ce0fee01 */
            finish();
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_update_from_internet;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mBookId = extras.getLong(UniqueId.KEY_ID, 0L);
            if (mBookId > 0) {
                // we're only requesting ONE book to be updated.
                TextView authorView = findViewById(R.id.author);
                authorView.setText(extras.getString(UniqueId.KEY_AUTHOR_FORMATTED));
                TextView titleView = findViewById(R.id.title);
                titleView.setText(extras.getString(UniqueId.KEY_TITLE));

                findViewById(R.id.row_book).setVisibility(View.VISIBLE);
            }
        }
        this.setTitle(R.string.select_fields_to_update);
        LibraryThingManager.showLtAlertIfNecessary(this, false, "update_from_internet");

        mListContainer = findViewById(R.id.manage_fields_scrollview);

        initFields();
        populateFields();
        initCancelConfirmButtons();
    }

    private void initFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, UniqueId.KEY_AUTHOR_ID,
                R.string.author, true,
                FieldUsage.Usage.ADD_EXTRA);
        addIfVisible(UniqueId.KEY_TITLE, null,
                R.string.title, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_ISBN, null,
                R.string.isbn, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_THUMBNAIL, null,
                R.string.thumbnail, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, UniqueId.KEY_SERIES_NAME,
                R.string.series, true,
                FieldUsage.Usage.ADD_EXTRA);
        addIfVisible(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, UniqueId.KEY_ANTHOLOGY_BITMASK,
                R.string.anthology, true,
                FieldUsage.Usage.ADD_EXTRA);
        addIfVisible(UniqueId.KEY_BOOK_PUBLISHER, null,
                R.string.publisher, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_DATE_PUBLISHED, null,
                R.string.date_published, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_FIRST_PUBLICATION, null,
                R.string.first_publication, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_DESCRIPTION, null,
                R.string.description, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_PAGES, null,
                R.string.pages, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_LIST_PRICE, null,
                R.string.list_price, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_FORMAT, null,
                R.string.format, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_GENRE, null,
                R.string.genre, false,
                FieldUsage.Usage.COPY_IF_BLANK);
        addIfVisible(UniqueId.KEY_BOOK_LANGUAGE, null,
                R.string.lbl_language, false,
                FieldUsage.Usage.COPY_IF_BLANK);
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param field     name to use in FieldUsages
     * @param visField  Field name to check for visibility. If null, use field itself.
     * @param stringId  of field label string
     * @param canAppend if the field is a list to which we can append to
     * @param usage     Usage to apply.
     */
    private void addIfVisible(@NonNull final String field,
                              @Nullable String visField,
                              @StringRes final int stringId,
                              final boolean canAppend,
                              @NonNull final FieldUsage.Usage usage) {

        if (visField == null || visField.trim().isEmpty()) {
            visField = field;
        }

        if (Fields.isVisible(visField)) {
            mFieldUsages.put(new FieldUsage(field, stringId, usage, canAppend));
        }
    }

    /**
     * Display the list of fields, dynamically adding them in a loop
     */
    private void populateFields() {

        for (FieldUsage usage : mFieldUsages.values()) {
            View row = this.getLayoutInflater().inflate(R.layout.row_update_from_internet, mListContainer, false);

            TextView fieldLabel = row.findViewById(R.id.field);
            fieldLabel.setText(usage.getLabel(this));

            CompoundButton cb = row.findViewById(R.id.usage);
            cb.setChecked(usage.isSelected());
            cb.setText(usage.getUsageInfo(UpdateFromInternetActivity.this));
            cb.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // ENHANCE The check is really a FOUR-state.
                    final CompoundButton cb = (CompoundButton) v;
                    final FieldUsage usage = ViewTagger.getTagOrThrow(cb);
                    usage.nextState();
                    cb.setChecked(usage.isSelected());
                    cb.setText(usage.getUsageInfo(UpdateFromInternetActivity.this));
                }
            });

            ViewTagger.setTag(cb, usage);
            mListContainer.addView(row);
        }
    }

    private void initCancelConfirmButtons() {
        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // sanity check
                if (countUserSelections() == 0) {
                    StandardDialogs.showBriefMessage(UpdateFromInternetActivity.this, R.string.select_min_1_field);
                    return;
                }

                // If they have selected thumbnails, check if they want to download ALL
                FieldUsage coversWanted = mFieldUsages.get(UniqueId.KEY_BOOK_THUMBNAIL);
                // but don't ask if its a single book only
                if (mBookId == 0 && coversWanted.isSelected()) {
                    // Verify - this can be a dangerous operation
                    AlertDialog dialog = new AlertDialog.Builder(UpdateFromInternetActivity.this)
                            .setMessage(R.string.overwrite_thumbnail)
                            .setTitle(R.string.update_fields)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .create();
                    dialog.setButton(AlertDialog.BUTTON_POSITIVE, UpdateFromInternetActivity.this.getString(R.string.yes),
                            new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int which) {
                                    mFieldUsages.get(UniqueId.KEY_BOOK_THUMBNAIL).usage = FieldUsage.Usage.OVERWRITE;
                                    startUpdate(mBookId);
                                }
                            });
                    dialog.setButton(AlertDialog.BUTTON_NEGATIVE, UpdateFromInternetActivity.this.getString(android.R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @SuppressWarnings("EmptyMethod")
                                public void onClick(final DialogInterface dialog, final int which) {
                                    //do nothing
                                }
                            });
                    dialog.setButton(AlertDialog.BUTTON_NEUTRAL, UpdateFromInternetActivity.this.getString(R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int which) {
                                    mFieldUsages.get(UniqueId.KEY_BOOK_THUMBNAIL).usage = FieldUsage.Usage.COPY_IF_BLANK;
                                    startUpdate(mBookId);
                                }
                            });
                    dialog.show();
                } else {
                    startUpdate(mBookId);
                }
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // not sure if needed... can't harm.
                ManagedTask.TaskController tc = UpdateFromInternetThread.getMessageSwitch().getController(mUpdateSenderId);
                if (tc != null) {
                    tc.requestAbort();
                }
                finish();
            }
        });
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @return super.onCreateOptionsMenu(menu);
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.search_sites)
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(this, SearchAdminActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int countUserSelections() {
        int nChildren = mListContainer.getChildCount();
        int nSelected = 0;
        for (int i = 0; i < nChildren; i++) {
            View v = mListContainer.getChildAt(i);
            CompoundButton cb = v.findViewById(R.id.usage);
            if (cb != null) {
                FieldUsage usage = ViewTagger.getTagOrThrow(cb);
                if (usage.isSelected()) {
                    nSelected++;
                }
            }
        }
        return nSelected;
    }

    /**
     * @param bookId 0 for all books, or a valid book id for one book
     */
    private void startUpdate(final long bookId) {
        UpdateFromInternetThread updateThread = new UpdateFromInternetThread(getTaskManager(), mFieldUsages, mSearchTaskListener);
        if (bookId > 0) {
            updateThread.setBookId(bookId);
        }

        mUpdateSenderId = updateThread.getSenderId();
        UpdateFromInternetThread.getMessageSwitch().addListener(mUpdateSenderId, mSearchTaskListener, false);
        updateThread.start();
    }

    @Override
    @CallSuper
    protected void onPause() {
        Tracker.enterOnPause(this);
        if (mUpdateSenderId != 0) {
            UpdateFromInternetThread.getMessageSwitch().removeListener(mUpdateSenderId, mSearchTaskListener);
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateFromInternetThread.getMessageSwitch().addListener(mUpdateSenderId, mSearchTaskListener, true);
        }
        Tracker.exitOnResume(this);
    }

    /**
     * Class to manage a collection of fields and the rules for importing them.
     * Inherits from {@link LinkedHashMap} to guarantee iteration order.
     *
     * @author Philip Warner
     */
    public static class FieldUsages extends LinkedHashMap<String, FieldUsage> {
        private static final long serialVersionUID = 1L;

        @NonNull
        public FieldUsage put(@NonNull final FieldUsage usage) {
            this.put(usage.key, usage);
            return usage;
        }
    }

    public static class FieldUsage {
        /** a key, usually from {@link com.eleybourn.bookcatalogue.UniqueId} */
        @NonNull
        public final String key;
        /** how to use this field */
        @NonNull
        public Usage usage;

        /** is the field a list type */
        private final boolean canAppend;
        /** label to show to the user */
        @StringRes
        private final int labelId;


        public FieldUsage(@NonNull final String name,
                          @StringRes final int id,
                          @NonNull final Usage usage,
                          final boolean canAppend) {
            this.key = name;
            this.labelId = id;
            this.canAppend = canAppend;
            this.usage = usage;
        }

        public boolean isSelected() {
            return (usage != Usage.SKIP);
        }

        public String getLabel(@NonNull final Context context) {
            return context.getString(labelId);
        }

        public String getUsageInfo(@NonNull final Context context) {
            return context.getString(usage.getStringId());
        }

        /**
         * Cycle to the next Usage stage:
         *
         * if (canAppend): SKIP -> COPY_IF_BLANK -> ADD_EXTRA -> OVERWRITE -> SKIP
         * else          : SKIP -> COPY_IF_BLANK -> OVERWRITE -> SKIP
         */
        public void nextState() {
            switch (usage) {
                case SKIP:
                    usage = Usage.COPY_IF_BLANK;
                    break;
                case COPY_IF_BLANK:
                    if (canAppend) {
                        usage = Usage.ADD_EXTRA;
                    } else {
                        usage = Usage.OVERWRITE;
                    }
                    break;
                case ADD_EXTRA:
                    usage = Usage.OVERWRITE;
                    break;
                case OVERWRITE:
                    usage = Usage.SKIP;
            }
        }

        public enum Usage {
            SKIP, COPY_IF_BLANK, ADD_EXTRA, OVERWRITE;

            @StringRes
            int getStringId() {
                switch (this) {
                    case COPY_IF_BLANK:
                        return R.string.usage_copy_if_blank;
                    case ADD_EXTRA:
                        return R.string.usage_add_extra;
                    case OVERWRITE:
                        return R.string.usage_overwrite;
                    default:
                        return R.string.usage_skip;
                }
            }
        }
    }
}
