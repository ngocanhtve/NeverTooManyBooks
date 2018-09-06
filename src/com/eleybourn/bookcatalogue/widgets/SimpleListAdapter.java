/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * ArrayAdapter to manage rows of an arbitrary type with row movement via clicking
 * on predefined sub-views, if present.
 *
 * The layout can optionally contain these "@+id/" :
 *
 *  - row_details         onRowClick
 *  - if no 'id/row_details' found, then 'id/row' is tried instead
 *  - ROW_UP              onRowUp
 *  - ROW_DOWN            onRowDown
 *  - ROW_DELETE          onRowDelete
 *
 *  ids.xml has these predefined:
 *  <pre>
 *		<item name="row_details" type="id"/>
 *		<item name="row" type="id"/>
 *     	<item name="ROW_UP" type="id"/>
 *		<item name="ROW_DOWN" type="id"/>
 *		<item name="ROW_DELETE" type="id"/>
 *     	<item name="TAG_POSITION" type="id" />
 *	</pre>
 *
 * @author Philip Warner
 */
public abstract class SimpleListAdapter<T> extends ArrayAdapter<T> {
    private final int mRowViewId;
    private final ArrayList<T> mItems;
    private final OnClickListener mRowClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int pos = getViewRow(v);
                T item = getItem(pos);
                onRowClick(v, item, pos);
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    };
    private final OnClickListener mRowDeleteListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int pos = getViewRow(v);
                T old = getItem(pos);
                if (onRowDelete(v, old, pos)) {
                    remove(old);
                    notifyDataSetChanged();
                    onListChanged();
                }
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }
        }
    };
    private final OnClickListener mRowDownListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = getViewRow(v);
            if (pos == (getCount() - 1))
                return;
            T old = getItem(pos);
            try {
                onRowDown(v, old, pos);

                mItems.set(pos, getItem(pos + 1));
                mItems.set(pos + 1, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }
        }
    };
    private final OnClickListener mRowUpListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = getViewRow(v);
            if (pos == 0)
                return;
            T old = getItem(pos - 1);
            try {
                onRowUp(v, old, pos);

                mItems.set(pos - 1, getItem(pos));
                mItems.set(pos, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }

        }
    };

    // Flag fields to (slightly) optimize lookups and prevent looking for
    // fields that are not there.
    private boolean mCheckedFields = false;
    private boolean mHasPosition = false;
    private boolean mHasUp = false;
    private boolean mHasDown = false;
    private boolean mHasDelete = false;

    public SimpleListAdapter(Context context, int rowViewId, ArrayList<T> items) {
        super(context, rowViewId, items);
        mRowViewId = rowViewId;
        mItems = items;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected void onListChanged() {
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected void onRowClick(View v, T object, int position) {
    }

    /**
     *
     * @return  true if delete is allowed to happen
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    protected boolean onRowDelete(View v, T object, int position) {
        return true;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected void onRowDown(View v, T object, int position) {
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected void onRowUp(View v, T object, int position) {
    }

    /**
     * Call to set up the row view.
     *  @param target The target row view object
     * @param object The object (or type T) from which to draw values.
     */
    abstract protected void onSetupView(View target, T object, int position);

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        final T object = this.getItem(position);

        // Get the view; if not defined, load it.
        if (convertView == null) {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // If possible, ask the object for the view ID
            if (object != null && object instanceof ViewProvider) {
                //noinspection ConstantConditions
                convertView = vi.inflate(((ViewProvider) object).getViewId(), null);
            } else {
                //noinspection ConstantConditions
                convertView = vi.inflate(mRowViewId, null);
            }
        }

        // Save this views position
        ViewTagger.setTag(convertView, R.id.TAG_POSITION, position);
        // Giving the whole row an onClickListener seems to interfere with drag/drop.
        View details = convertView.findViewById(R.id.row_details);
        if (details == null) {
            details = convertView.findViewById(R.id.row);
        }
        if (details != null) {
            details.setOnClickListener(mRowClickListener);
            details.setFocusable(false);
        }

        // If the object is not null, do some processing
        if (object != null) {
            // Try to set position value
            if (mHasPosition || !mCheckedFields) {
                TextView pt = convertView.findViewById(R.id.ROW_POSITION);
                if (pt != null) {
                    mHasPosition = true;
                    String text = Integer.toString(position + 1);
                    pt.setText(text);
                }
            }

            // Try to set the UP handler
            if (mHasUp || !mCheckedFields) {
                ImageView up = convertView.findViewById(R.id.ROW_UP);
                if (up != null) {
                    up.setOnClickListener(mRowUpListener);
                    mHasUp = true;
                }
            }

            // Try to set the DOWN handler
            if (mHasDown || !mCheckedFields) {
                ImageView dn = convertView.findViewById(R.id.ROW_DOWN);
                if (dn != null) {
                    dn.setOnClickListener(mRowDownListener);
                    mHasDown = true;
                }
            }

            // Try to set the DELETE handler
            if (mHasDelete || !mCheckedFields) {
                ImageView del = convertView.findViewById(R.id.ROW_DELETE);
                if (del != null) {
                    del.setOnClickListener(mRowDeleteListener);
                    mHasDelete = true;
                }
            }

            // Ask the subclass to set other fields.
            try {
                onSetupView(convertView, object, position);
            } catch (Exception e) {
                Logger.logError(e);
            }
            convertView.setBackgroundResource(android.R.drawable.list_selector_background);

            mCheckedFields = true;
        }
        return convertView;
    }

    /**
     * Find the first ancestor that has the ID R.id.row. This
     * will be the complete row View. Use the TAG on that to get
     * the physical row number.
     *
     * @param v View to search from
     *
     * @return The row view.
     */
    private Integer getViewRow(View v) {
        View pv = v;
        while (pv.getId() != R.id.row) {
            ViewParent p = pv.getParent();
            if (!(p instanceof View))
                throw new RuntimeException("Could not find row view in view ancestors");
            pv = (View) p;
        }
        Object o = ViewTagger.getTag(pv, R.id.TAG_POSITION);
        if (o == null)
            throw new RuntimeException("A view with the tag R.id.row was found, but it is not the view for the row");
        return (Integer) o;
    }

    /**
     * Interface to allow underlying objects to determine their view ID.
     */
    public interface ViewProvider {
        int getViewId();
    }

}