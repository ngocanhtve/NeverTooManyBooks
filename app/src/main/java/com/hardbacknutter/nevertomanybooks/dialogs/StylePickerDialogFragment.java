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
package com.hardbacknutter.nevertomanybooks.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.adapters.RadioGroupRecyclerAdapter;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.PreferredStylesActivity;

public class StylePickerDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "StylePickerDialogFragment";

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
    private final ArrayList<BooklistStyle> mBooklistStyles = new ArrayList<>();
    private boolean mShowAllStyles;
    private RadioGroupRecyclerAdapter<BooklistStyle> mAdapter;

    private BooklistStyle mCurrentStyle;

    private WeakReference<StyleChangedListener> mListener;

    /**
     * Constructor.
     *
     * @param all if {@code true} show all styles, otherwise only the preferred ones.
     */
    public static void newInstance(@NonNull final FragmentManager fm,
                                   @NonNull final BooklistStyle currentStyle,
                                   final boolean all) {

        StylePickerDialogFragment smf = new StylePickerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_STYLE, currentStyle);
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);
        smf.setArguments(args);
        smf.show(fm, TAG);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final StyleChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mCurrentStyle = args.getParcelable(UniqueId.BKEY_STYLE);
        mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);

        loadStyles();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_styles_menu, null);

        RecyclerView listView = root.findViewById(R.id.styles);
        listView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);
        mAdapter = new RadioGroupRecyclerAdapter<>(getLayoutInflater(), mBooklistStyles,
                                                   mCurrentStyle, this::onStyleSelected);
        listView.setAdapter(mAdapter);

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                       .setTitle(R.string.title_select_style)
                       .setView(root)
                       .setNeutralButton(R.string.menu_customize_ellipsis, (d, w) -> {
                           Intent intent = new Intent(getContext(), PreferredStylesActivity.class);
                           // use the activity so we get the results there.
                           getActivity().startActivityForResult(intent,
                                                                UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                           dismiss();

                       })
                       // see onResume for setting the listener.
                       .setPositiveButton(posBtnTxtId(), null)
                       .create();
    }

    @StringRes
    private int posBtnTxtId() {
        return mShowAllStyles ? R.string.menu_show_less_ellipsis
                              : R.string.menu_show_more_ellipsis;
    }

    /**
     * Fetch the styles from the database.
     */
    private void loadStyles() {
        try (DAO db = new DAO()) {
            mBooklistStyles.clear();
            mBooklistStyles.addAll(BooklistStyles.getStyles(db, mShowAllStyles).values());
        }
    }

    /**
     * Set the dialog OnClickListener. This allows reloading the list without
     * having the dialog close on us after the user clicks a button.
     */
    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(Dialog.BUTTON_POSITIVE)
                  .setOnClickListener(v -> {
                      mShowAllStyles = !mShowAllStyles;
                      ((AlertDialog) getDialog()).getButton(Dialog.BUTTON_POSITIVE)
                                                 .setText(posBtnTxtId());
                      loadStyles();
                      mAdapter.notifyDataSetChanged();
                  });
        }
    }

    /**
     * Called when the user clicked a style. Send the result back to the listener.
     *
     * @param style the desired style
     */
    private void onStyleSelected(@NonNull final BooklistStyle style) {
        if (mListener.get() != null) {
            mListener.get().onStyleChanged(style);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onStyleChanged",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }

        dismiss();
    }

    public interface StyleChangedListener {

        void onStyleChanged(@NonNull BooklistStyle style);
    }
}
