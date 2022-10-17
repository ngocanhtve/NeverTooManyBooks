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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetDirectoryUriContract;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;

@Keep
public class CalibrePreferencesFragment
        extends ConnectionValidationBasePreferenceFragment {

    public static final String TAG = "CalibrePreferencesFrag";

    private static final String PSK_CA_FROM_FILE = "psk_ca_from_file";
    private static final String PSK_PICK_FOLDER = "psk_pick_folder";

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> pickFolderLauncher;

    private Preference folderPref;
    private Preference caPref;
    private final ActivityResultLauncher<String> openCaUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(),
                                      o -> o.ifPresent(this::onOpenCaUri));

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        init(R.string.site_calibre, CalibreHandler.PK_ENABLED);
        setPreferencesFromResource(R.xml.preferences_calibre, rootKey);

        EditTextPreference etp;

        etp = findPreference(CalibreContentServer.PK_HOST_URL);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());


        etp = findPreference(CalibreContentServer.PK_HOST_USER);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());


        etp = findPreference(CalibreContentServer.PK_HOST_PASS);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.selectAll();
        });
        etp.setSummaryProvider(preference -> {
            final String value = ((EditTextPreference) preference).getText();
            if (value == null || value.isEmpty()) {
                return getString(R.string.preference_not_set);
            } else {
                return "********";
            }
        });


        //noinspection ConstantConditions
        caPref = findPreference(PSK_CA_FROM_FILE);
        //noinspection ConstantConditions
        caPref.setSummary(createCaSummary());
        caPref.setOnPreferenceClickListener(preference -> {
            openCaUriLauncher.launch("*/*");
            return true;
        });

        //noinspection ConstantConditions
        folderPref = findPreference(PSK_PICK_FOLDER);
        //noinspection ConstantConditions
        setFolderSummary(folderPref);
        folderPref.setOnPreferenceClickListener(preference -> {
            //noinspection ConstantConditions
            pickFolderLauncher.launch(CalibreContentServer.getFolderUri(getContext())
                                                          .orElse(null));
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pickFolderLauncher = registerForActivityResult(
                new GetDirectoryUriContract(), o -> {
                    //noinspection ConstantConditions
                    o.ifPresent(uri -> CalibreContentServer.setFolderUri(getContext(), uri));
                    setFolderSummary(folderPref);
                });
    }

    /**
     * Read the existing download folder, and set the preference summary.
     *
     * @param preference to use
     */
    private void setFolderSummary(@NonNull final Preference preference) {
        //noinspection ConstantConditions
        final Uri uri = CalibreContentServer.getFolderUri(getContext()).orElse(null);
        if (uri == null) {
            preference.setSummary(R.string.preference_not_set);
        } else {
            final DocumentFile df = DocumentFile.fromTreeUri(getContext(), uri);
            if (df != null) {
                // Normally this will always return a name
                String name = df.getName();
                // This was seen on API 26 running in the emulator when selecting the 'download'
                //TEST: could this be due to having TWO download folders ? (device+sdcard)
                if (name == null) {
                    // not nice, but better then nothing...
                    name = uri.getLastPathSegment();
                }
                preference.setSummary(name);
            } else {
                // should never happen... flw
                preference.setSummary(R.string.preference_not_set);
            }
        }
    }

    private void onOpenCaUri(@NonNull final Uri uri) {
        //noinspection ConstantConditions
        try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
            if (is != null) {
                final X509Certificate ca;
                try (BufferedInputStream bis = new BufferedInputStream(is)) {
                    ca = (X509Certificate) CertificateFactory
                            .getInstance("X.509").generateCertificate(bis);
                }
                CalibreContentServer.setCertificate(getContext(), ca);
            }
        } catch (@NonNull final IOException | CertificateException e) {
            caPref.setSummary(R.string.error_certificate_invalid);
            return;
        }

        caPref.setSummary(createCaSummary());
    }

    /**
     * Read the existing CA file from storage, and create the preference summary.
     *
     * @return text to display as the summary
     */
    @NonNull
    private String createCaSummary() {
        try {
            //noinspection ConstantConditions
            final X509Certificate ca = CalibreContentServer.getCertificate(getContext());
            ca.checkValidity();
            return getString(R.string.certificate_issued_to,
                             ca.getSubjectX500Principal().getName())
                   + '\n'
                   + getString(R.string.certificate_issued_by,
                               ca.getIssuerX500Principal().getName());

        } catch (@NonNull final CertificateException e) {
            return getString(R.string.error_certificate_invalid);

        } catch (@NonNull final IOException e) {
            return getString(R.string.preference_not_set);
        }
    }
}
