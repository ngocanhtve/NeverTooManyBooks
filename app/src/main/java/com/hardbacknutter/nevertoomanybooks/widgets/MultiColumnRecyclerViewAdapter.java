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

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public abstract class MultiColumnRecyclerViewAdapter<HOLDER extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<HOLDER> {

    private static final String TAG = "MultiColumnRecyclerView";

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    private final int columnCount;

    public MultiColumnRecyclerViewAdapter(@NonNull final Context context,
                                          final int columnCount) {
        this.inflater = LayoutInflater.from(context);
        this.columnCount = columnCount;
    }

    @NonNull
    protected LayoutInflater getInflater() {
        return inflater;
    }

    protected int transpose(final int position) {
        final int realItemCount = getRealItemCount();
        final int rowCount = getRowCount(realItemCount);

        final int column = position % columnCount;
        final int row = position / columnCount;

        int listIndex = (column * rowCount) + row;

        if (listIndex >= realItemCount) {
            listIndex = -1;
        }

        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "transpose",
                     "realItemCount=" + realItemCount
                     + ", rowCount=" + rowCount
                     + ", position=" + position
                     + ", column=" + column
                     + ", row=" + row
                     + ", listIndex=" + listIndex);
        }
        return listIndex;
    }

    protected int revert(final int listIndex) {
        final int realItemCount = getRealItemCount();
        final int rowCount = getRowCount(realItemCount);

        final int column = listIndex % rowCount;
        final int row = listIndex / rowCount;

        final int position = (column * columnCount) + row;

        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "revert",
                     "listIndex=" + listIndex
                     + ", position=" + position);
        }
        return position;
    }


    protected int getRowCount(final int realItemCount) {
        final int rowCount;
        if (realItemCount % columnCount != 0) {
            rowCount = (realItemCount / columnCount) + 1;
        } else {
            rowCount = realItemCount / columnCount;
        }
        return rowCount;
    }

    /**
     * Acts like the original getItemCount() method.
     *
     * @return the actual item count
     */
    protected abstract int getRealItemCount();

    /**
     * Return the <strong>CELL COUNT</strong> for the grid.
     *
     * @return cell count
     */
    @Override
    public int getItemCount() {
        final int itemCount = getRealItemCount();
        final int rowCount = getRowCount(itemCount);
        return rowCount * columnCount;
    }
}
