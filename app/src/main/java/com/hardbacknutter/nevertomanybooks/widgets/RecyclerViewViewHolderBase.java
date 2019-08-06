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
package com.hardbacknutter.nevertomanybooks.widgets;

import android.graphics.Color;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.widgets.ddsupport.ItemTouchHelperViewHolder;

/**
 * Holder pattern for each row in a RecyclerView.
 * <p>
 * Extends the original with support for:
 * <ul>
 * <li>typed encapsulated item</li>
 * <li>a 'delete' button</li>
 * <li>a 'checkable' button</li>
 * <li>{@link ItemTouchHelperViewHolder}</li>
 * </ul>
 * Uses pre-defined ID's:
 * <ul>
 * <li>R.id.TLV_ROW_DETAILS</li>
 * <li>R.id.TLV_ROW_DELETE</li>
 * <li>R.id.TLV_ROW_CHECKABLE</li>
 * <li>R.id.TLV_ROW_GRABBER</li>
 * </ul>
 */
public class RecyclerViewViewHolderBase
        extends RecyclerView.ViewHolder
        implements ItemTouchHelperViewHolder {

    /** optional row checkable button. */
    @Nullable
    public final CompoundButton mCheckableButton;
    /** The details part of the row (or the row itself). */
    @NonNull
    public final View rowDetailsView;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    final ImageView mDragHandleView;
    /** optional row delete button. */
    @Nullable
    final View mDeleteButton;

    protected RecyclerViewViewHolderBase(@NonNull final View itemView) {
        super(itemView);

        // Don't enable the whole row, so buttons keep working
        View rd = itemView.findViewById(R.id.ROW_DETAILS);
        // but if we did not define a details row subview, use the row itself anyhow.
        rowDetailsView = rd != null ? rd : itemView;
        rowDetailsView.setFocusable(false);

        // optional
        mDeleteButton = itemView.findViewById(R.id.ROW_DELETE_BTN);
        mCheckableButton = itemView.findViewById(R.id.ROW_CHECKABLE_BTN);
        mDragHandleView = itemView.findViewById(R.id.ROW_GRABBER_ICON);
    }

    @Override
    public void onItemSelected() {
        //ENHANCE: style this.
        itemView.setBackgroundColor(Color.LTGRAY);
    }

    @Override
    public void onItemClear() {
        itemView.setBackgroundColor(Color.TRANSPARENT);
    }
}
