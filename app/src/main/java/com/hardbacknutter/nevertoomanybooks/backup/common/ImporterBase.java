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
package com.hardbacknutter.nevertoomanybooks.backup.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * For better or worse... this class and it's children implementations
 * are passed around a lot, and hence thighly coupled.
 * The alternative was a lot of duplicate code and a LOT of individual parameter passing.
 *
 * @param <METADATA> the result object from a {@link #readMetaData(Context)}
 * @param <RESULTS>  the result object from a {@link #read(Context, ProgressListener)}
 */
public abstract class ImporterBase<METADATA, RESULTS> {

    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mRecordTypes;

    @Nullable
    private METADATA mMetaData;

    /**
     * <strong>How</strong> to handle existing books/covers.
     * New Books/Covers are always imported according to {@link #mRecordTypes}.
     */
    @NonNull
    private DataReader.Updates mUpdateOption = DataReader.Updates.Skip;

    public ImporterBase(@NonNull final Set<RecordType> defaultRecordTypes) {
        mRecordTypes = defaultRecordTypes;
    }

    public void addRecordType(@NonNull final RecordType... recordTypes) {
        mRecordTypes.addAll(Arrays.asList(recordTypes));
    }

    public void setRecordType(final boolean add,
                              @NonNull final RecordType recordType) {
        if (add) {
            mRecordTypes.add(recordType);
        } else {
            mRecordTypes.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getRecordTypes() {
        // Return a copy!
        return EnumSet.copyOf(mRecordTypes);
    }

    /**
     * Get the {@link DataReader.Updates} setting.
     *
     * @return setting
     */
    @NonNull
    public DataReader.Updates getUpdateOption() {
        return mUpdateOption;
    }

    public void setUpdateOption(@NonNull final DataReader.Updates updateOption) {
        mUpdateOption = updateOption;
    }

    @NonNull
    public Optional<METADATA> getMetaData() {
        if (mMetaData != null) {
            return Optional.of(mMetaData);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Create a new {@link DataReader} specific for the source from where we're importing.
     * <p>
     * It is the callers responsibility to close this reader.
     *
     * @param context Current context
     *
     * @return reader
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws IOException             on other failures
     */
    protected abstract DataReader<METADATA, RESULTS> createReader(@NonNull Context context)
            throws ImportException,
                   CertificateException,
                   CoverStorageException,
                   IOException,
                   InvalidArchiveException;

    /**
     * Read the {@link METADATA} object from the backup.
     *
     * @param context Current context
     *
     * @return Optional with {@link METADATA}
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws ImportException         on a decoding/parsing of data issue
     * @throws IOException             on other failures
     * @throws SSLException            on secure connection failures
     */
    @NonNull
    public Optional<METADATA> readMetaData(@NonNull final Context context)
            throws ImportException,
                   IOException,
                   CertificateException,
                   StorageException,
                   InvalidArchiveException {

        try (DataReader<METADATA, RESULTS> reader = createReader(context)) {
            final Optional<METADATA> metaData = reader.readMetaData(context);
            mMetaData = metaData.orElse(null);
            return metaData;
        }
    }

    /**
     * Perform a full read.
     *
     * @param context Current context
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws ImportException         on a decoding/parsing of data issue
     * @throws IOException             on other failures
     * @throws SSLException            on secure connection failures
     */
    @NonNull
    @WorkerThread
    public RESULTS read(@NonNull final Context context,
                        @NonNull final ProgressListener progressListener)
            throws ImportException,
                   InvalidArchiveException,
                   IOException,
                   StorageException,
                   CredentialsException, CertificateException {

        SanityCheck.requireValue(mRecordTypes, "mRecordTypes");

        try (DataReader<METADATA, RESULTS> reader = createReader(context)) {
            return reader.read(context, progressListener);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ImporterBase{"
               + "mRecordTypes=" + mRecordTypes
               + ", mUpdateOption=" + mUpdateOption
               + ", mMetaData=" + mMetaData
               + '}';
    }
}
