/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.tar.TarArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.xml.XmlArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class ExportManager
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<ExportManager> CREATOR = new Creator<ExportManager>() {
        @Override
        public ExportManager createFromParcel(@NonNull final Parcel source) {
            return new ExportManager(source);
        }

        @Override
        public ExportManager[] newArray(final int size) {
            return new ExportManager[size];
        }
    };

    /**
     * Options to indicate new books or books with more recent
     * update_date fields should be exported.
     * <p>
     * 0: all books
     * 1: books added/updated since last backup.
     */
    static final int EXPORT_SINCE_LAST_BACKUP = 1 << 16;
    /**
     * all defined flags.
     */
    private static final int MASK = Options.ALL | EXPORT_SINCE_LAST_BACKUP;
    /** Log tag. */
    private static final String TAG = "ExportHelper";
    /** Write to this temp file first. */
    private static final String TEMP_FILE_NAME = TAG + ".tmp";
    /** Last full backup date. */
    private static final String PREF_LAST_FULL_BACKUP_DATE = "backup.last.date";
    /**
     * Bitmask.
     * Contains the user selected options before doing the import/export.
     * After the import/export, reflects the entities actually imported/exported.
     */
    private int mOptions;
    @Nullable
    private Uri mUri;
    @Nullable
    private ExportResults mResults;
    @Nullable
    private ArchiveContainer mArchiveContainer;
    /** EXPORT_SINCE_LAST_BACKUP. */
    @Nullable
    private Date mDateFrom;

    /**
     * Constructor.
     *
     * @param options to export
     */
    public ExportManager(final int options) {
        mOptions = options;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportManager(@NonNull final Parcel in) {
        mOptions = in.readInt();
        long dateLong = in.readLong();
        if (dateLong != 0) {
            mDateFrom = new Date(dateLong);
        }
        mUri = in.readParcelable(getClass().getClassLoader());
        mResults = in.readParcelable(getClass().getClassLoader());
    }

    /** Called from the dialog via its View listeners. */
    void setOption(final int optionBit,
                   final boolean isSet) {
        if (isSet) {
            mOptions |= optionBit;
        } else {
            mOptions &= ~optionBit;
        }
    }

    public int getOptions() {
        return mOptions;
    }

    /** Called <strong>after</strong> the export/import to report back what was handled. */
    public void setOptions(final int options) {
        mOptions = options;
    }

    @NonNull
    public ArchiveContainer getArchiveContainer() {
        if (mArchiveContainer == null) {
            // use the default
            return ArchiveContainer.Tar;
        }
        return mArchiveContainer;
    }

    void setArchiveContainer(@NonNull final ArchiveContainer archiveContainer) {
        mArchiveContainer = archiveContainer;
    }

    /**
     * Get the Uri for the user location to write to.
     *
     * @return Uri
     */
    @NonNull
    public Uri getUri() {
        Objects.requireNonNull(mUri, ErrorMsg.NULL_URI);
        return mUri;
    }

    public void setUri(@NonNull final Uri uri) {
        mUri = uri;
    }

    @Nullable
    public Date getDateSince() {
        return mDateFrom;
    }

    /**
     * Convenience method to return the date-from as a time {@code long}.
     * Returns 0 if the date is not set.
     *
     * @return time
     */
    long getTimeFrom() {
        if (mDateFrom != null && ((mOptions & EXPORT_SINCE_LAST_BACKUP) != 0)) {
            return mDateFrom.getTime();
        } else {
            return 0;
        }
    }

    /**
     * Create a BackupWriter for the specified Uri.
     *
     * @param context Current context
     *
     * @return a new writer
     *
     * @throws IOException on failure
     */
    public ArchiveWriter getArchiveWriter(@NonNull final Context context)
            throws IOException {

        //noinspection EnumSwitchStatementWhichMissesCases
        switch (getArchiveContainer()) {
            case Xml:
                return new XmlArchiveWriter(context, this);

            case Tar:
            default:
                // the default
                return new TarArchiveWriter(context, this);
        }
    }

    /**
     * Get the temporary File to write to.
     * When writing is done (success <strong>and</strong> failure),
     * {@link #onSuccess} / {@link #onFail} must be called as needed.
     *
     * @param context Current context
     *
     * @return File
     */
    @NonNull
    public File getTempOutputFile(@NonNull final Context context) {
        return AppDir.Cache.getFile(context, TEMP_FILE_NAME);
    }

    /**
     * Called by the export task before starting.
     */
    public void validate() {
        if ((mOptions & MASK) == 0) {
            throw new IllegalStateException("options not set");
        }
        if (mUri == null) {
            throw new IllegalStateException("Uri was NULL");
        }
        if ((mOptions & EXPORT_SINCE_LAST_BACKUP) != 0) {
            mDateFrom = getLastFullBackupDate(App.getAppContext());
        } else {
            mDateFrom = null;
        }
    }

    @NonNull
    public ExportResults getResults() {
        Objects.requireNonNull(mResults);
        return mResults;
    }

    public void setResults(@NonNull final ExportResults results) {
        mResults = results;
    }

    /**
     * Should be called after a successful write.
     *
     * @param context Current context
     *
     * @throws IOException on failure to write to the destination Uri
     */
    public void onSuccess(@NonNull final Context context)
            throws IOException {
        FileUtils.copy(context, AppDir.Cache.getFile(context, TEMP_FILE_NAME), getUri());
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILE_NAME));

        // if the backup was a full one (not a 'since') remember that.
        if ((mOptions & ExportManager.EXPORT_SINCE_LAST_BACKUP) != 0) {
            setLastFullBackupDate(context);
        }
    }

    /**
     * Should be called after a failed write.
     *
     * @param context Current context
     */
    public void onFail(@NonNull final Context context) {
        FileUtils.delete(AppDir.Cache.getFile(context, TEMP_FILE_NAME));
    }

    /**
     * Store the date of the last full backup ('now') and reset the startup prompt-counter.
     *
     * @param context Current context
     */
    private void setLastFullBackupDate(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(PREF_LAST_FULL_BACKUP_DATE,
                                    DateUtils.utcSqlDateTimeForToday())
                         .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                                 Prefs.STARTUP_BACKUP_COUNTDOWN)
                         .apply();
    }

    /**
     * Get the last time we made a full backup.
     *
     * @param context Current context
     *
     * @return Date in the UTC timezone.
     */
    @Nullable
    private Date getLastFullBackupDate(@NonNull final Context context) {
        String lastBackup = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PREF_LAST_FULL_BACKUP_DATE, null);

        if (lastBackup != null && !lastBackup.isEmpty()) {
            return DateUtils.parseSqlDateTime(lastBackup);
        }

        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(mOptions);
        if (mDateFrom != null) {
            dest.writeLong(mDateFrom.getTime());
        } else {
            dest.writeInt(0);
        }
        dest.writeParcelable(mUri, flags);
        dest.writeParcelable(mResults, flags);
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportHelper{"
               + ", mOptions=0b" + Integer.toBinaryString(mOptions)
               + ", mUri=" + mUri
               + ", mArchiveType=" + mArchiveContainer
               + ", mDateFrom=" + mDateFrom
               + ", mResults=" + mResults
               + '}';
    }
}
