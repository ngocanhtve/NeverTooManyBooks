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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.ItemTouchHelperViewHolder;


/**
 * Row ViewHolder for each editable row in a RecyclerView.
 *
 * <ul>Provides support for:
 *      <li>{@link ItemTouchHelperViewHolder}</li>
 *      <li>a 'checkable' button</li>
 * </ul>
 * <ul>Uses pre-defined ID's:
 *      <li>R.id.ROW_CHECKABLE_BTN</li>
 *      <li>R.id.ROW_GRABBER_ICON</li>
 * </ul>
 */
public abstract class ItemTouchHelperViewHolderBase
        extends RowViewHolder
        implements ItemTouchHelperViewHolder {

    /** optional row checkable button. */
    @Nullable
    private final CompoundButton checkableButton;
    /** optional drag handle button for drag/drop support. */
    @Nullable
    private final ImageView dragHandleView;

    /** used while dragging. */
    @ColorInt
    private final int itemDraggedBackgroundColor;
    /** preserves the original background while dragging. */
    private Drawable originalItemSelectedBackground;

    protected ItemTouchHelperViewHolderBase(@NonNull final View itemView) {
        super(itemView);
        setClickTargetViewFocusable(false);

        itemDraggedBackgroundColor = AttrUtils.getColorInt(
                itemView.getContext(), com.google.android.material.R.attr.colorPrimary);

        // optional
        dragHandleView = itemView.findViewById(R.id.ROW_GRABBER_ICON);
        checkableButton = itemView.findViewById(R.id.ROW_CHECKABLE_BTN);
    }

    public boolean isDraggable() {
        return dragHandleView != null;
    }

    @SuppressLint("ClickableViewAccessibility")
    void setOnDragListener(@NonNull final View.OnTouchListener listener) {
        //noinspection ConstantConditions
        dragHandleView.setOnTouchListener(listener);
    }

    /**
     * The user clicked on the checkable button.
     *
     * @param listener which received the position clicked, and must
     *                 return the new checkable status for the row.
     */
    public void setOnItemCheckChangedListener(@NonNull final Function<Integer, Boolean> listener) {
        //noinspection ConstantConditions
        checkableButton.setOnClickListener(
                v -> setChecked(listener.apply(getBindingAdapterPosition())));
    }

    public void setChecked(final boolean checked) {
        //noinspection ConstantConditions
        checkableButton.setChecked(checked);
    }

    @Override
    public void onItemDragStarted() {
        originalItemSelectedBackground = itemView.getBackground();
        itemView.setBackgroundColor(itemDraggedBackgroundColor);
    }

    @Override
    public void onItemDragFinished() {
        //2022-03-28: this used to work by just calling 'setBackground'
        // It seems we now ALSO need to call 'setBackgroundColor' ??
        itemView.setBackgroundColor(Color.TRANSPARENT);
        itemView.setBackground(originalItemSelectedBackground);
    }
}
