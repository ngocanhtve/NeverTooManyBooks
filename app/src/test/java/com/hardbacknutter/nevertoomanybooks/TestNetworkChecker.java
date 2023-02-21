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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import com.hardbacknutter.nevertoomanybooks.network.NetworkChecker;

public class TestNetworkChecker
        implements NetworkChecker {

    private final boolean connected;

    public TestNetworkChecker(final boolean connected) {
        this.connected = connected;
    }

    @Override
    public boolean isNetworkAvailable(@NonNull final Context context) {
        return connected;
    }

    @Override
    public void ping(@NonNull final String urlStr,
                     final int timeoutInMs)
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {

    }
}