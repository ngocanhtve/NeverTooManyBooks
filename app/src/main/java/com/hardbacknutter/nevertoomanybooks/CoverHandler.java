/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.cropper.CropImageActivity;
import com.hardbacknutter.nevertoomanybooks.cropper.CropImageViewTouchBase;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * Layer Type to use for the Cropper.
 * <p>
 * {@link CropImageViewTouchBase} We get 'unsupported feature' crashes if the option
 * to always use GL is turned on.
 * See:
 * <a href="http://developer.android.com/guide/topics/graphics/hardware-accel.html>
 * http://developer.android.com/guide/topics/graphics/hardware-accel.html</a>
 * <a href="http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath">
 * http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath</a>
 * so for API: level > 11, we turn it off manually.
 * <p>
 * 2018-11-30: making this a configuration option.
 */
public class CoverHandler {

    /** log tag. */
    private static final String TAG = "CoverHandler";

    private static final String IMAGE_MIME_TYPE = "image/*";
    /** The cropper uses a single file. */
    private static final String TEMP_COVER_FILENAME = TAG + ".file";
    /** After taking a picture, do nothing. */
    private static final int CAMERA_NEXT_ACTION_NOTHING = 0;
    /** After taking a picture, crop. */
    private static final int CAMERA_NEXT_ACTION_CROP = 1;
    /** After taking a picture, edit. */
    private static final int CAMERA_NEXT_ACTION_EDIT = 2;
    private final int mCIdx;

    /** The fragment hosting us. */
    @NonNull
    private final Fragment mFragment;
    @NonNull
    private final ImageView mCoverView;
    /** Context from the fragment, cached as used frequently. */
    @NonNull
    private final Context mContext;
    @NonNull
    private final Book mBook;
    /**
     * keep a reference to the ISBN Field, so we can use the *current* value
     * when we're in the book edit fragment.
     */
    @NonNull
    private final TextView mIsbnView;
    @Nullable
    private final ProgressBar mProgressBar;
    private final int mMaxWidth;
    private final int mMaxHeight;
    /**
     * Constructor.
     *
     * @param fragment    the hosting fragment
     * @param progressBar (optional) a progress bar
     * @param book        the book whose cover we're handling
     * @param isbnView    the view to read the *current* ISBN from
     * @param cIdx        0..n image index
     * @param coverView   the views to populate
     * @param scale       image scale to apply
     */
    CoverHandler(@NonNull final Fragment fragment,
                 @Nullable final ProgressBar progressBar,
                 @NonNull final Book book,
                 @NonNull final TextView isbnView,
                 final int cIdx,
                 @NonNull final ImageView coverView,
                 final int scale) {

        mFragment = fragment;
        //noinspection ConstantConditions
        mContext = mFragment.getContext();
        mProgressBar = progressBar;
        mBook = book;
        mIsbnView = isbnView;
        mCIdx = cIdx;
        mCoverView = coverView;
        int maxSize = ImageUtils.getMaxImageSize(mContext, scale);
        mMaxHeight = maxSize;
        mMaxWidth = maxSize;
    }

    @Nullable
    private CoverBrowserFragment mCoverBrowser;
    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;
    @Nullable
    private CameraHelper mCameraHelper;

