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
package com.hardbacknutter.nevertomanybooks.backup.archivebase;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

import com.hardbacknutter.nevertomanybooks.utils.SerializationUtils.DeserializationException;

/**
 * Interface provided by every entity read from a backup file.
 */
public interface ReaderEntity {

    /**
     * @return the original "file name" (archive entry name) of the object.
     */
    @NonNull
    String getName();

    /**
     * @return the type of this entity.
     */
    @NonNull
    BackupEntityType getType();

    /**
     * @return the Modified date from archive entry.
     */
    @NonNull
    Date getDateModified();


    /**
     * @return the stream to read the entity.
     */
    @NonNull
    InputStream getStream();

    /** Save the data to a directory, using the original file name. */
    void saveToDirectory(@NonNull File dir)
            throws IOException;

    /**
     * @return the data as a Serializable object.
     */
    @NonNull
    <T extends Serializable> T getSerializable()
            throws IOException, DeserializationException;

    /** Supported entity types. */
    enum BackupEntityType {
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
