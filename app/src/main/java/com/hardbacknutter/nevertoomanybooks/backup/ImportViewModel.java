/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

public class ImportViewModel
        extends DataReaderViewModel<ArchiveMetaData, ImportResults> {

    @Nullable
    private ImportHelper importHelper;
    private boolean removeDeletedBooksAfterImport = true;

    @NonNull
    ImportHelper createDataReaderHelper(@NonNull final Context context,
                                        @NonNull final Uri uri,
                                        final Locale systemLocale)
            throws DataReaderException, FileNotFoundException {

        importHelper = new ImportHelper(context, systemLocale, uri);
        return importHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return importHelper != null;
    }

    @Override
    @NonNull
    public ImportHelper getDataReaderHelper() {
        return Objects.requireNonNull(importHelper, "importHelper");
    }

    @Override
    public boolean isReadyToGo() {
        if (importHelper == null) {
            // duh...
            return false;
        }
        if (importHelper.getRecordTypes().isEmpty()) {
            // nothing to do
            return false;
        }
        return importHelper.getMetaData().isPresent();
    }

    boolean isRemoveDeletedBooksAfterImport() {
        return removeDeletedBooksAfterImport;
    }

    void setRemoveDeletedBooksAfterImport(final boolean removeDeletedBooksAfterImport) {
        this.removeDeletedBooksAfterImport = removeDeletedBooksAfterImport;
    }

    void onImportFinished(@NonNull final ImportResults result) {
        if (result.styles > 0) {
            final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
            // Resort the styles menu as per their (new) order.
            stylesHelper.updateMenuOrder();
            // Force a refresh of the cached styles.
            stylesHelper.clearCache();
        }

        // If the user checked the option to import books,
        // we also imported the deleted-book records for future syncs
        // which is independent from the sync option.

        // Here we are effectively deleting the actual books if sync was enabled.
        if (getDataReaderHelper().getRecordTypes().contains(RecordType.Books)
            && removeDeletedBooksAfterImport) {
            ServiceLocator.getInstance().getDeletedBooksDao().sync();
        }
    }
}
