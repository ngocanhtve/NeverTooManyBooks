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
package com.hardbacknutter.nevertoomanybooks.viewmodels.tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveExportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.EntityExporterTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * See parent class doc.
 *
 * <strong>Note:</strong> a ViewModel must be "public" despite Android Studio
 * proposing "package-private".
 * The catch: it will work in the emulator, but fail on a real device.
 */
public class ExportTaskModel
        extends TaskBaseModel<ExportManager> {

    /** export configuration. */
    @Nullable
    private ExportManager mHelper;

    public void setHelper(@NonNull final ExportManager helper) {
        mHelper = helper;
    }

    public String getDefaultArchiveName(@NonNull final Context context) {
        Objects.requireNonNull(mHelper);
        return getNamePrefix(context) + mHelper.getArchiveContainer().getFileExtension();
    }

    public String getDefaultCsvFilename(@NonNull final Context context) {
        return getNamePrefix(context) + ".csv";
    }

    public String getDefaultDatabaseName(@NonNull final Context context) {
        return getNamePrefix(context) + ".db";
    }

    private String getNamePrefix(@NonNull final Context context) {
        return context.getString(R.string.app_name) + '-'
               + DateUtils.localSqlDateForToday()
                          .replace(" ", "-")
                          .replace(":", "")
               + ".ntmb";
    }

    /**
     * Start the task.
     * {@link #setHelper(ExportManager)} must have been called before.
     *
     * @param uri to write to
     */
    public void startArchiveExportTask(@NonNull final Uri uri) {
        Objects.requireNonNull(mHelper);
        mHelper.setUri(uri);
        ArchiveExportTask task = new ArchiveExportTask(mHelper, getTaskListener());
        setTask(task);
        task.execute();
    }

    /**
     * Start the task.
     * {@link #setHelper(ExportManager)} must have been called before.
     *
     * @param uri to write to
     */
    public void startExporterTask(@NonNull final Uri uri) {
        Objects.requireNonNull(mHelper);
        mHelper.setUri(uri);
        final Context context = LocaleUtils.applyLocale(App.getAppContext());
        Exporter exporter = new CsvExporter(context, Options.BOOKS, null);
        EntityExporterTask task = new EntityExporterTask(exporter, mHelper, getTaskListener());
        setTask(task);
        task.execute();
    }
}
