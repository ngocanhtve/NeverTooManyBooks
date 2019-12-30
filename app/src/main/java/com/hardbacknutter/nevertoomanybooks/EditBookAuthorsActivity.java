/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.baseactivity.EditObjectListActivity;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit a list of authors provided in an {@code ArrayList<Author>}
 * and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditBookAuthorDialogFragment} for list
 * <p>
 * You can edit the type in the edit-author dialog, but you cannot set it when adding
 * an author to the list.
 */
public class EditBookAuthorsActivity
        extends EditObjectListActivity<Author> {

    private static final String TAG = "EditBookAuthorsActivity";

    /**
     * Constructor.
     */
    public EditBookAuthorsActivity() {
        super(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_author;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_book_authors);

        mAutoCompleteAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                                   mModel.getDb()
                                         .getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));

        mAutoCompleteTextView = findViewById(R.id.author);
        mAutoCompleteTextView.setAdapter(mAutoCompleteAdapter);
    }

    @Override
    protected RecyclerViewAdapterBase
    createListAdapter(@NonNull final ArrayList<Author> list,
                      @NonNull final StartDragListener dragStartListener) {
        return new AuthorListAdapter(this, list, dragStartListener);
    }

    @Override
    protected void onAdd(@NonNull final View target) {
        String name = mAutoCompleteTextView.getText().toString().trim();
        if (name.isEmpty()) {
            Snackbar.make(mAutoCompleteTextView, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        Author newAuthor = Author.fromString(name);
        // see if it already exists
        newAuthor.fixId(this, mModel.getDb(), Locale.getDefault());
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            Snackbar.make(mAutoCompleteTextView, R.string.warning_author_already_in_list,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        mListAdapter.notifyDataSetChanged();

        // and clear the form for next entry.
        mAutoCompleteTextView.setText("");
    }

    /**
     * Process the modified (if any) data.
     *
     * @param author    the user was editing (with the original data)
     * @param tmpAuthor the modifications the user made in a placeholder object.
     *                  Non-modified data was copied here as well.
     */
    protected void processChanges(@NonNull final Author author,
                                  @NonNull final Author tmpAuthor) {

        // anything other than the type changed ?
        if (author.getFamilyName().equals(tmpAuthor.getFamilyName())
            && author.getGivenNames().equals(tmpAuthor.getGivenNames())
            && author.isComplete() == tmpAuthor.isComplete()) {

            // Type is not part of the Author table, but of the book_author table.
            if (author.getType() != tmpAuthor.getType()) {
                author.setType(tmpAuthor.getType());
                ItemWithFixableId
                        .pruneList(mList, this, mModel.getDb(), Locale.getDefault(), false);
                mListAdapter.notifyDataSetChanged();
            }
            // nothing was modified,
            // or only the type was different, either way, we're done here.
            return;
        }

        // The base data for the Author was modified.

        // See if the old one is used by any other books.
        long nrOfReferences = mModel.getDb().countBooksByAuthor(this, author)
                              + mModel.getDb().countTocEntryByAuthor(this, author);
        // if it's not, we simply re-use the old object.
        if (mModel.isSingleUsage(nrOfReferences)) {
            // Copy the new data into the original Author object that the user was changing.
            // The Author object keeps the same (old) ID!
            author.copyFrom(tmpAuthor, true);
            ItemWithFixableId.pruneList(mList, this, mModel.getDb(), Locale.getDefault(), false);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, the base data for the Author was modified and the old Author is used
        // in more than one place.
        // Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);
        String message = getString(R.string.confirm_apply_author_changed,
                                   author.getSorting(this),
                                   tmpAuthor.getSorting(this),
                                   allBooks);
        new AlertDialog.Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, which) -> {
                    // This change is done in the database NOW!
                    // Process the destination Series.
                    // It will either find/update or insert the data and update the tmpAuthor.id
                    boolean replaced;
                    //URGENT: do we really need to have a new id?
                    // Can we not simply update the original object?
                    if (mModel.getDb().updateOrInsertAuthor(this, tmpAuthor)) {
                        // Anywhere where the old Author (id) is used, replace it with the new one.
                        replaced = mModel.getDb().globalReplace(this, author, tmpAuthor);

                        // flag any changes up.
                        mModel.setGlobalReplacementsMade(replaced);
                        // unlink the old one, and link with the new Author
                        mList.remove(author);
                        mList.add(tmpAuthor);
                        ItemWithFixableId.pruneList(mList, this, mModel.getDb(),
                                                    Locale.getDefault(), false);
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        // eek?
                        Logger.warnWithStackTrace(this, TAG,
                                                  "Could not update", "author=" + tmpAuthor);
                        new AlertDialog.Builder(this)
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .setMessage(R.string.error_unexpected_error)
                                .show();
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (d, which) -> {
                    // treat the new data as a new Author.
                    // unlink the old one, and link with the new Author
                    mList.remove(author);
                    mList.add(tmpAuthor);
                    ItemWithFixableId.pruneList(mList, this, mModel.getDb(),
                                                Locale.getDefault(), false);
                    mListAdapter.notifyDataSetChanged();
                })
                .create()
                .show();
    }

    protected void updateItem(@NonNull final Author author,
                              @NonNull final Author newAuthorData,
                              @NonNull final Locale fallbackLocale) {
        // make the book related field changes
        author.copyFrom(newAuthorData, true);
        ItemWithFixableId.pruneList(mList, this, mModel.getDb(), fallbackLocale, false);
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditBookAuthorDialogFragment
            extends DialogFragment {

        static final String TAG = "EditBookAuthorDialogFrag";
        /** Key: type. */
        private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();
        /** Database Access. */
        protected DAO mDb;
        private EditBookAuthorsActivity mHostActivity;
        private AutoCompleteTextView mFamilyNameView;
        private AutoCompleteTextView mGivenNamesView;
        private Checkable mIsCompleteView;
        /** Enable or disable the type buttons. */
        private CompoundButton mUseTypeBtn;
        /** The Author we're editing. */
        private Author mAuthor;
        /** Current edit. */
        private String mFamilyName;
        /** Current edit. */
        private String mGivenNames;
        /** Current edit. */
        private boolean mIsComplete;
        /** Current edit. */
        @Author.Type
        private int mType;

        private boolean mAuthorTypeIsUsed;

        /**
         * Constructor.
         *
         * @param author to edit.
         *
         * @return the instance
         */
        static EditBookAuthorDialogFragment newInstance(@NonNull final Author author) {
            EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
            Bundle args = new Bundle(1);
            args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        protected int getLayoutId() {
            return R.layout.dialog_edit_book_author;
        }

        @Override
        public void onAttach(@NonNull final Context context) {
            super.onAttach(context);
            mHostActivity = (EditBookAuthorsActivity) context;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDb = new DAO(TAG);

            mAuthorTypeIsUsed = App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE);

            Bundle args = requireArguments();
            mAuthor = args.getParcelable(DBDefinitions.KEY_FK_AUTHOR);
            Objects.requireNonNull(mAuthor, "Author must be passed in args");

            if (savedInstanceState == null) {
                mFamilyName = mAuthor.getFamilyName();
                mGivenNames = mAuthor.getGivenNames();
                mIsComplete = mAuthor.isComplete();
                mType = mAuthor.getType();
            } else {
                mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                            false);
                mType = savedInstanceState.getInt(DBDefinitions.KEY_AUTHOR_TYPE);
            }
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            View root = layoutInflater.inflate(getLayoutId(), null);

            Context context = getContext();

            @SuppressWarnings("ConstantConditions")
            ArrayAdapter<String> mFamilyNameAdapter =
                    new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
            ArrayAdapter<String> mGivenNameAdapter =
                    new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

            // the dialog fields != screen fields.
            mFamilyNameView = root.findViewById(R.id.family_name);
            mFamilyNameView.setText(mFamilyName);
            mFamilyNameView.setAdapter(mFamilyNameAdapter);

            mGivenNamesView = root.findViewById(R.id.given_names);
            mGivenNamesView.setText(mGivenNames);
            mGivenNamesView.setAdapter(mGivenNameAdapter);

            mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
            mIsCompleteView.setChecked(mIsComplete);

            mTypeButtons.put(Author.TYPE_WRITER,
                             root.findViewById(R.id.cbx_author_type_writer));
            mTypeButtons.put(Author.TYPE_CONTRIBUTOR,
                             root.findViewById(R.id.cbx_author_type_contributor));
            mTypeButtons.put(Author.TYPE_INTRODUCTION,
                             root.findViewById(R.id.cbx_author_type_intro));
            mTypeButtons.put(Author.TYPE_TRANSLATOR,
                             root.findViewById(R.id.cbx_author_type_translator));
            mTypeButtons.put(Author.TYPE_EDITOR,
                             root.findViewById(R.id.cbx_author_type_editor));
            mTypeButtons.put(Author.TYPE_ARTIST,
                             root.findViewById(R.id.cbx_author_type_artist));
            mTypeButtons.put(Author.TYPE_INKING,
                             root.findViewById(R.id.cbx_author_type_inking));
            mTypeButtons.put(Author.TYPE_COLORIST,
                             root.findViewById(R.id.cbx_author_type_colorist));
            mTypeButtons.put(Author.TYPE_COVER_ARTIST,
                             root.findViewById(R.id.cbx_author_type_cover_artist));
            mTypeButtons.put(Author.TYPE_COVER_INKING,
                             root.findViewById(R.id.cbx_author_type_cover_inking));
            mTypeButtons.put(Author.TYPE_COVER_COLORIST,
                             root.findViewById(R.id.cbx_author_type_cover_colorist));

            mUseTypeBtn = root.findViewById(R.id.use_author_type_button);
            mUseTypeBtn.setOnCheckedChangeListener((v, isChecked) -> setTypeEnabled(isChecked));

            Group authorTypeGroup = root.findViewById(R.id.author_type_group);
            authorTypeGroup.setVisibility(mAuthorTypeIsUsed ? View.VISIBLE : View.GONE);

            if (mType != Author.TYPE_UNKNOWN) {
                setTypeEnabled(true);
                for (int i = 0; i < mTypeButtons.size(); i++) {
                    mTypeButtons.valueAt(i).setChecked((mType & mTypeButtons.keyAt(i)) != 0);
                }
            } else {
                setTypeEnabled(false);
            }

            return new AlertDialog.Builder(context)
                    .setIcon(R.drawable.ic_edit)
                    .setView(root)
                    .setTitle(R.string.title_edit_author)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                    .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                        // don't check on the name being the same as the old one here,
                        // we're doing more extensive checks later on.
                        mFamilyName = mFamilyNameView.getText().toString().trim();
                        if (mFamilyName.isEmpty()) {
                            Snackbar.make(mFamilyNameView, R.string.warning_missing_name,
                                          Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        mGivenNames = mGivenNamesView.getText().toString().trim();
                        mIsComplete = mIsCompleteView.isChecked();
                        mType = getTypeFromViews();

                        // Create a new Author as a holder for all changes.
                        Author tmpAuthor = new Author(mFamilyName, mGivenNames, mIsComplete);
                        if (mUseTypeBtn.isChecked()) {
                            tmpAuthor.setType(mType);
                        }
                        mHostActivity.processChanges(mAuthor, tmpAuthor);
                    })
                    .create();
        }

        @Override
        public void onStart() {
            super.onStart();

            if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
                // force the dialog to be big enough
                Dialog dialog = getDialog();
                if (dialog != null) {
                    int width = ViewGroup.LayoutParams.MATCH_PARENT;
                    int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    //noinspection ConstantConditions
                    dialog.getWindow().setLayout(width, height);
                }
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
            outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
            outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
            outState.putInt(DBDefinitions.KEY_AUTHOR_TYPE, mType);
        }

        @Override
        public void onPause() {
            mFamilyName = mFamilyNameView.getText().toString().trim();
            mGivenNames = mGivenNamesView.getText().toString().trim();
            mIsComplete = mIsCompleteView.isChecked();
            mType = getTypeFromViews();
            super.onPause();
        }

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
        }

        private void setTypeEnabled(final boolean enable) {
            // don't bother changing the 'checked' status, we'll ignore them anyhow.
            // and this is more user friendly if they flip the switch more than once.
            mUseTypeBtn.setChecked(enable);
            for (int i = 0; i < mTypeButtons.size(); i++) {
                CompoundButton typeBtn = mTypeButtons.valueAt(i);
                typeBtn.setEnabled(enable);
            }
        }

        private int getTypeFromViews() {
            int authorType = Author.TYPE_UNKNOWN;
            for (int i = 0; i < mTypeButtons.size(); i++) {
                if (mTypeButtons.valueAt(i).isChecked()) {
                    authorType |= mTypeButtons.keyAt(i);
                }
            }
            return authorType;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView authorView;
        @NonNull
        final TextView authorSortView;
        @NonNull
        final TextView authorTypeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            authorView = itemView.findViewById(R.id.row_author);
            authorSortView = itemView.findViewById(R.id.row_author_sort);
            authorTypeView = itemView.findViewById(R.id.row_author_type);

            if (!App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE)) {
                authorTypeView.setVisibility(View.GONE);
            }
        }
    }


    protected class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        private final boolean mAuthorTypeIsUsed;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);

            mAuthorTypeIsUsed = App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Context context = getContext();

            final Author author = getItem(position);
            String authorLabel = author.getLabel(context);

            holder.authorView.setText(authorLabel);

            if (!authorLabel.equals(author.getSorting(context))) {
                holder.authorSortView.setVisibility(View.VISIBLE);
                holder.authorSortView.setText(author.getSorting(context));
            } else {
                holder.authorSortView.setVisibility(View.GONE);
            }

            if (mAuthorTypeIsUsed && author.getType() != Author.TYPE_UNKNOWN) {
                holder.authorTypeView.setText(author.getTypeLabels(context));
                holder.authorTypeView.setVisibility(View.VISIBLE);
            } else {
                holder.authorTypeView.setVisibility(View.GONE);
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(
                    v -> EditBookAuthorDialogFragment
                            .newInstance(author)
                            .show(getSupportFragmentManager(), EditBookAuthorDialogFragment.TAG));
        }
    }
}
