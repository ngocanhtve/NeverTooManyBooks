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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.KeyManagementException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

public final class ImportHelper {

    /**
     * New Books/Covers are always imported
     * (if {@link RecordType#Books} is set obviously).
     * <p>
     * Existing Books/Covers handling:
     * <ul>
     *     <li>00: skip entirely, keep the current data.</li>
     *     <li>01: overwrite current data with incoming data.</li>
     *     <li>10: [invalid combination]</li>
     *     <li>11: check the "update_date" field and only import newer data.</li>
     * </ul>
     */
    public static final int OPTION_UPDATES_MAY_OVERWRITE = 1;
    public static final int OPTION_UPDATES_MUST_SYNC = 1 << 1;

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final Uri mUri;
    /** <strong>How</strong> to read from the Uri. */
    @NonNull
    private final ArchiveEncoding mEncoding;
    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mImportEntries = EnumSet.noneOf(RecordType.class);

    /** Bitmask.  Contains extra options for the {@link RecordReader}. */
    @Options
    private int mOptions;

    /**
     * Private constructor. Use the factory methods instead.
     *
     * @param uri      to read from
     * @param encoding which the uri uses
     */
    private ImportHelper(@NonNull final Uri uri,
                         @NonNull final ArchiveEncoding encoding) {
        mUri = uri;
        mEncoding = encoding;
        initWithDefault();
    }

    /**
     * Constructor using a generic Uri. The encoding will be determined from the Uri.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static ImportHelper withFile(@NonNull final Context context,
                                        @NonNull final Uri uri)
            throws FileNotFoundException, InvalidArchiveException {
        final ArchiveEncoding encoding = ArchiveEncoding.getEncoding(context, uri).orElseThrow(
                () -> new InvalidArchiveException(uri.toString()));

        return new ImportHelper(uri, encoding);
    }

    /**
     * Constructor for a Calibre content server.
     *
     * @param hostUrl  for a Calibre content server.
     * @param encoding the remote server to use
     */
    static ImportHelper withRemoteServer(@NonNull final String hostUrl,
                                         @SuppressWarnings("SameParameterValue")
                                         @NonNull final ArchiveEncoding encoding) {
        if (!encoding.isRemoteServer()) {
            throw new IllegalStateException("Not a remote server");
        }
        return new ImportHelper(Uri.parse(hostUrl), encoding);
    }

    private void initWithDefault() {
        mImportEntries.clear();
        mImportEntries.add(RecordType.MetaData);

        switch (mEncoding) {
            case Csv:
                // Default: new books and sync updates
                mImportEntries.add(RecordType.Books);
                setNewAndUpdatedBooks();
                break;

            case Zip:
            case Tar:
                // Default: update all entries and sync updates
                mImportEntries.add(RecordType.Styles);
                mImportEntries.add(RecordType.Preferences);
                mImportEntries.add(RecordType.Books);
                mImportEntries.add(RecordType.Cover);
                setNewAndUpdatedBooks();
                break;

            case SqLiteDb:
                // Default: new books only
                mImportEntries.add(RecordType.Books);
                setNewBooksOnly();
                break;

            case Json:
                mImportEntries.add(RecordType.Styles);
                mImportEntries.add(RecordType.Preferences);
                mImportEntries.add(RecordType.Books);
                setNewAndUpdatedBooks();
                break;

            case CalibreCS:
                mImportEntries.add(RecordType.Books);
                mImportEntries.add(RecordType.Cover);
                setNewAndUpdatedBooks();
                break;

            case Xml:
            default:
                break;
        }
    }

    /**
     * Get the Uri for the user location to read from.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        return mUri;
    }

    /**
     * Get the {@link FileUtils.UriInfo} of the archive (based on the Uri/Encoding).
     *
     * @param context Current context
     *
     * @return info
     */
    @NonNull
    public FileUtils.UriInfo getUriInfo(@NonNull final Context context) {
        if (mEncoding.isRemoteServer()) {
            final String displayName =
                    context.getString(mEncoding.getRemoteServerDescriptionResId());
            return new FileUtils.UriInfo(mUri, displayName, 0);
        } else {
            return FileUtils.getUriInfo(context, mUri);
        }
    }

    /**
     * Get the encoding of the source.
     *
     * @return encoding
     */
    @NonNull
    ArchiveEncoding getEncoding() {
        return mEncoding;
    }


    /**
     * Create an {@link ArchiveReader} based on the type.
     *
     * @param context Current context
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to produce a supported reader
     * @throws GeneralParsingException on a decoding/parsing of data issue
     * @throws IOException             on other failures
     */
    @NonNull
    @WorkerThread
    public ArchiveReader createArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException, GeneralParsingException,
                   IOException,
                   CertificateException, KeyManagementException {
        if (BuildConfig.DEBUG /* always */) {
            if (mImportEntries.isEmpty()) {
                throw new IllegalStateException("mImportEntries is empty");
            }
        }
        return mEncoding.createReader(context, this);
    }

    public void setImportEntry(@NonNull final RecordType recordType,
                               final boolean isSet) {
        if (isSet) {
            mImportEntries.add(recordType);
        } else {
            mImportEntries.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getImportEntries() {
        return mImportEntries;
    }


    @SuppressWarnings("WeakerAccess")
    public boolean isNewBooksOnly() {
        return (mOptions & (OPTION_UPDATES_MAY_OVERWRITE | OPTION_UPDATES_MUST_SYNC)) == 0;
    }

    @SuppressWarnings("WeakerAccess")
    public void setNewBooksOnly() {
        mOptions &= ~OPTION_UPDATES_MAY_OVERWRITE;
        mOptions &= ~OPTION_UPDATES_MUST_SYNC;
    }

    public boolean isAllBooks() {
        return (mOptions & OPTION_UPDATES_MAY_OVERWRITE) != 0
               && (mOptions & OPTION_UPDATES_MUST_SYNC) == 0;
    }

    public void setAllBooks() {
        mOptions |= OPTION_UPDATES_MAY_OVERWRITE;
        mOptions &= ~OPTION_UPDATES_MUST_SYNC;
    }

    public boolean isNewAndUpdatedBooks() {
        return (mOptions & (OPTION_UPDATES_MAY_OVERWRITE | OPTION_UPDATES_MUST_SYNC)) != 0;
    }

    @SuppressWarnings("WeakerAccess")
    public void setNewAndUpdatedBooks() {
        mOptions |= OPTION_UPDATES_MAY_OVERWRITE;
        mOptions |= OPTION_UPDATES_MUST_SYNC;
    }

    @Options
    public int getOptions() {
        return mOptions;
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner options = new StringJoiner(",", "[", "]");
        if ((mOptions & OPTION_UPDATES_MAY_OVERWRITE) != 0) {
            options.add("UPDATES_MAY_OVERWRITE");
        }
        if ((mOptions & OPTION_UPDATES_MUST_SYNC) != 0) {
            options.add("UPDATES_MUST_SYNC");
        }

        return "ImportHelper{"
               + "mUri=" + mUri
               + ", mArchiveEncoding=" + mEncoding
               + ", mImportEntries=" + mImportEntries
               + ", mOptions=0b" + Integer.toBinaryString(mOptions) + ": " + options.toString()
               + '}';
    }

    @IntDef(flag = true, value = {OPTION_UPDATES_MAY_OVERWRITE, OPTION_UPDATES_MUST_SYNC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Options {

    }
}
