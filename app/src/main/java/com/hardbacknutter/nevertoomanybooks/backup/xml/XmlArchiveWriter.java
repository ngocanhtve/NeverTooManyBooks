/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Hardcoded to only write {@link RecordType#Books} into an XML file.
 * <p>
 * We limit xml export to a full export only (ignoring {@link ExportHelper#isIncremental()}).
 * Hence we're not using or updating the "last full backup date".
 * Reasoning is that we don't support importing from xml.
 */
public class XmlArchiveWriter
        implements DataWriter<ExportResults> {

    /**
     * v4 writes out an XML envelope with two elements inside:<br>
     * Books, <strong>followed</strong> by MetaData.
     * <p>
     * v3 used to write metadata,styles,preferences,books (in that order).
     */
    private static final int VERSION = 4;

    /** The xml root tag. */
    private static final String TAG_APPLICATION_ROOT = "NeverTooManyBooks";

    /** Export configuration. */
    @NonNull
    private final ExportHelper exportHelper;

    /**
     * Constructor.
     *
     * @param exportHelper export configuration
     */
    public XmlArchiveWriter(@NonNull final ExportHelper exportHelper) {
        this.exportHelper = exportHelper;
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   IOException {

        final int booksToExport = ServiceLocator.getInstance().getBookDao()
                                                .countBooksForExport(null);

        if (booksToExport > 0) {
            progressListener.setMaxPos(booksToExport);

            final ExportResults results;

            try (OutputStream os = exportHelper.createOutputStream(context);
                 Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
                 RecordWriter recordWriter = new XmlRecordWriter(null)) {

                // manually concat
                // 1. archive envelope
                bw.write(XmlUtils.XML_VERSION_1_0_ENCODING_UTF_8
                         + '<' + TAG_APPLICATION_ROOT + XmlUtils.versionAttr(VERSION) + ">\n");
                // 2. the actual data inside the container
                results = recordWriter.write(context, bw, EnumSet.of(RecordType.Books),
                                             progressListener);

                // 3. the metadata
                recordWriter.writeMetaData(bw, ArchiveMetaData.create(context, VERSION, results));
                // 4. close the envelope
                bw.write("</" + TAG_APPLICATION_ROOT + ">\n");
            }

            // do NOT set the LastFullBackupDate
            return results;
        } else {
            return new ExportResults();
        }
    }
}
