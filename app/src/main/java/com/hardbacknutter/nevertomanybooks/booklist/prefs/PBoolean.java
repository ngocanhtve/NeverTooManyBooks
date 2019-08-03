package com.hardbacknutter.nevertomanybooks.booklist.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * A Boolean is stored as a Boolean.
 * <p>
 * Used for {@link androidx.preference.SwitchPreference}
 */
public class PBoolean
        extends PPrefBase<Boolean> {

    /**
     * Constructor. Uses the global setting as the default value, or false if none.
     *
     * @param key  of the preference
     * @param uuid the style id
     */
    public PBoolean(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent) {
        super(key, uuid, isPersistent, App.getPrefBoolean(key, false));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param defaultValue default to use if there is no global default
     */
    public PBoolean(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final Boolean defaultValue) {
        super(key, uuid, isPersistent, App.getPrefBoolean(key, defaultValue));
    }

    @Override
    public void set(@Nullable final Boolean value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            getPrefs().edit().putBoolean(getKey(), value).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Boolean value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putBoolean(getKey(), value);
        }
    }

    @NonNull
    @Override
    public Boolean get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            return getPrefs().getBoolean(getKey(), mDefaultValue);
        }
    }

    /**
     * syntax sugar...
     */
    public boolean isTrue() {
        return get();
    }

    /**
     * syntax sugar...
     */
    public boolean isFalse() {
        return !get();
    }

    @Override
    @NonNull
    public String toString() {
        return "PBoolean{" + super.toString()
                + ",value=`" + get() + '`'
                + '}';
    }
}
