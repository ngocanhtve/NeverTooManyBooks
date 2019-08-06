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
package com.hardbacknutter.nevertomanybooks.dialogs.entities;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertomanybooks.BookChangedListener;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.entities.Publisher;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;

/**
 * Dialog to edit an existing publisher.
 * <p>
 * Calling point is a List.
 */
public class EditPublisherDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditPublisherDialogFragment";

    /** Database Access. */
    private DAO mDb;

    private Publisher mPublisher;
    private String mName;

    private AutoCompleteTextView mNameView;
    private WeakReference<BookChangedListener> mBookChangedListener;

    /**
     * Constructor.
     *
     * @param publisher to edit.
     *
     * @return the instance
     */
    public static EditPublisherDialogFragment newInstance(@NonNull final Publisher publisher) {

        EditPublisherDialogFragment frag = new EditPublisherDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_PUBLISHER, publisher);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPublisher = requireArguments().getParcelable(DBDefinitions.KEY_PUBLISHER);
        Objects.requireNonNull(mPublisher);
        if (savedInstanceState == null) {
            mName = mPublisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER);
        }

        mDb = new DAO();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_edit_publisher, null);

        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> mAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line,
                                   mDb.getPublisherNames());

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        return new AlertDialog.Builder(getContext())
                       .setIcon(R.drawable.ic_edit)
                       .setView(root)
                       .setTitle(R.string.lbl_publisher)
                       .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                       .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                           mName = mNameView.getText().toString().trim();
                           if (mName.isEmpty()) {
                               UserMessage.show(mNameView, R.string.warning_missing_name);
                               return;
                           }
                           dismiss();

                           if (mPublisher.getName().equals(mName)) {
                               return;
                           }
                           mDb.updatePublisher(mPublisher.getName(), mName);

                           Bundle data = new Bundle();
                           data.putString(DBDefinitions.KEY_PUBLISHER, mPublisher.getName());
                           if (mBookChangedListener.get() != null) {
                               mBookChangedListener.get()
                                                   .onBookChanged(0, BookChangedListener.PUBLISHER,
                                                                  data);
                           } else {
                               if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                                   Logger.debug(this, "onBookChanged",
                                                Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
                               }
                           }
                       })
                       .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_PUBLISHER, mName);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
