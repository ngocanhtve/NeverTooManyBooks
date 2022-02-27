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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoReader;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoWriter;

/**
 * Note on mHasLastUpdateDateField / mSyncDateUserEditable:
 * It's debatable that we could just use mHasLastUpdateDateField for both meanings.
 */
public enum SyncServer
        implements Parcelable {

    /** A Calibre Content Server. */
    CalibreCS(R.string.lbl_calibre_content_server, true, true),
    /** StripInfo web site. */
    StripInfo(R.string.site_stripinfo_be, false, false);

    /** {@link Parcelable}. */
    public static final Creator<SyncServer> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncServer createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public SyncServer[] newArray(final int size) {
            return new SyncServer[size];
        }
    };

    public static final String ERROR_NO_READER_AVAILABLE = "No reader available";
    /* Log tag. */
    private static final String TAG = "SyncServer";
    /** The (optional) preset encoding to pass to export/import. */
    public static final String BKEY_SITE = TAG + ":encoding";
    @StringRes
    private final int mLabel;


    private final boolean mHasLastUpdateDateField;
    private final boolean mSyncDateIsUserEditable;


    /**
     * Constructor.
     *
     * @param label                  will be displayed to the user
     * @param hasLastUpdateDateField whether the server provides a 'last update' field we can use
     * @param syncDateUserEditable   whether the user can manually influence the sync date
     */
    SyncServer(@StringRes final int label,
               final boolean hasLastUpdateDateField,
               final boolean syncDateUserEditable) {
        mLabel = label;
        mHasLastUpdateDateField = hasLastUpdateDateField;
        mSyncDateIsUserEditable = syncDateUserEditable;
    }


    /** A short label. Used in drop down menus and similar. */
    @StringRes
    public int getLabel() {
        return mLabel;
    }

    public boolean isEnabled(@NonNull final SharedPreferences global) {
        switch (this) {
            case CalibreCS:
                return CalibreHandler.isSyncEnabled(global);
            case StripInfo:
                return StripInfoHandler.isSyncEnabled(global);

            default:
                throw new IllegalArgumentException();
        }
    }

    public boolean isSyncDateUserEditable() {
        return mSyncDateIsUserEditable;
    }

    /**
     * Check whether each book has a specific last-update date to
     * (help) sync it with the server/web site
     */
    public boolean hasLastUpdateDateField() {
        return mHasLastUpdateDateField;
    }

    /**
     * Create an {@link DataWriter} based on the type.
     *
     * @param context Current context
     * @param helper  writer configuration
     *
     * @return a new writer
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     */
    @NonNull
    public DataWriter<SyncWriterResults> createWriter(@NonNull final Context context,
                                                      @NonNull final SyncWriterHelper helper)
            throws CertificateException,
                   SSLException {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(helper.getRecordTypes(), "getRecordTypes");
        }

        switch (this) {
            case CalibreCS:
                return new CalibreContentServerWriter(context, helper);

            case StripInfo:
                return new StripInfoWriter(helper);

            default:
                throw new IllegalStateException(DataWriter.ERROR_NO_WRITER_AVAILABLE);
        }
    }

    /**
     * Create an {@link DataReader} based on the type.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @return a new reader
     *
     * @throws DataReaderException  on a decoding/parsing of data issue
     * @throws CertificateException on failures related to a user installed CA.
     * @throws SSLException         on secure connection failures
     * @throws IOException          on other failures
     */
    @NonNull
    @WorkerThread
    public DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull final Context context,
            @NonNull final SyncReaderHelper helper)
            throws DataReaderException,
                   CertificateException,
                   IOException {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(helper.getRecordTypes(), "getRecordTypes");
        }

        final DataReader<SyncReaderMetaData, ReaderResults> reader;
        switch (this) {
            case CalibreCS:
                reader = new CalibreContentServerReader(context, helper);
                break;

            case StripInfo:
                reader = new StripInfoReader(context, helper);
                break;

            default:
                throw new IllegalStateException(ERROR_NO_READER_AVAILABLE);
        }

        reader.validate(context);
        return reader;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(this.ordinal());
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncServer{"
               + "mLabel=" + mLabel
               + ", mHasLastUpdateDateField=" + mHasLastUpdateDateField
               + ", mSyncDateIsUserEditable=" + mSyncDateIsUserEditable
               + '}';
    }
}
