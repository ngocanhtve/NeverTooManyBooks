/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Base Dialog class to edit an <strong>in-line in Books table</strong> String field.
 */
public abstract class EditStringBaseDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditStringBaseDialog";
    /** Argument. */
    static final String BKEY_TEXT = TAG + ":text";
    static final String BKEY_REQUEST_KEY = TAG + ":rk";

    @StringRes
    private final int dialogTitleId;
    @StringRes
    private final int labelResId;
    @NonNull
    private final String dataKey;
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** View Binding. */
    private DialogEditStringBinding vb;
    /** The text we're editing. */
    private String originalText;
    /** Current edit. */
    private String currentText;

    /**
     * Constructor; only used by the child class no-args constructor.
     *
     * @param titleId    for the dialog (i.e. the toolbar)
     * @param labelResId to use for the 'hint' of the input field
     */
    EditStringBaseDialogFragment(@StringRes final int titleId,
                                 @StringRes final int labelResId,
                                 @NonNull final String dataKey) {
        super(R.layout.dialog_edit_string);

        dialogTitleId = titleId;
        this.labelResId = labelResId;
        this.dataKey = dataKey;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        originalText = args.getString(BKEY_TEXT, "");

        if (savedInstanceState == null) {
            currentText = originalText;
        } else {
            currentText = savedInstanceState.getString(BKEY_TEXT, "");
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditStringBinding.bind(view);

        vb.toolbar.setTitle(dialogTitleId);

        vb.lblEditString.setHint(getString(labelResId));
        vb.lblEditString.setErrorEnabled(true);

        vb.editString.setText(currentText);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        vb.editString.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, getList());
        vb.editString.setAdapter(adapter);

        vb.editString.requestFocus();
    }

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_save) {
            return buttonPanel.findViewById(R.id.btn_positive);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Get the (optional) list of strings for the auto-complete.
     *
     * @return list
     */
    @NonNull
    protected List<String> getList() {
        return new ArrayList<>();
    }

    private boolean saveChanges() {
        viewToModel();
        if (currentText.isEmpty()) {
            showError(vb.lblEditString, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (currentText.equals(originalText)) {
            return true;
        }

        onSave(originalText, currentText);

        RowChangedListener.setResult(this, requestKey, dataKey, 0);
        return true;
    }

    private void viewToModel() {
        currentText = vb.editString.getText().toString().trim();
    }

    /**
     * Save data.
     *
     * @param originalText the original text which was passed in to be edited
     * @param currentText  the modified text
     */
    abstract void onSave(String originalText,
                         String currentText);

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_TEXT, currentText);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
