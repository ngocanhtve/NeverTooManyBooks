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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.net.HttpURLConnection;
import java.net.URL;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Dedicated 404 HTTP_NOT_FOUND providing a user readable/localized message.
 */
public class HttpNotFoundException
        extends HttpStatusException {

    private static final long serialVersionUID = 6461290696382042369L;
    @Nullable
    private String message;

    /**
     * Constructor.
     *
     * @param siteResId     the site string res; which will be embedded in a default user message
     * @param statusMessage the original status message from the HTTP request
     * @param url           (optional) The full url, for debugging
     */
    HttpNotFoundException(@StringRes final int siteResId,
                          @NonNull final String statusMessage,
                          @Nullable final URL url) {
        super(siteResId, HttpURLConnection.HTTP_NOT_FOUND, statusMessage, url);
    }

    /**
     * Override the default message.
     *
     * @param message to use
     */
    void setUserMessage(@NonNull final String message) {
        this.message = message;
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        if (message != null) {
            return message;

        } else if (getSiteResId() != 0) {
            return context.getString(R.string.error_network_site_access_failed,
                                     context.getString(getSiteResId()));
        } else {
            return context.getString(R.string.httpErrorFileNotFound);
        }
    }
}
