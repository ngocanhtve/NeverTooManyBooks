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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Base class for all Goodreads handler classes.
 * <p>
 * The job of a handler is to implement a method to run the Goodreads request
 * and to process the output.
 */
public abstract class ApiHandler {

    /** Log tag. */
    private static final String TAG = "ApiHandler";

    /** log error string. */
    private static final String ERROR_UNEXPECTED_RESPONSE_CODE = "Unexpected response code: ";
    @NonNull
    protected final GoodreadsAuth mGrAuth;
    @NonNull
    final Context mAppContext;
    @NonNull
    private final SearchEngineConfig mSEConfig;

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     */
    protected ApiHandler(@NonNull final Context appContext,
                         @NonNull final GoodreadsAuth grAuth) {
        mAppContext = appContext;
        mGrAuth = grAuth;

        mSEConfig = SearchEngineRegistry.getInstance().getByEngineId(SearchSites.GOODREADS);
    }

    /**
     * Sign a request and GET it; then pass it off to a parser.
     *
     * @param url               to GET
     * @param parameterMap      (optional) parameters to add to the url
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws IOException on failures
     */
    protected void executeGet(@NonNull final String url,
                              @Nullable final Map<String, String> parameterMap,
                              final boolean requiresSignature,
                              @Nullable final DefaultHandler requestHandler)
            throws GeneralParsingException, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executeGet|url=\"" + url + '\"');
        }

        String fullUrl = url;
        if (parameterMap != null) {
            final Uri.Builder builder = new Uri.Builder();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            final String query = builder.build().getEncodedQuery();
            if (query != null) {
                // add or append query string.
                fullUrl += (url.indexOf('?') < 0 ? '?' : '&') + query;
            }
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(fullUrl).openConnection();
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        parseResponse(request, requestHandler);
    }

    /**
     * Sign a request and POST it; then pass it off to a parser.
     *
     * @param url               to POST
     * @param parameterMap      (optional) parameters to add to the POST
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws IOException on failures
     */
    void executePost(@NonNull final String url,
                     @Nullable final Map<String, String> parameterMap,
                     @SuppressWarnings("SameParameterValue") final boolean requiresSignature,
                     @Nullable final DefaultHandler requestHandler)
            throws GeneralParsingException, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executePost|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod(HttpUtils.POST);
        request.setDoOutput(true);
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signPostRequest(request, parameterMap);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        // Now the actual POST payload consisting of the parameters (FORM data)
        if (parameterMap != null) {
            final Uri.Builder builder = new Uri.Builder();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            final String query = builder.build().getEncodedQuery();
            if (query != null) {
                try (OutputStream os = request.getOutputStream();
                     Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                     Writer writer = new BufferedWriter(osw)) {
                    writer.write(query);
                    writer.flush();
                }
            }
        }

        parseResponse(request, requestHandler);
    }

    /**
     * Check the response code; then pass a successful response off to a parser.
     *
     * @param request        to execute
     * @param requestHandler (optional) handler for the parser
     *
     * @throws CredentialsException  on login failure
     * @throws HttpNotFoundException the URL was not found
     * @throws HttpStatusException   on other HTTP failures
     * @throws IOException           on other failures
     */
    private void parseResponse(@NonNull final HttpURLConnection request,
                               @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, HttpNotFoundException, HttpStatusException, IOException,
                   GeneralParsingException {

        try {
            final int responseCode = request.getResponseCode();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_HTTP_XML) {
                Log.d(TAG, "execute"
                           + "|" + request.getURL()
                           + "|" + ERROR_UNEXPECTED_RESPONSE_CODE
                           + responseCode + '/' + request.getResponseMessage());
            }

            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_CREATED:
                    try (InputStream is = request.getInputStream()) {
                        final SAXParserFactory factory = SAXParserFactory.newInstance();
                        final SAXParser parser = factory.newSAXParser();
                        parser.parse(is, requestHandler);

                    } catch (@NonNull final ParserConfigurationException | SAXException e) {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "parseResponse", e);
                        }
                        throw new GeneralParsingException(e);
                    }
                    break;

                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    GoodreadsAuth.invalidateCredentials();
                    throw new CredentialsException(R.string.site_goodreads,
                                                   request.getResponseMessage(),
                                                   request.getURL());

                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new HttpNotFoundException(R.string.site_goodreads,
                                                    request.getResponseMessage(),
                                                    request.getURL());

                default:
                    throw new HttpStatusException(R.string.site_goodreads,
                                                  request.getResponseCode(),
                                                  request.getResponseMessage(),
                                                  request.getURL());
            }
        } finally {
            request.disconnect();
        }
    }

    /**
     * Sign a request and submit it. Return the raw text output.
     *
     * @param url               to GET
     * @param requiresSignature Flag to optionally sign the request
     *
     * @return the raw text output.
     *
     * @throws CredentialsException  on login failure
     * @throws HttpNotFoundException the URL was not found
     * @throws HttpStatusException   on other HTTP failures
     * @throws IOException           on other failures
     */
    @NonNull
    String executeRawGet(@NonNull final String url,
                         @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException, HttpNotFoundException, HttpStatusException, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executeRawGet|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        try {
            final int responseCode = request.getResponseCode();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_HTTP_XML) {
                Log.d(TAG, "execute"
                           + "|" + request.getURL()
                           + "|" + ERROR_UNEXPECTED_RESPONSE_CODE
                           + responseCode + '/' + request.getResponseMessage());
            }
            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_CREATED:
                    // Read the request into a single String.
                    final StringBuilder content = new StringBuilder();
                    final InputStream is = request.getInputStream();
                    if (is != null) {
                        while (true) {
                            final int i = is.read();
                            if (i == -1) {
                                break;
                            }
                            content.append((char) i);
                        }
                    }
                    return content.toString();

                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    GoodreadsAuth.invalidateCredentials();
                    throw new CredentialsException(R.string.site_goodreads,
                                                   request.getResponseMessage(),
                                                   request.getURL());

                case HttpURLConnection.HTTP_NOT_FOUND:
                    throw new HttpNotFoundException(R.string.site_goodreads,
                                                    request.getResponseMessage(),
                                                    request.getURL());

                default:
                    throw new HttpStatusException(R.string.site_goodreads,
                                                  request.getResponseCode(),
                                                  request.getResponseMessage(),
                                                  request.getURL());
            }
        } finally {
            request.disconnect();
        }
    }
}
