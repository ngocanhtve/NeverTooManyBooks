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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Value class to report back what was imported.
 * <p>
 * Note: failed = processed - created - updated
 */
public class SyncReaderResults
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SyncReaderResults> CREATOR = new Creator<SyncReaderResults>() {
        @Override
        public SyncReaderResults createFromParcel(@NonNull final Parcel in) {
            return new SyncReaderResults(in);
        }

        @Override
        public SyncReaderResults[] newArray(final int size) {
            return new SyncReaderResults[size];
        }
    };
    /** Log tag. */
    private static final String TAG = "SyncReaderResults";
    /**
     * {@link SyncReaderResults} after an import.
     */
    public static final String BKEY_IMPORT_RESULTS = TAG + ":results";
    /**
     * Keeps track of failed import lines in a text file.
     * Not strictly needed as row number should be part of the messages.
     * Keeping for possible future enhancements.
     */
    public final List<Integer> failedLinesNr = new ArrayList<>();
    /** Keeps track of failed import lines in a text file. */
    public final List<String> failedLinesMessage = new ArrayList<>();

    /** The total #books that were present in the import data. */
    public int booksProcessed;
    /** #books we created. */
    public int booksCreated;
    /** #books we updated. */
    public int booksUpdated;
    /** #books we skipped for NON-failure reasons. */
    public int booksSkipped;
    /** #books which explicitly failed. */
    public int booksFailed;

    /** The total #covers that were present in the import data. */
    public int coversProcessed;
    /** #covers we created. */
    public int coversCreated;
    /** #covers we updated. */
    public int coversUpdated;
    /** #covers we skipped for NON-failure reasons. */
    public int coversSkipped;
    /** # covers which explicitly failed. */
    public int coversFailed;

    public SyncReaderResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SyncReaderResults(@NonNull final Parcel in) {
        booksProcessed = in.readInt();
        booksCreated = in.readInt();
        booksUpdated = in.readInt();
        booksSkipped = in.readInt();
        booksFailed = in.readInt();

        coversProcessed = in.readInt();
        coversCreated = in.readInt();
        coversUpdated = in.readInt();
        coversSkipped = in.readInt();
        coversFailed = in.readInt();

        in.readList(failedLinesNr, getClass().getClassLoader());
        in.readList(failedLinesMessage, getClass().getClassLoader());
    }

    public void add(@NonNull final SyncReaderResults results) {
        booksProcessed += results.booksProcessed;
        booksCreated += results.booksCreated;
        booksUpdated += results.booksUpdated;
        booksSkipped += results.booksSkipped;
        booksFailed += results.booksFailed;

        coversProcessed += results.coversProcessed;
        coversCreated += results.coversCreated;
        coversUpdated += results.coversUpdated;
        coversSkipped += results.coversSkipped;
        coversFailed += results.coversFailed;

        failedLinesNr.addAll(results.failedLinesNr);
        failedLinesMessage.addAll(results.failedLinesMessage);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(booksProcessed);
        dest.writeInt(booksCreated);
        dest.writeInt(booksUpdated);
        dest.writeInt(booksSkipped);
        dest.writeInt(booksFailed);

        dest.writeInt(coversProcessed);
        dest.writeInt(coversCreated);
        dest.writeInt(coversUpdated);
        dest.writeInt(coversSkipped);
        dest.writeInt(coversFailed);

        dest.writeList(failedLinesNr);
        dest.writeList(failedLinesMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Results{"
               + "booksProcessed=" + booksProcessed
               + ", booksCreated=" + booksCreated
               + ", booksUpdated=" + booksUpdated
               + ", booksSkipped=" + booksSkipped
               + ", booksFailed=" + booksFailed

               + ", coversProcessed=" + coversProcessed
               + ", coversCreated=" + coversCreated
               + ", coversUpdated=" + coversUpdated
               + ", coversSkipped=" + coversSkipped
               + ", coversFailed=" + coversFailed

               + ", failedLinesNr=" + failedLinesNr
               + ", failedLinesMessage=" + failedLinesMessage
               + '}';
    }
}
