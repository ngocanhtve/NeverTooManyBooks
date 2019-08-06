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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import com.hardbacknutter.nevertomanybooks.utils.SerializationUtils;
import com.hardbacknutter.nevertomanybooks.utils.SerializationUtils.DeserializationException;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic ReaderEntity methods using
 * only a limited set of methods from the base interface.
 */
public abstract class ReaderEntityAbstract
        implements ReaderEntity {

    /** Buffer size for buffered streams. */
    private static final int BUFFER_SIZE = 32768;

    @NonNull
    private final BackupEntityType mType;

    protected ReaderEntityAbstract(@NonNull final BackupEntityType type) {
        mType = type;
    }

    /** Get the type of this entity. */
    @NonNull
    @Override
    public BackupEntityType getType() {
        return mType;
    }

    @Override
    public void saveToDirectory(@NonNull final File dir)
            throws IOException {
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory");
        }

        // Build the new File and save
        File destFile = new File(dir.getAbsoluteFile() + File.separator + getName());
        try {
            StorageUtils.copyFile(getStream(), BUFFER_SIZE, destFile);
        } finally {
            if (destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.setLastModified(getDateModified().getTime());
            }
        }
    }

    @NonNull
    @Override
    public <T extends Serializable> T getSerializable()
            throws IOException, DeserializationException {
        // Turn the input into a byte array
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final byte[] buffer = new byte[BUFFER_SIZE];

        while (true) {
            int cnt = getStream().read(buffer);
            if (cnt <= 0) {
                break;
            }
            out.write(buffer);
        }
        out.close();
        return SerializationUtils.deserializeObject(out.toByteArray());
    }
}
