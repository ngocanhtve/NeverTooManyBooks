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
package com.hardbacknutter.nevertomanybooks.datamanager.validators;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertomanybooks.backup.FormattedMessageException;

/**
 * Exception class for all validation errors. String ID and args are stored for later retrieval.
 * <p>
 * The messages will be shown to the user, hence the need for a String resource.
 */
public class ValidatorException
        extends RuntimeException
        implements FormattedMessageException {

    private static final long serialVersionUID = 171094428181491962L;
    @StringRes
    private final int mStringId;
    /** Args to pass to format function. */
    @NonNull
    private final Object[] mArgs;

    public ValidatorException(@StringRes final int stringId,
                              @NonNull final Object... args) {
        mStringId = stringId;
        mArgs = args;
    }

    @NonNull
    public String getFormattedMessage(@NonNull final Context context) {
        return context.getString(mStringId, mArgs);
    }
}
