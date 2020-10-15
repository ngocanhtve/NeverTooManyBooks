/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "PreferredStylesViewMode";

    /** Database Access. */
    private DAO mDb;

    /** the selected style at onCreate time. */
    private String mInitialStyleUuid;

    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean mIsDirty;

    /** The *in-memory* list of styles. */
    private ArrayList<BooklistStyle> mStyleList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);
            mStyleList = StyleDAO.getStyles(context, mDb, true);

            mInitialStyleUuid = Objects.requireNonNull(
                    args.getString(BooklistStyle.BKEY_STYLE_UUID), "mInitialStyleUuid");
        }
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    public boolean isDirty() {
        return mIsDirty;
    }


    public void setDirty(final boolean isDirty) {
        mIsDirty = isDirty;
    }

    /**
     * Get the style UUID that was the selected style when this object was created.
     *
     * @return UUID
     */
    @NonNull
    public String getInitialStyleUuid() {
        return mInitialStyleUuid;
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mStyleList;
    }

    /**
     * Called after a style has been edited.
     *
     * @param style      the (potentially) modified style
     * @param templateId id of the original style we cloned (different from current)
     *                   or edited (same as current).
     *
     * @return position of the style in the list
     */
    public int onStyleEdited(@NonNull final BooklistStyle style,
                             final long templateId) {
        mIsDirty = true;

        // Always save/update a new style to the database first.
        // This will give it an id if it's a new style.
        StyleDAO.updateOrInsert(mDb, style);

        // Now (re)organise the list of styles.

        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' (use id instead), as the incoming style object
        // was parcelled along the way, which *might* have changed it.
        int editedRow = -1;
        for (int i = 0; i < mStyleList.size(); i++) {
            if (mStyleList.get(i).getId() == style.getId()) {
                editedRow = i;
                break;
            }
        }

        if (editedRow < 0) {
            // Not in the list; we're adding a new Style.
            // Put it at the top and set as user-preferred
            mStyleList.add(0, style);
            style.setPreferred(true);
            // save the preferred state
            StyleDAO.update(mDb, style);
            editedRow = 0;

        } else {
            // We edited an existing Style.
            // Check if we edited in-place or cloned a style
            final BooklistStyle origStyle = mStyleList.get(editedRow);
            if (origStyle.equals(style)) {
                // just a style edited in-place, update the list with the new object
                mStyleList.set(editedRow, style);

            } else {
                if (origStyle.isUserDefined()) {
                    // It's a clone of an user-defined style.
                    // Put it directly after the user-defined original
                    mStyleList.add(editedRow, style);

                } else {
                    // It's a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // if the original style was a preferred style,
                        // replace the original row with the new one
                        mStyleList.set(editedRow, style);

                        // Make the new one preferred
                        style.setPreferred(true);
                        // save the preferred state
                        StyleDAO.update(mDb, style);

                        // And demote the original
                        origStyle.setPreferred(false);
                        // save the preferred state
                        StyleDAO.update(mDb, origStyle);

                        mStyleList.add(origStyle);

                    } else {
                        // Put it directly after the original
                        mStyleList.add(editedRow, style);
                    }
                }
            }
        }

        // Not sure if this check is really needed... or already covered above
        // if the style was cloned from a builtin style,
        if (templateId < 0) {
            // then we're assuming the user wanted to 'replace' the builtin style,
            // so remove the builtin style from the preferred styles.
            mStyleList.stream()
                      .filter(s -> s.getId() == templateId)
                      .findFirst()
                      .ifPresent(s -> {
                          s.setPreferred(false);
                          // save the preferred state
                          StyleDAO.update(mDb, s);
                      });
        }

        return editedRow;
    }

    /**
     * Update the given style.
     *
     * @param style to update
     */
    public void updateStyle(@NonNull final BooklistStyle style) {
        StyleDAO.update(mDb, style);
    }

    /**
     * Delete the given style.
     *
     * @param context Current context
     * @param style   to delete
     */
    public void deleteStyle(@NonNull final Context context,
                            @NonNull final BooklistStyle style) {
        StyleDAO.delete(context, mDb, style);
        mStyleList.remove(style);
    }

    /**
     * Save the preferred style menu list.
     */
    public void updateMenuOrder() {
        StyleDAO.updateMenuOrder(mDb, mStyleList);
    }

    /**
     * User explicitly wants to purge the BLNS for the given style.
     *
     * @param styleId to purge
     */
    public void purgeBLNS(final long styleId) {
        mDb.purgeNodeStatesByStyle(styleId);
    }
}
