package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * A String is stored as a String.
 * <p>
 * Used for {@link androidx.preference.EditTextPreference}
 */
public class PString
        extends PPrefBase<String> {

    /**
     * Constructor. Uses the global setting as the default value, or "" if none.
     *
     * @param key  of the preference
     * @param uuid the style id
     */
    public PString(@NonNull final String key,
                   @NonNull final String uuid,
                   final boolean isPersistent) {
        super(key, uuid, isPersistent, App.getPrefString(key));
    }

    @NonNull
    @Override
    public String get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // guard against the pref being there, but with value null.
            String tmp = getPrefs().getString(getKey(), mDefaultValue);
            return tmp != null ? tmp : mDefaultValue;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PString{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}
