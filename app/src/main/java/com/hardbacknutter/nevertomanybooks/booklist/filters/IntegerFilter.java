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
package com.hardbacknutter.nevertomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;

/**
 * Not directly used for now.
 * Split of from {@link BooleanFilter} making the later a subclass of this one.
 */
public class IntegerFilter
        extends PInteger
        implements Filter<Integer> {

    public static final Integer P_NOT_USED = -1;

    @StringRes
    private final int mLabelId;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    IntegerFilter(@StringRes final int labelId,
                  @NonNull final String key,
                  @NonNull final String uuid,
                  final boolean isPersistent,
                  @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                  @NonNull final DomainDefinition domain) {
        super(key, uuid, isPersistent, P_NOT_USED);
        mLabelId = labelId;
        mTable = table;
        mDomain = domain;
    }

    /**
     * @return Filter expression, or {@code null} if not active
     */
    @Override
    @Nullable
    public String getExpression() {
        Integer value = get();
        if (!P_NOT_USED.equals(value)) {
            return mTable.dot(mDomain) + '=' + value;
        }
        return null;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    /**
     * syntax sugar.
     *
     * @return {@code true} if this filter is active
     */
    @Override
    public boolean isActive() {
        return !P_NOT_USED.equals(get());
    }

    @Override
    @NonNull
    public String toString() {
        return "IntegerFilter{"
               + "mTable=" + mTable
               + ", mDomain=" + mDomain
               + ", mLabelId=" + mLabelId
               + ", " + super.toString()
               + "}\n";
    }

}
