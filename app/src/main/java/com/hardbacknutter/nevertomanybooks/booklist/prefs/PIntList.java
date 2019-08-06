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
package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@code List<Integer>} is stored as a CSV String.
 * <p>
 * No equivalent Preference widget
 */
public class PIntList
        extends PCollectionBase<Integer, List<Integer>> {

    /**
     * Constructor.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     */
    protected PIntList(@NonNull final String key,
                       @NonNull final String uuid,
                       final boolean isPersistent) {
        super(key, uuid, isPersistent, new ArrayList<>());
        mNonPersistedValue = new ArrayList<>();
    }

    @Override
    public void set(@NonNull final Parcel in) {
        List<Integer> tmp = new ArrayList<>();
        in.readList(tmp, getClass().getClassLoader());
        set(tmp);
    }

    @Override
    @NonNull
    public String toString() {
        return "PIntList{" + super.toString() + '}'
               + ", value=`" + get() + '`';
    }

    @NonNull
    @Override
    public List<Integer> get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            String values = getPrefs().getString(getKey(), null);
            if (values == null || values.isEmpty()) {
                return mDefaultValue;
            }

            return getAsList(values);
        }
    }

    @NonNull
    private List<Integer> getAsList(@NonNull final String values) {
        List<Integer> list = new ArrayList<>();
        for (String s : values.split(DELIM)) {
            list.add(Integer.parseInt(s));
        }
        return list;
    }
}
