/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.tararchive;

import com.eleybourn.bookcatalogue.backup.archivebase.BackupContainer;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupReader;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupWriter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

/**
 * Class to handle TAR archive storage.
 *
 * TAR files have some limitations: no application-defined metadata can be stored with the files, the
 * index is at the start, so it helps to know the entity size before it is written, and they usually
 * do not support random access.
 *
 * So we:
 *
 * - use "file names" to encode special meaning (eg. "books*.csv" is always an export file).
 * - use intermediate temp files so we can figure out sizes
 *
 * {@link #getVersion()}
 * #1: original code, used serialized styles and flat xml files for info/prefs
 *
 * #2: writes new xml format supporting lists of elements, styles are xml as wel now.
 * Can still read #1 archives
 *
 * @author pjw
 */
public class TarBackupContainer implements BackupContainer {

    /** Buffer size for buffered streams. */
    public static final int BUFFER_SIZE = 32768;

    /** archives are written in this version. */
    private static final int VERSION_WRITTEN = 2;
    /** we can still read archives from this version and up to our current version */
    private static final int VERSION_READ = 1;

    /** Always first entry; Used in the storage and identification of data store in TAR file. */
    static final String INFO_FILE = "INFO.xml";
    /** Used in the storage and identification of data store in TAR file. */
    static final Pattern INFO_PATTERN =
            Pattern.compile("^INFO_.*\\.xml$", Pattern.CASE_INSENSITIVE);

    /** Used in the storage and identification of data store in TAR file. */
    static final String BOOKS_FILE = "books.csv";
    /** Used in the storage and identification of data store in TAR file */
    static final Pattern BOOKS_PATTERN =
            Pattern.compile("^books_.*\\.csv$", Pattern.CASE_INSENSITIVE);

    /** Used in the storage and identification of data store in TAR file. */
    static final String DB_FILE = "snapshot.db";
    /** Used in the storage and identification of data store in TAR file. */
    static final String PREFERENCES = "preferences.xml";
    /** Used in the storage and identification of data store in TAR file. */
    static final String STYLES = "styles.xml";
    /**
     * archive entry that will contain xml dumps of actual tables.
     * For now, this is export only, cannot import yet.
     * Meant for those who want to experiment with the data on a desktop/server
     * without the need to parse csv strings
     */
    static final String XML_DATA = "data.xml";


    /** Backup file spec. */
    @NonNull
    private final File mFile;

    /**
     * Constructor.
     */
    public TarBackupContainer(@NonNull final File file) {
        mFile = file;
    }

    @NonNull
    public File getFile() {
        return mFile;
    }

    @Override
    @NonNull
    public BackupReader newReader()
            throws IOException {
        return new TarBackupReader(this);
    }

    @Override
    @NonNull
    public BackupWriter newWriter()
            throws IOException {
        return new TarBackupWriter(this);
    }

    /**
     * We always write the latest version archives (no backwards compatibility).
     */
    @Override
    public int getVersion() {
        return VERSION_WRITTEN;
    }

    /**
     * We can still read this older version.
     */
    @Override
    public int canReadVersion() {
        return VERSION_READ;
    }

    /**
     * @return <tt>true</tt> if valid
     */
    @Override
    public boolean isValid() {
        // The reader will do basic validation.
        try (BackupReader reader = new TarBackupReader(this)) {
            BackupInfo backupInfo = reader.getInfo();
            // the info block will/can do more checks.
            return backupInfo.isValid();
        } catch (IOException e) {
            Logger.error(e);
        }
        return false;
    }
}
