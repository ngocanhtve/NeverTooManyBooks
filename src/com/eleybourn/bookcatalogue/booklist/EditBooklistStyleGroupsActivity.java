/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.booklist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.EditBooklistStyleGroupsActivity.GroupWrapper;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.OnStartDragListener;

/**
 * Activity to edit the groups associated with a style (include/exclude and/or move up/down).
 *
 * @author Philip Warner
 */
public class EditBooklistStyleGroupsActivity
        extends EditObjectListActivity<GroupWrapper> {

    /** Preferences setup. */
    private static final String REQUEST_BKEY_STYLE = "Style";

    /** Copy of the style we are editing. */
    private BooklistStyle mStyle;

    /**
     * Constructor.
     */
    public EditBooklistStyleGroupsActivity() {
        super(null);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_style_edit_group_list;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Intent intent = getIntent();
        mStyle = intent.getParcelableExtra(REQUEST_BKEY_STYLE);
        Objects.requireNonNull(mStyle);

        // Init the subclass now that we have the style
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.name_colon_value,
                           getString(R.string.pg_groupings), mStyle.getLabel(this)));

        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(),
                                    R.string.hint_booklist_style_groups, null);
        }
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @NonNull
    @Override
    protected ArrayList<GroupWrapper> getList() {
        // Build an array list with the groups from the style
        ArrayList<GroupWrapper> groupWrappers = new ArrayList<>();
        for (BooklistGroup group : mStyle.getGroups()) {
            groupWrappers.add(new GroupWrapper(group, mStyle.getUuid(), true));
        }

        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        for (BooklistGroup group : BooklistGroup.getAllGroups(mStyle)) {
            if (!mStyle.hasGroupKind(group.getKind())) {
                groupWrappers.add(new GroupWrapper(group, mStyle.getUuid(), false));
            }
        }

        return groupWrappers;
    }

    /**
     * Adds the style to the resulting data
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        //Note: right now, we don't have any updated preferences other then the groups.
        // Save the properties of this style
        Map<String, PPref> allPreferences = mStyle.getPreferences(true);

        // Loop through ALL groups
        for (GroupWrapper wrapper : mList) {
            // Remove its kind from style
            mStyle.removeGroup(wrapper.group.getKind());
            // If required, add the group back; this also takes care of the order.
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }

        // Apply any saved properties.
        mStyle.updatePreferences(allPreferences);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debug(this, "onSave", mStyle);
        }

        data.putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
        setResult(Activity.RESULT_OK, data);
        return true;
    }

    protected RecyclerViewAdapterBase createListAdapter(@NonNull final ArrayList<GroupWrapper> list,
                                                        @NonNull final OnStartDragListener dragStartListener) {
        //        adapter.registerAdapterDataObserver(new SimpleAdapterDataObserver() {
//            @Override
//            public void onChanged() {
//            }
//        });
        return new GroupWrapperListAdapter(this, list, dragStartListener);
    }

    /**
     * We build a list of GroupWrappers which is passed to the underlying class for editing.
     * The wrapper includes extra details needed by this activity.
     */
    public static class GroupWrapper
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<GroupWrapper> CREATOR =
                new Creator<GroupWrapper>() {
                    @Override
                    public GroupWrapper createFromParcel(@NonNull final Parcel source) {
                        return new GroupWrapper(source);
                    }

                    @Override
                    public GroupWrapper[] newArray(final int size) {
                        return new GroupWrapper[size];
                    }
                };

        private static final long serialVersionUID = 3108094089675884238L;

        /** The actual group. */
        @NonNull
        final BooklistGroup group;
        @NonNull
        final String uuid;
        /** Whether this groups is present in the style. */
        boolean present;

        /** Constructor. */
        GroupWrapper(@NonNull final BooklistGroup group,
                     @NonNull final String uuid,
                     final boolean present) {
            this.group = group;
            this.uuid = uuid;
            this.present = present;
        }

        /** {@link Parcelable}. */
        GroupWrapper(@NonNull final Parcel in) {
            present = in.readByte() != 0;
            //noinspection ConstantConditions
            uuid = in.readString();
            //noinspection ConstantConditions
            group = BooklistGroup.newInstance(in.readInt(), uuid);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeByte((byte) (present ? 1 : 0));
            dest.writeString(uuid);
            dest.writeInt(group.getKind());
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView nameView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
        }
    }

    protected static class GroupWrapperListAdapter
            extends RecyclerViewAdapterBase<GroupWrapper, Holder> {

        GroupWrapperListAdapter(@NonNull final Context context,
                                @NonNull final ArrayList<GroupWrapper> items,
                                @NonNull final OnStartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_booklist_style, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            GroupWrapper groupWrapper = getItem(position);

            holder.nameView.setText(groupWrapper.group.getName(getContext()));

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(groupWrapper.present);

            holder.mCheckableButton.setOnClickListener(v -> {
                boolean newStatus = !groupWrapper.present;
                groupWrapper.present = newStatus;
                holder.mCheckableButton.setChecked(newStatus);
                notifyItemChanged(position);
            });
        }
    }
}