    /**
     * Delete any orphaned temporary cover files.
     *
     * @param context Current context
     */
    static void deleteOrphanedCoverFiles(@NonNull final Context context) {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            StorageUtils.deleteFile(StorageUtils.getTempCoverFile(context, String.valueOf(cIdx)));
        }
    }

    /**
     * Context menu for the image.
     */
    void onCreateContextMenu() {
        Menu menu = MenuPicker.createMenu(mContext);
        new MenuInflater(mContext).inflate(R.menu.c_image, menu);

        CharSequence title;
        if (getCoverFile().exists()) {
            title = mContext.getString(R.string.title_cover);
        } else {
            MenuItem menuItem = menu.findItem(R.id.SUBMENU_THUMB_REPLACE);
            menu = menuItem.getSubMenu();
            title = menuItem.getTitle();
        }

        new MenuPicker<>(mContext, title, menu, null, CoverHandler.this::onViewContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     * Reminder: the 'menuItem' here *is* the 'item'.
     *
     * @param menuItem that the user selected
     *
     * @return {@code true} if handled here.
     */
    private boolean onViewContextItemSelected(@NonNull final MenuItem menuItem,
                                              @Nullable final Void aVoid) {
        switch (menuItem.getItemId()) {
            case R.id.MENU_DELETE: {
                deleteCoverFile();
                return true;
            }
            case R.id.SUBMENU_THUMB_ROTATE: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mShowHintAboutRotating) {
                    TipManager.display(mContext, R.string.tip_autorotate_camera_images, null);
                    mShowHintAboutRotating = false;
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CW: {
                new RotateTask(getCoverFile(), 90, mProgressBar, this)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CCW: {
                new RotateTask(getCoverFile(), -90, mProgressBar, this)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_180: {
                new RotateTask(getCoverFile(), 180, mProgressBar, this)
                        .execute();
                return true;
            }
            case R.id.MENU_THUMB_CROP: {
                cropCoverFile();
                return true;
            }
            case R.id.MENU_EDIT: {
                editCoverFile();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_CAMERA: {
                startCamera();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_GALLERY: {
                startChooser();
                return true;
            }
            case R.id.MENU_THUMB_ADD_ALT_EDITIONS: {
                startCoverBrowser();
                return true;
            }
            default: {
                return false;
            }
        }
    }

    /**
     * Handles results from Camera, Image Gallery, Cropping.
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            case UniqueId.REQ_ACTION_COVER_BROWSER: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    processCoverBrowserResult(data);
                }
                return true;
            }
            case UniqueId.REQ_ACTION_GET_CONTENT: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    processChooserResult(data);
                }
                return true;
            }
            case UniqueId.REQ_ACTION_IMAGE_CAPTURE: {
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    File source = mCameraHelper.getFile(mContext, data);
                    if (source != null && source.exists()) {
                        // always store/display first.
                        File destination = getCoverFile();
                        StorageUtils.renameFile(source, destination);
                        setImage(destination);
                        // anything else?
                        @CameraNextAction
                        int action = PIntString.getListPreference(Prefs.pk_camera_image_action,
                                                                  CAMERA_NEXT_ACTION_NOTHING);
                        switch (action) {
                            case CAMERA_NEXT_ACTION_CROP:
                                cropCoverFile();
                                break;
                            case CAMERA_NEXT_ACTION_EDIT:
                                editCoverFile();
                                break;

                            case CAMERA_NEXT_ACTION_NOTHING:
                            default:
                                break;
                        }
                        return true;
                    }
                }
                // remove orphans
                CameraHelper.deleteTempFile(mContext);
                return true;
            }
            case UniqueId.REQ_CROP_IMAGE:
            case UniqueId.REQ_EDIT_IMAGE: {
                if (resultCode == Activity.RESULT_OK) {
                    File source = StorageUtils.getTempCoverFile(mContext, TEMP_COVER_FILENAME);
                    File destination = getCoverFile();
                    StorageUtils.renameFile(source, destination);
                    setImage(destination);
                    return true;
                }
                // remove orphans
                StorageUtils.deleteFile(
                        StorageUtils.getTempCoverFile(mContext, TEMP_COVER_FILENAME));
                return true;
            }
            default:
                return false;
        }
    }

    void setImage() {
        setImage(getCoverFile());
    }

    /**
     * Put the cover image on screen, <strong>and update the book</strong> with the file name.
     */
    private void setImage(@Nullable final File file) {
        long fileLen = file == null ? 0 : file.length();

        if (fileLen > ImageUtils.MIN_IMAGE_FILE_SIZE) {
            new ImageUtils.ImageLoader(mCoverView, file, mMaxWidth, mMaxHeight, true)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            mBook.putString(UniqueId.BKEY_FILE_SPEC[mCIdx], file.getAbsolutePath());
            return;
        }

        if (fileLen == 0) {
            ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_add_a_photo, false, mMaxHeight);
        } else {
            ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_broken_image, false, mMaxHeight);
        }
        mBook.remove(UniqueId.BKEY_FILE_SPEC[mCIdx]);
    }

    /**
     * Get the File object for the cover of the book we are editing.
     */
    @NonNull
    File getCoverFile() {
        // for existing books, we use the UUID and the index to get the stored file.
        String uuid = mBook.getString(DBDefinitions.KEY_BOOK_UUID);
        if (!uuid.isEmpty()) {
            return StorageUtils.getCoverFileForUuid(mCoverView.getContext(), uuid, mCIdx);
        }

        // for new books, check the bundle.
        String fileSpec = mBook.getString(UniqueId.BKEY_FILE_SPEC[mCIdx]);
        if (!fileSpec.isEmpty()) {
            return new File(fileSpec);
        }

        // return a new File object
        return StorageUtils.getTempCoverFile(mContext, String.valueOf(mCIdx));
    }

    /**
     * Delete the image.
     */
    private void deleteCoverFile() {
        StorageUtils.deleteFile(getCoverFile());

        // Ensure that the cached images for this book are deleted (if present).
        // Yes, this means we also delete the ones where != index, but we don't care; it's a cache.
        String uuid = mBook.getString(DBDefinitions.KEY_BOOK_UUID);
        if (!uuid.isEmpty()) {
            CoversDAO.delete(uuid);
        }
        // replace the old image with a placeholder.
        ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_add_a_photo, false, mMaxHeight);
    }

    private void startCamera() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setRotationAngle(
                    PIntString.getListPreference(Prefs.pk_camera_image_autorotate, 0));
            mCameraHelper.setUseFullSize(true);
        }
        mCameraHelper.startCamera(mFragment, UniqueId.REQ_ACTION_IMAGE_CAPTURE);
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     */
    private void startCoverBrowser() {
        // getting alternative editions is limited to LibraryThing and ISFDB for now.
        // Remind the user that LT is rather essential.
        if (!LibraryThingManager.hasKey(mContext)) {
            LibraryThingManager.alertRegistrationNeeded(mContext, false, "cover_browser");
            return;
        }

        ISBN isbn = ISBN.createISBN(mIsbnView.getText().toString());
        if (isbn != null && isbn.isValid()) {
            //noinspection ConstantConditions
            mCoverBrowser = CoverBrowserFragment.newInstance(isbn.asText());
            // we must use the same fragment manager as the hosting fragment...
            mCoverBrowser.show(mFragment.getParentFragmentManager(), CoverBrowserFragment.TAG);
            // ... as we will communicate between the two fragments directly
            mCoverBrowser.setTargetFragment(mFragment, UniqueId.REQ_ACTION_COVER_BROWSER);

        } else {
            Snackbar.make(mCoverView, R.string.warning_requires_isbn,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * When the user clicks the switcher in the {@link CoverBrowserFragment},
     * we take that image and stuff it into the view.
     */
    private void processCoverBrowserResult(@NonNull final Intent data) {
        String fileSpec = data.getStringExtra(CoverBrowserFragment.BKEY_SELECTED_COVER);
        if (mCoverBrowser != null && fileSpec != null && !fileSpec.isEmpty()) {
            File source = new File(fileSpec);
            File destination = getCoverFile();
            StorageUtils.renameFile(source, destination);
            setImage(destination);
            // all done, get rid of the browser fragment
            mCoverBrowser.dismiss();
        }
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void processChooserResult(@NonNull final Intent data) {
        Uri uri = data.getData();
        if (uri != null) {
            File file = null;
            try (InputStream is = mContext.getContentResolver().openInputStream(uri)) {
                file = StorageUtils.saveInputStreamToFile(mContext, is, getCoverFile());

            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Unable to copy content to file", e);
                }
            }

            if (file != null) {
                // Update the ImageView with the new image
                setImage(file);
            } else {
                String msg = mContext.getString(R.string.warning_cover_copy_failed) + ". "
                             + mContext.getString(R.string.error_if_the_problem_persists,
                                                  mContext.getString(R.string.lbl_send_debug_info));
                Snackbar.make(mCoverView, msg, Snackbar.LENGTH_LONG).show();
            }
        } else {
            /* Deal with the case where the chooser returns a {@code null} intent.
             * This seems to happen when the filename is not properly understood
             * by the chooser (e.g. an apostrophe in the file name confuses
             * ES File Explorer in the current version as of 23-Sep-2012. */
            Snackbar.make(mCoverView, R.string.warning_cover_copy_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from an external app.
     */
    private void startChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType(IMAGE_MIME_TYPE);
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mContext.getString(R.string.title_select_image)),
                UniqueId.REQ_ACTION_GET_CONTENT);
    }

    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     */
    private void cropCoverFile() {
        File inputFile = getCoverFile();
        // Get the output file spec.
        File outputFile = StorageUtils.getTempCoverFile(mContext, TEMP_COVER_FILENAME);
        // delete any orphaned file.
        StorageUtils.deleteFile(outputFile);

        boolean wholeImage = PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .getBoolean(Prefs.pk_image_cropper_frame_whole, false);

        Intent intent = new Intent(mContext, CropImageActivity.class)
                .putExtra(CropImageActivity.BKEY_IMAGE_ABSOLUTE_PATH,
                          inputFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_OUTPUT_ABSOLUTE_PATH,
                          outputFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_WHOLE_IMAGE, wholeImage);

        mFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE);
    }

    /**
     * Edit the image using an external application.
     */
    private void editCoverFile() {
        File inputFile = getCoverFile();
        File outputFile = StorageUtils.getTempCoverFile(mContext, TEMP_COVER_FILENAME);

        //TODO: we should revoke the permissions afterwards!
        Uri inputUri = GenericFileProvider.getUriForFile(mContext, inputFile);
        Uri outputUri = GenericFileProvider.getUriForFile(mContext, outputFile);

        Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(inputUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, outputUri);

        List<ResolveInfo> resInfoList =
                mContext.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfoList.isEmpty()) {
            // We do not know which app will be used, so need to grant permission to all.
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                mContext.grantUriPermission(packageName, outputUri,
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            intent = Intent.createChooser(intent, mContext.getString(R.string.edit));
            mFragment.startActivityForResult(intent, UniqueId.REQ_EDIT_IMAGE);

        } else {
            Snackbar.make(mCoverView, mFragment.getString(R.string.error_no_image_editor),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    @IntDef({CAMERA_NEXT_ACTION_NOTHING, CAMERA_NEXT_ACTION_CROP, CAMERA_NEXT_ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CameraNextAction {

    }

    private static class RotateTask
            extends AsyncTask<Void, Void, Boolean> {

        @Nullable
        private final WeakReference<CoverHandler> mCoverHandler;
        @NonNull
        private final File mFile;
        private final long mAngle;
        @NonNull
        private final WeakReference<ProgressBar> mProgressBar;

        RotateTask(@NonNull final File file,
                   final long angle,
                   @Nullable final ProgressBar progressBar,
                   @Nullable final CoverHandler coverHandler) {

            mCoverHandler = new WeakReference<>(coverHandler);
            mFile = file;
            mAngle = angle;
            mProgressBar = new WeakReference<>(progressBar);
        }

        @Override
        protected void onPreExecute() {
            if (mProgressBar.get() != null) {
                mProgressBar.get().setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            return ImageUtils.rotate(App.getAppContext(), mFile, mAngle);
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            if (mProgressBar.get() != null) {
                mProgressBar.get().setVisibility(View.GONE);
            }
            if (success && mCoverHandler != null && mCoverHandler.get() != null) {
                mCoverHandler.get().setImage(mFile);
            }
        }
    }
}
