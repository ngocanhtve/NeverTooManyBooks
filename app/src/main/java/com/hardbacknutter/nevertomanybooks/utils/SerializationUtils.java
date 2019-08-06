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
package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Collection of methods to wrap common serialization routines.
 */
public final class SerializationUtils {

    private SerializationUtils() {
    }

    /**
     * Convert a Serializable object to a byte array.
     *
     * @param o Object to convert
     *
     * @return Resulting byte array.
     *
     * @throws IllegalStateException upon failure.
     */
    @NonNull
    public static byte[] serializeObject(@NonNull final Serializable o)
            throws IllegalStateException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        } catch (@NonNull final IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    /**
     * Deserialize the passed byte array.
     *
     * @param obj object to deserialize
     *
     * @return object
     *
     * @throws DeserializationException on failure
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> T deserializeObject(@NonNull final byte[] obj)
            throws DeserializationException {
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(obj))) {
            return (T) is.readObject();
        } catch (@NonNull final ClassCastException | ClassNotFoundException | IOException e) {
            throw new DeserializationException(e);
        }
    }

    /**
     * Serialize then de-serialize to create a deep clone.
     *
     * @param obj to clone
     *
     * @return the clone
     */
    @SuppressWarnings("unused")
    @NonNull
    public static <T extends Serializable> T cloneObject(@NonNull final T obj)
            throws DeserializationException {
        return deserializeObject(serializeObject(obj));
    }

    /**
     * Catchall class for errors in serialization.
     */
    public static class DeserializationException
            extends Exception {

        private static final long serialVersionUID = -2040548134317746620L;

        DeserializationException(@Nullable final Exception e) {
            initCause(e);
        }
    }
}
