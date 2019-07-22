package com.eleybourn.bookcatalogue.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Date;
import java.util.Objects;

public class ExportOptions
        implements Parcelable {

    /**
     * options as to *what* should be exported.
     */
    public static final int BOOK_CSV = 1;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int XML_TABLES = 1 << 4;
    //public static final int IMPORT_5 = 1 << 5;
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;
    public static final int DATABASE = 1 << 8;

    /** Options value to indicate ALL things should be exported. */
    public static final int ALL =
            PREFERENCES
                    | BOOK_LIST_STYLES
                    | COVERS
                    | BOOK_CSV
                    | XML_TABLES;

    /**
     * Options to indicate new books or books with more recent update_date
     * fields should be exported.
     * <p>
     * 0: all books
     * 1: books added/updated since {@link #dateFrom}.
     * If the latter is {@code null}, then since last backup.
     */
    public static final int EXPORT_SINCE = 1 << 16;

    /**
     * Nothing to backup.
     */
    public static final int NOTHING = 0;

    /**
     * all defined flags.
     */
    public static final int MASK = ALL | EXPORT_SINCE;
    public static final Creator<ExportOptions> CREATOR = new Creator<ExportOptions>() {
        @Override
        public ExportOptions createFromParcel(@NonNull final Parcel source) {
            return new ExportOptions(source);
        }

        @Override
        public ExportOptions[] newArray(final int size) {
            return new ExportOptions[size];
        }
    };
    /** file to export to. */
    @Nullable
    public File file;
    /**
     * bitmask for the options.
     */
    public int what = NOTHING;
    /**
     * EXPORT_SINCE.
     */
    @Nullable
    public Date dateFrom;

    public ExportOptions() {
    }

    /**
     * Constructor.
     *
     * @param file to export to
     */
    public ExportOptions(@NonNull final File file) {
        this.file = file;
    }

    /** {@link Parcelable}. */
    protected ExportOptions(@NonNull final Parcel in) {
        what = in.readInt();
        if (in.readInt() != 0) {
            file = new File(in.readString());
        }
        if (in.readInt() != 0) {
            dateFrom = new Date(in.readLong());
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(what);

        if (file != null) {
            // has file
            dest.writeInt(1);
            dest.writeString(file.getPath());
        } else {
            // no file
            dest.writeInt(0);
        }

        if (dateFrom != null) {
            // has date
            dest.writeInt(1);
            dest.writeLong(dateFrom.getTime());
        } else {
            // no date
            dest.writeInt(0);
        }
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    public void validate() {
        // if we want 'since', we *must* have a valid dateFrom
        if ((what & ExportOptions.EXPORT_SINCE) != 0) {
            Objects.requireNonNull(dateFrom, "Export Failed - 'dateFrom' is null");
        } else {
            // sanity check: we don't want 'since', so make sure fromDate is not set.
            dateFrom = null;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportOptions{"
                + "file=`" + file + '`'
                + ", what=0%" + Integer.toBinaryString(what)
                + ", dateFrom=" + dateFrom
                + '}';
    }
}
