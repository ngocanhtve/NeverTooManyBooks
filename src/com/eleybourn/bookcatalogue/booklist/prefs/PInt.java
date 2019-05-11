package com.eleybourn.bookcatalogue.booklist.prefs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Allows us to cast any {@code PPref<Integer>} as needed.
 */
public interface PInt {

    void set(@Nullable Integer value);

    @NonNull
    Integer get();
}
