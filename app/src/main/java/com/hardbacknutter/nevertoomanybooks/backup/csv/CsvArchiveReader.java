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
import androidx.annotation.VisibleForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * A minimal implementation of {@link ArchiveReader} which reads a plain CSV file with books.
 */
public class CsvArchiveReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CsvArchiveReader";

    /** Import configuration. */
    @NonNull
    private final ImportHelper mHelper;
    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /**
     * Constructor.
     *
     * @param helper import configuration
     */
    public CsvArchiveReader(@NonNull final ImportHelper helper) {
        mHelper = helper;
        mDb = new DAO(TAG);
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

        try (RecordReader recordReader = new CsvRecordReader(context, mDb)) {
            final ArchiveReaderRecord record =
                    new CsvArchiveRecord(mHelper.getArchiveName(context), is);

            return recordReader.read(context, record, mHelper.getOptions(), progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }

    @VisibleForTesting
    public static class CsvArchiveRecord
            implements ArchiveReaderRecord {

        @NonNull
        private final String mName;

        /** The record source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param name of this record
         * @param is   InputStream to use
         */
        CsvArchiveRecord(@NonNull final String name,
                         @NonNull final InputStream is) {
            mName = name;
            mIs = is;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return Optional.of(RecordType.Books);
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return Optional.of(RecordEncoding.Csv);
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
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
    }
}
