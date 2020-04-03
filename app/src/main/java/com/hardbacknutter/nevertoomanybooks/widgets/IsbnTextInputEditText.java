/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;

/**
 * Provides a TextInputEditText field specific to editing an ISBN number.
 * <p>
 * This hides & facilitates the internal needs for the inputType, and the virtual keyboard.
 * <ul>
 *     <li>Sets a DigitsKeyListener to workaround the inputType issue;
 *          see {@link #getInputType()}</li>
 *      <li><strong>Deliberately</strong> not adding a TextWatcher here to keep this flexible</li>
 *      <li>the virtual keyboard can only add one 'X' character in the entire field</li>
 * </ul>
 * <p>
 * <strong>Notes on the virtual keyboard:</strong>
 * <p>
 * Stop if from showing when a field gets the focus.<br>
 * This must be done for <strong>ALL</strong> fields individually
 * <pre>
 * {@code editText.setShowSoftInputOnFocus(false);}
 * </pre>
 * Hide it when already showing:
 * <pre>
 * {@code
 *      InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
 *      if (imm != null && imm.isActive(this)) {
 *          imm.hideSoftInputFromWindow(getWindowToken(), 0);
 *      }
 * }
 * </pre>
 */
public class IsbnTextInputEditText
        extends TextInputEditText {

    /** all digits in ISBN strings. */
    private static final String ISBN_DIGITS = "0123456789xX";

    public IsbnTextInputEditText(@NonNull final Context context) {
        super(context);
        init();
    }

    public IsbnTextInputEditText(@NonNull final Context context,
                                 final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IsbnTextInputEditText(@NonNull final Context context,
                                 final AttributeSet attrs,
                                 final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // stop the virtual keyboard from showing
        setShowSoftInputOnFocus(false);
        setInputType(InputType.TYPE_NULL);

        setKeyListener(DigitsKeyListener.getInstance(ISBN_DIGITS));

        // hide the virtual keyboard.
        final InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    /**
     * Support for a custom virtual keyboard.
     * Handle character insertion at cursor position.
     * <p>
     * Only allows one 'X' to be entered.
     *
     * @param keyChar to handle
     */
    public void onKey(final char keyChar) {
        final int start = getSelectionStart();
        final int end = getSelectionEnd();
        Editable current = getText();
        if (current != null) {
            // only allow one X character
            if (keyChar != 'X'
                || (!current.toString().contains("X") && !current.toString().contains("x"))) {

                current.replace(start, end, String.valueOf(keyChar));
                // set the cursor immediately behind the inserted character
                setSelection(start + 1, start + 1);
            }
        }
    }

    /**
     * Support for a custom virtual keyboard.
     * Handle a virtual key press.
     * <p>
     * Currently only handles {@link KeyEvent#KEYCODE_DEL} but can be expanded as needed.
     *
     * @param keyCode to handle
     */
    public void onKey(final int keyCode) {
        final int start = getSelectionStart();
        final int end = getSelectionEnd();
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (start < end) {
                // We have a selection. Delete it.
                //noinspection ConstantConditions
                getText().replace(start, end, "");
                setSelection(start, start);
            } else {
                // Delete char before cursor
                if (start > 0) {
                    //noinspection ConstantConditions
                    getText().replace(start - 1, start, "");
                    setSelection(start - 1, start - 1);
                }
            }
        }
    }

    /**
     * <strong>THIS IS CRUCIAL</strong>
     * <p>
     * {@link #setInputType} and {@link #setKeyListener} seem to initiate a race condition
     * that one overrides the other no matter in which order they are called.
     * It seems the solution is:
     * <ol>
     *     <li>make this method return {@code null} (thereby disregarding the xml attribute</li>
     *     <li>Add a key listener in the constructor</li>
     * </ol>
     *
     * @return what we really want this method to return.
     */
    @Override
    public int getInputType() {
        return EditorInfo.TYPE_NULL;
    }
}
