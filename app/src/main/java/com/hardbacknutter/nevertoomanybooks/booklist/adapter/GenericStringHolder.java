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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * ViewHolder to handle any field that can be displayed as a string.
 * <p>
 * Assumes there is a 'name' TextView.
 */
public class GenericStringHolder
        extends RowViewHolder<DataHolder> {

    private static final int[] TEXT_APP_ATTR = {
            com.google.android.material.R.attr.textAppearanceTitleLarge,
            com.google.android.material.R.attr.textAppearanceTitleMedium,
            com.google.android.material.R.attr.textAppearanceTitleSmall};
    /**
     * The group this holder represents.
     * It's ok to store this as it's intrinsically linked with the ViewType.
     */
    @BooklistGroup.Id
    private final int groupId;
    /*** View to populate. */
    @NonNull
    private final TextView textView;
    @NonNull
    private final FormatFunction formatter;
    /**
     * Key of the related data column.
     * It's ok to store this as it's intrinsically linked with the BooklistGroup.
     */
    @NonNull
    private final String key;

    /** Only set/used when running in BuildConfig.DEBUG. */
    @Nullable
    private DebugPosition debugPosition;

    /**
     * Constructor.
     *
     * @param level    the level in the Booklist tree
     * @param itemView the view specific for this holder
     * @param group    the group this holder represents
     */
    GenericStringHolder(@IntRange(from = 1) final int level,
                        @NonNull final View itemView,
                        @NonNull final BooklistGroup group,
                        @NonNull final FormatFunction formatter) {
        super(itemView);

        groupId = group.getId();
        key = group.getDisplayDomainExpression().getDomain().getName();
        textView = itemView.findViewById(R.id.level_text);
        this.formatter = formatter;

        final Context context = textView.getContext();

        textView.setTextAppearance(AttrUtils.getResId(
                context, TEXT_APP_ATTR[getLevelMultiplier(level)]));

        textView.setTypeface(null, Typeface.BOLD);

        drawBullet(context, level);
    }

    private static int getLevelMultiplier(@IntRange(from = 1) final int level) {
        return MathUtils.clamp(level - 1, 0, 2);
    }

    void setDebugPosition(@Nullable final DebugPosition debugPosition) {
        this.debugPosition = debugPosition;
    }

    /**
     * Optionally draw a bullet before the text on a level.
     * The size of the bullet depends on the level.
     *
     * @param context Current context
     * @param level   the level in the Booklist tree
     */
    private void drawBullet(@NonNull final Context context,
                            @IntRange(from = 1) final int level) {
        final Resources res = context.getResources();
        final int size;
        final TypedArray ta = res.obtainTypedArray(R.array.bob_group_level_bullet_size);
        try {
            size = ta.getDimensionPixelSize(getLevelMultiplier(level), 0);
        } finally {
            ta.recycle();
        }

        if (size > 0) {
            @SuppressLint("UseCompatLoadingForDrawables")
            final Drawable bullet = context.getDrawable(R.drawable.ic_baseline_lens_24);
            bullet.setBounds(0, 0, size, size);
            textView.setCompoundDrawablePadding(
                    res.getDimensionPixelSize(R.dimen.bob_group_level_bullet_padding));
            textView.setCompoundDrawablesRelative(bullet, null, null, null);
        }
    }

    @Override
    public void onBindViewHolder(final int position,
                                 @NonNull final DataHolder rowData,
                                 @NonNull final Style style) {
        textView.setText(formatter.format(groupId, rowData, key));

        if (BuildConfig.DEBUG) {
            dbgPosition(position, rowData);
        }
    }

    private void dbgPosition(final int position,
                             @NonNull final DataHolder rowData) {
        // Debugger help: color the row according to state
        if (DEBUG_SWITCHES.BOB_NODE_STATE) {
            //noinspection ConstantConditions
            itemView.setBackgroundColor(
                    debugPosition.getDbgRowColor(rowData.getInt(DBKey.PK_ID)));
        }

        if (DEBUG_SWITCHES.BOB_NODE_POSITIONS) {
            final String dbgText = " " + position + '/'
                                   + rowData.getLong(DBKey.BL_LIST_VIEW_NODE_ROW_ID);

            // just hang it of the existing text view.
            final CharSequence text = textView.getText();
            final SpannableString dbg = new SpannableString(text + dbgText);
            dbg.setSpan(new ForegroundColorSpan(Color.BLUE), text.length(), dbg.length(),
                        0);
            dbg.setSpan(new RelativeSizeSpan(0.7f), text.length(), dbg.length(), 0);

            textView.setText(dbg);
        }
    }
}
