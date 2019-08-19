/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.utils.SerializationUtils.DeserializationException;

/**
 * Interface provided by every entity read from a backup file.
 */
public interface ReaderEntity {

    /**
     * Get the original "file name" (archive entry name) of the object.
     *
     * @return name
     */
    @NonNull
    String getName();

    /**
     * Get the type of this entity.
     *
     * @return Type
     */
    @NonNull
    Type getType();

    /**
     * Get the Modified date from archive entry.
     *
     * @return Date
     */
    @NonNull
    Date getDateModified();

    /**
     * Get the stream to read the entity.
     *
     * @return the InputStream
     *
     * @throws IOException on failure
     */
    @NonNull
    InputStream getInputStream()
            throws IOException;

    /**
     * Save the data to a directory, using the original file name.
     *
     * @param name of the directory to save to.
     *
     * @throws IOException on failure
     */
    void saveToDirectory(@NonNull File name)
            throws IOException;

    /**
     * Get the data as a Serializable object.
     *
     * @return the object
     *
     * @throws IOException              on failure
     * @throws DeserializationException on failure
     */
    @NonNull
    <T extends Serializable> T getSerializable()
            throws IOException, DeserializationException;

    /** Supported entity types. */
    enum Type {
        Books,
        Info,
        Database,
        Preferences,
        BooklistStyles,
        Cover,
        XML,
        Unknown,

        PreferencesPreV200,
        BooklistStylesPreV200,
    }
}
