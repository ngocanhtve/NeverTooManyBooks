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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

/**
 * Class to wrap events that can not be de-serialized so that a {@link EventsCursor} always
 * returns a valid {@link Event}.
 */
public class LegacyEvent
        extends Event<EventsCursor, LegacyViewHolder> {


    private static final long serialVersionUID = 585493755218719498L;

    /**
     * Constructor.
     */
    LegacyEvent(@NonNull final Context context) {
        super(context.getString(R.string.legacy_record));
    }

    @NonNull
    @Override
    public LegacyViewHolder onCreateViewHolder(@NonNull final LayoutInflater layoutInflater,
                                               @NonNull final ViewGroup parent) {
        return new LegacyViewHolder(parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull final LegacyViewHolder holder,
                                 @NonNull final EventsCursor invalid,
                                 @NonNull final DAO db) {
        Context context = holder.itemView.getContext();
        holder.tv1.setText(context.getString(R.string.legacy_record, getId()));
        holder.tv2.setText(context.getString(R.string.legacy_description));
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final DAO db) {

        menuItems.add(new ContextDialogItem(
                context.getString(R.string.gr_tq_menu_delete_event),
                () -> QueueManager.getQueueManager().deleteEvent(getId())));

    }

}
