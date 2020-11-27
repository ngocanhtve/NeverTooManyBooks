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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.db.DbArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.JsonArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveReader;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class ImportHelper {

    public static final String IMPORT_NOT_SUPPORTED = "Type not supported here";

    /**
     * Options as to what should be imported.
     * Not all implementations will support all options.
     * <p>
     * The bit numbers are not stored and can be changed.
     */
    public static final int OPTIONS_NOTHING = 0;
    public static final int OPTIONS_INFO = 1;
    public static final int OPTIONS_PREFS = 1 << 1;
    public static final int OPTIONS_STYLES = 1 << 2;
    public static final int OPTIONS_BOOKS = 1 << 6;
    public static final int OPTIONS_COVERS = 1 << 7;
    /**
     * New Books are always imported (if {@link #OPTIONS_BOOKS} is set obviously).
     * <p>
     * Existing books handling:
     * <ul>
     *     <li>00: skip entirely, keep the current data.</li>
     *     <li>01: overwrite current data with incoming data.</li>
     *     <li>10: [invalid combination]</li>
     *     <li>11: check the "update_date" field and only import newer data.</li>
     * </ul>
     */
    public static final int OPTIONS_UPDATED_BOOKS = 1 << 16;
    public static final int OPTIONS_UPDATED_BOOKS_SYNC = 1 << 17;

    /** All entity types. This does not include INFO nor the sync options. */
    public static final int OPTIONS_ENTITIES = OPTIONS_PREFS | OPTIONS_STYLES
                                               | OPTIONS_BOOKS | OPTIONS_COVERS;

    /** Picked by the user; where we read from. */
    @NonNull
    private final Uri mUri;
    /** Constructed from the Uri. */
    @Nullable
    private ArchiveContainer mArchiveContainer;
    @Nullable
    private ArchiveInfo mArchiveInfo;
    /**
     * Bitmask.
     * Contains the user selected options before doing the import.
     * After the import, reflects the entities actually imported.
     */
    @Options
    private int mOptions;

    /**
     * Constructor.
     *
     * @param uri to read from
     */
    public ImportHelper(@NonNull final Uri uri) {
        mUri = uri;
    }


    /**
     * Check if we have an {@link ArchiveReader} available that can read the passed Uri.
     *
     * @param context Current context
     *
     * @return {@code true} if supported
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSupported(@NonNull final Context context) {
        switch (getContainer(context)) {
            case Csv:
            case Zip:
            case Tar:
            case Json:
            case SqLiteDb:
                return true;

            case Xml:
            case Unknown:
            default:
                return false;
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
     * Get the archive container as read from the uri.
     *
     * @param context Current context
     *
     * @return container
     */
    @NonNull
    public ArchiveContainer getContainer(@NonNull final Context context) {
        if (mArchiveContainer == null) {
            mArchiveContainer = getContainer(context, mUri);
        }
        return mArchiveContainer;
    }

    /**
     * Guess/Create an {@link ArchiveContainer} based on the type of the input Uri.
     *
     * @param context Current context
     * @param uri     to read
     *
     * @return ArchiveContainer
     */
    private ArchiveContainer getContainer(@NonNull final Context context,
                                          @NonNull final Uri uri) {

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                // read the "magic bytes": https://en.wikipedia.org/wiki/List_of_file_signatures
                final byte[] b = new byte[0x200];
                final int len = is.read(b);

                // zip file, offset 0, "PK{3}{4}"
                if (len > 4
                    && b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04) {
                    return ArchiveContainer.Zip;
                }

                // xml file, offset 0, the string "<?xml "
                if (len > 5
                    && b[0] == 0x3c && b[1] == 0x3f && b[2] == 0x78 && b[3] == 0x6d
                    && b[4] == 0x6c && b[5] == 0x20) {
                    return ArchiveContainer.Xml;
                }

                // tar file: offset 0x101, the string "ustar"
                if (len > 0x110
                    && b[0x101] == 0x75 && b[0x102] == 0x73 && b[0x103] == 0x74
                    && b[0x104] == 0x61 && b[0x105] == 0x72) {
                    return ArchiveContainer.Tar;
                }

                // sqlite v3, offset 0, 53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00
                // the string "SQLite format 3"
                if (len > 16
                    && b[0] == 0x53 && b[1] == 0x51 && b[2] == 0x4c && b[3] == 0x69
                    && b[4] == 0x74 && b[5] == 0x65 && b[6] == 0x20 && b[7] == 0x66
                    && b[8] == 0x6f && b[9] == 0x72 && b[10] == 0x6d && b[11] == 0x61
                    && b[12] == 0x74 && b[13] == 0x20 && b[14] == 0x33 && b[15] == 0x00) {
                    return ArchiveContainer.SqLiteDb;
                }
            }
        } catch (@NonNull final IOException ignore) {
            // ignore
        }

        // If the magic bytes check did not work out, we look at the extension.
        // Allow some name variations:"file.ext", "file.ext (1)", "file.ext (2)" etc
        final Pair<String, Long> uriInfo = FileUtils.getUriInfo(context, uri);
        if (uriInfo != null && uriInfo.first != null) {
            Pattern pattern = Pattern.compile("^.*\\.csv( \\(\\d+\\))?$",
                                              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (pattern.matcher(uriInfo.first).find()) {
                return ArchiveContainer.Csv;
            }

            pattern = Pattern.compile("^.*\\.json( \\(\\d+\\))?$",
                                      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (pattern.matcher(uriInfo.first).find()) {
                return ArchiveContainer.Json;
            }
        }
        // give up.
        return ArchiveContainer.Unknown;
    }

    // TODO: split this up into one check for each entity we could import.
    public boolean isBooksOnlyContainer(@NonNull final Context context) {
        final ArchiveContainer container = getContainer(context);
        return ArchiveContainer.Csv == container
               || ArchiveContainer.Json == container
               || ArchiveContainer.SqLiteDb == container;
    }

    /**
     * Get the {@link ArchiveInfo}.
     * <p>
     * This allows us to show the info contained to the user without starting an actual import.
     *
     * @param context Current context
     *
     * @return the info bundle, or {@code null} if the archive does not provide info
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    ArchiveInfo getArchiveInfo(@NonNull final Context context)
            throws InvalidArchiveException, IOException {
        if (mArchiveInfo == null) {
            try (ArchiveReader reader = getArchiveReader(context)) {
                mArchiveInfo = reader.readArchiveInfo(context);
            }
        }
        return mArchiveInfo;
    }

    /**
     * Get the archive creation date.
     *
     * @param context Current context
     *
     * @return the date, or {@code null} if none present
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @Nullable
    public LocalDateTime getArchiveCreationDate(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        final ArchiveInfo info = getArchiveInfo(context);
        if (info == null) {
            return null;
        } else {
            return info.getCreationDate(context);
        }
    }

    /**
     * Create an {@link ArchiveReader} for the specified Uri.
     *
     * @param context Current context
     *
     * @return a new reader
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    public ArchiveReader getArchiveReader(@NonNull final Context context)
            throws InvalidArchiveException, IOException {

        SanityCheck.requirePositiveValue(mOptions & (OPTIONS_ENTITIES
                                                     | OPTIONS_UPDATED_BOOKS
                                                     | OPTIONS_UPDATED_BOOKS_SYNC),
                                         "mOptions");

        final ArchiveReader reader;
        switch (getContainer(context)) {
            case Zip:
                reader = new ZipArchiveReader(context, this);
                break;

            case Tar:
                reader = new TarArchiveReader(context, this);
                break;

            case Csv:
                reader = new CsvArchiveReader(this);
                break;

            case SqLiteDb:
                reader = new DbArchiveReader(context, this);
                break;

            case Json:
                reader = new JsonArchiveReader(this);
                break;

            case Xml:
            case Unknown:
            default:
                throw new InvalidArchiveException(IMPORT_NOT_SUPPORTED);
        }

        reader.validate(context);
        return reader;
    }

    public boolean isSkipUpdatedBooks() {
        return !isOptionSet(OPTIONS_UPDATED_BOOKS | OPTIONS_UPDATED_BOOKS_SYNC);
    }

    public void setSkipUpdatedBooks() {
        setOption(OPTIONS_UPDATED_BOOKS | OPTIONS_UPDATED_BOOKS_SYNC, false);
    }

    public boolean isOverwriteUpdatedBook() {
        return isOptionSet(OPTIONS_UPDATED_BOOKS) && !isOptionSet(OPTIONS_UPDATED_BOOKS_SYNC);
    }

    public void setOverwriteUpdatedBook() {
        setOption(OPTIONS_UPDATED_BOOKS, true);
        setOption(OPTIONS_UPDATED_BOOKS_SYNC, false);
    }

    public boolean isSyncUpdatedBooks() {
        return isOptionSet(OPTIONS_UPDATED_BOOKS | OPTIONS_UPDATED_BOOKS_SYNC);
    }

    public void setSyncUpdatedBooks() {
        setOption(OPTIONS_UPDATED_BOOKS | OPTIONS_UPDATED_BOOKS_SYNC, true);
    }

    public boolean isOptionSet(@Options final int optionBit) {
        return (mOptions & optionBit) != 0;
    }

    @Options
    public int getOptions() {
        return mOptions;
    }

    /**
     * Should be called <strong>before</strong> the import to indicate what should be imported.
     *
     * @param options flags
     */
    public void setOptions(@Options final int options) {
        mOptions = options;
    }

    /**
     * Called from the dialog via its View listeners.
     *
     * @param optionBit bit or combination of bits
     * @param isSet     bit value
     */
    public void setOption(@Options final int optionBit,
                          final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    /**
     * Check if there any options set that will cause us to import anything.
     *
     * @return {@code true} if something will be imported
     */
    public boolean hasEntityOption() {
        return (mOptions & OPTIONS_ENTITIES) != 0;
    }

    @Override
    @NonNull
    public String toString() {
        final StringJoiner sj = new StringJoiner(",", "Options{", "}");
        if ((mOptions & OPTIONS_INFO) != 0) {
            sj.add("INFO");
        }
        if ((mOptions & OPTIONS_PREFS) != 0) {
            sj.add("PREFS");
        }
        if ((mOptions & OPTIONS_STYLES) != 0) {
            sj.add("STYLES");
        }
        if ((mOptions & OPTIONS_BOOKS) != 0) {
            sj.add("BOOKS");
        }
        if ((mOptions & OPTIONS_COVERS) != 0) {
            sj.add("COVERS");
        }

        if ((mOptions & OPTIONS_UPDATED_BOOKS) != 0) {
            sj.add("UPDATED_BOOKS");
        }
        if ((mOptions & OPTIONS_UPDATED_BOOKS_SYNC) != 0) {
            sj.add("UPDATED_BOOKS_SYNC");
        }

        return "ImportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mOptions=" + sj.toString()
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mArchiveInfo=" + mArchiveInfo
               + '}';
    }

    @IntDef(flag = true, value = {OPTIONS_INFO, OPTIONS_PREFS, OPTIONS_STYLES,
                                  OPTIONS_BOOKS, OPTIONS_COVERS,
                                  OPTIONS_UPDATED_BOOKS, OPTIONS_UPDATED_BOOKS_SYNC})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Options {

    }
}
