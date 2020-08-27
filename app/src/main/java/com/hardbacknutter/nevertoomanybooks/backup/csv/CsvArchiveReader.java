/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * A minimal implementation of {@link ArchiveReader} which reads a plain CSV file with books.
 * {@link Options} are NOT supported for now (hardcoded to {@link Options#BOOKS} only).
 */
public class CsvArchiveReader
        implements ArchiveReader {

    /** import configuration. */
    @NonNull
    private final ImportManager mHelper;

    public CsvArchiveReader(@NonNull final ImportManager helper) {
        mHelper = helper;
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            // openInputStream can return null, just pretend we couldn't find the file.
            // Should never happen - flw
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        try (Importer importer = new CsvImporter(context, Options.BOOKS)) {
            final ReaderEntity entity = new CsvReaderEntity(is);
            return importer.read(context, entity, progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void validate(@NonNull final Context context) {
        // hope for the best
    }

    private static class CsvReaderEntity
            implements ReaderEntity {

        /** The entity source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param is InputStream to use
         */
        CsvReaderEntity(@NonNull final InputStream is) {
            mIs = is;
        }

        @NonNull
        @Override
        public String getName() {
            return ArchiveContainerEntry.BooksCsv.getName();
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return mIs;
        }

        @NonNull
        @Override
        public ArchiveContainerEntry getType() {
            return ArchiveContainerEntry.BooksCsv;
        }
    }
}
