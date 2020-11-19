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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public class FormattedDiacriticArrayAdapter
        extends DiacriticArrayAdapter<String> {

    /** The formatter to apply on each line item. */
    @Nullable
    private final FieldFormatter<String> mFormatter;

    /**
     * Constructor.
     *
     * @param context   Current context.
     * @param objects   The objects to represent in the list view
     * @param formatter to use
     */
    public FormattedDiacriticArrayAdapter(@NonNull final Context context,
                                          @NonNull final List<String> objects,
                                          @Nullable final FieldFormatter<String> formatter) {
        super(context, R.layout.dropdown_menu_popup_item, 0, objects);
        mFormatter = formatter;
    }

    @Nullable
    @Override
    public String getItem(final int position) {
        if (mFormatter != null) {
            return mFormatter.format(getContext(), super.getItem(position));
        } else {
            return super.getItem(position);
        }
    }
}
