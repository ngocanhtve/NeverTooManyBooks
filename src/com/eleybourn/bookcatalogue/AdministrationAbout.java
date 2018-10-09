/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This is the About page. It contains details about the app, links to my website and email.
 *
 * @author Evan Leybourn
 */
public class AdministrationAbout extends BookCatalogueActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_about;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.app_name);
            setupPage();
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    private void setupPage() {
        /* Version Number */
        TextView release = findViewById(R.id.version);
        PackageManager manager = this.getPackageManager();
        PackageInfo info;
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            String versionName = info.versionName;
            release.setText(versionName);
        } catch (NameNotFoundException e) {
            Logger.logError(e);
        }

        TextView website = findViewById(R.id.website);
        website.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website)));
                startActivity(intent);
            }
        });
        TextView sourcecode = findViewById(R.id.sourcecode);
        sourcecode.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sourcecode)));
                startActivity(intent);
            }
        });
        TextView contact1 = findViewById(R.id.contact1);
        contact1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendContactEmail(R.string.contact1);
            }
        });
        TextView contact2 = findViewById(R.id.contact2);
        contact2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendContactEmail(R.string.contact2);
            }
        });

        {
            TextView tv = findViewById(R.id.amazon_links_info);
            // Setup the linked HTML
            String text = getString(R.string.hint_amazon_links_blurb,
                    getString(R.string.amazon_books_by_author),
                    getString(R.string.amazon_books_in_series),
                    getString(R.string.amazon_books_by_author_in_series),
                    getString(R.string.app_name));
            tv.setText(Utils.linkifyHtml(text, Linkify.ALL));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void sendContactEmail(@StringRes final int stringId) {
        try {
            Intent msg = new Intent(Intent.ACTION_SEND);
            msg.setType("text/plain");
            msg.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(stringId)});
            String subject = "[" + getString(R.string.app_name) + "] ";
            msg.putExtra(Intent.EXTRA_SUBJECT, subject);
            AdministrationAbout.this.startActivity(Intent.createChooser(msg, "Send email..."));
        } catch (ActivityNotFoundException e) {
            Logger.logError(e);
        }
    }
}