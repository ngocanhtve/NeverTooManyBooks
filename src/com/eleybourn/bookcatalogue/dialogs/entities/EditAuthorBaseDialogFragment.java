package com.eleybourn.bookcatalogue.dialogs.entities;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Base class to edit an {@link Author}. Contains all logic up to the point of
 * storing the edits.
 */
public abstract class EditAuthorBaseDialogFragment
        extends DialogFragment {

    /** Database access. */
    protected DAO mDb;

    private AutoCompleteTextView mFamilyNameView;
    private AutoCompleteTextView mGivenNamesView;
    private Checkable mIsCompleteView;

    private Author mAuthor;
    private String mFamilyName;
    private String mGivenNames;
    private boolean mIsComplete;
    WeakReference<BookChangedListener> mBookChangedListener;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        Bundle args = requireArguments();

        mAuthor = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_AUTHOR));
        if (savedInstanceState == null) {
            mFamilyName = mAuthor.getFamilyName();
            mGivenNames = mAuthor.getGivenNames();
            mIsComplete = mAuthor.isComplete();
        } else {
            mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
            mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_edit_author, null);

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

        mIsCompleteView = root.findViewById(R.id.is_complete);
        mIsCompleteView.setChecked(mIsComplete);

        return new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.title_edit_book_authors)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                    mFamilyName = mFamilyNameView.getText().toString().trim();
                    if (mFamilyName.isEmpty()) {
                        UserMessage.show(mFamilyNameView, R.string.warning_missing_name);
                        return;
                    }

                    mGivenNames = mGivenNamesView.getText().toString().trim();
                    mIsComplete = mIsCompleteView.isChecked();
                    dismiss();

                    if (mAuthor.getFamilyName().equals(mFamilyName)
                            && mAuthor.getGivenNames().equals(mGivenNames)
                            && mAuthor.isComplete() == mIsComplete) {
                        return;
                    }
                    // Create a new Author as a holder for the changes.
                    Author newAuthorData = new Author(mFamilyName, mGivenNames, mIsComplete);

                    confirmChanges(mAuthor, newAuthorData);
                })
                .create();
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    /**
     * Handle the edits.
     *
     * @param author        the original data.
     * @param newAuthorData a holder for the edited data.
     */
    protected abstract void confirmChanges(@NonNull Author author,
                                           @NonNull Author newAuthorData);

    @Override
    public void onPause() {
        mFamilyName = mFamilyNameView.getText().toString().trim();
        mGivenNames = mGivenNamesView.getText().toString().trim();
        mIsComplete = mIsCompleteView.isChecked();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
        outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
        outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
