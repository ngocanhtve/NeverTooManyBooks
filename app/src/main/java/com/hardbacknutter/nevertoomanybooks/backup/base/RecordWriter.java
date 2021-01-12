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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

public interface RecordWriter
        extends Closeable {

    /** Buffer for the Writer. */
    int BUFFER_SIZE = 65535;

    /**
     * Get the format version that this RecordWriter is writing out.
     *
     * @return the version
     */
    @AnyThread
    int getVersion();

    /**
     * Write a {@link RecordType#MetaData} record.
     *
     * @param writer   Writer to write to
     * @param metaData the bundle of information to write
     *
     * @throws IOException on failure
     */
    @WorkerThread
    default void writeMetaData(@NonNull final Writer writer,
                               @NonNull final ArchiveMetaData metaData)
            throws GeneralParsingException, IOException {
        // do nothing
    }

    /**
     * Write a set of {@link RecordType} records.
     * Unsupported record types should/will be silently skipped.
     *
     * @param context          Current context
     * @param writer           Writer to write to
     * @param entries          The set of entries which should be written.
     * @param progressListener Progress and cancellation interface
     *
     * @return {@link ExportResults}
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    ExportResults write(@NonNull Context context,
                        @NonNull Writer writer,
                        @NonNull Set<RecordType> entries,
                        @NonNull ProgressListener progressListener)
            throws GeneralParsingException, IOException;


    /**
     * Override if the implementation needs to close something.
     */
    @Override
    default void close() {
        // do nothing
    }
}
