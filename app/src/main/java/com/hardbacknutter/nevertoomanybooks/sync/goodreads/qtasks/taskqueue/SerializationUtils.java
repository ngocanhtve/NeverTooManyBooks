/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue;

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
final class SerializationUtils {

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
    static byte[] serializeObject(@NonNull final Serializable o)
            throws IllegalStateException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        } catch (@NonNull final IOException e) {
            // We should never see an IOException unless the developer made a boo-boo
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
    static <T> T deserializeObject(@NonNull final byte[] obj)
            throws DeserializationException {
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(obj))) {
            return (T) is.readObject();
        } catch (@NonNull final ClassCastException | ClassNotFoundException | IOException e) {
            throw new DeserializationException(e);
        }
    }

    /**
     * Catchall class for errors in serialization.
     */
    static class DeserializationException
            extends Exception {

        private static final long serialVersionUID = -2040548134317746620L;

        DeserializationException(@Nullable final Exception e) {
            initCause(e);
        }
    }
}
