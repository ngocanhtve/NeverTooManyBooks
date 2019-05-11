/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CropImageActivity
        extends CropMonitoredActivity {

    private static final String TAG = CropImageActivity.class.getSimpleName();

    public static final String BKEY_SCALE = TAG + ":scale";
    public static final String BKEY_DATA = TAG + ":data";
    public static final String BKEY_IMAGE_ABSOLUTE_PATH = TAG + ":image-path";
    public static final String BKEY_OUTPUT_ABSOLUTE_PATH = TAG + ":output";
    public static final String BKEY_WHOLE_IMAGE = TAG + ":whole-image";

    private static final String BKEY_OUTPUT_X = TAG + ":outputX";
    private static final String BKEY_OUTPUT_Y = TAG + ":outputY";
    private static final String BKEY_SCALE_UP_IF_NEEDED = TAG + ":scaleUpIfNeeded";
    private static final String BKEY_ASPECT_X = TAG + ":aspectX";
    private static final String BKEY_ASPECT_Y = TAG + ":aspectY";
    private static final String BKEY_RETURN_DATA = TAG + ":return-data";
    private static final String BKEY_CIRCLE_CROP = TAG + ":circleCrop";
    private static final String BKEY_NO_FACE_DETECTION = TAG + ":noFaceDetection";

    /** used to calculate free space on Shared Storage, 400kb per picture is a GUESS. */
    private static final long ESTIMATED_PICTURE_SIZE = 400_000L;

    /** only used with mOptionSaveUri. */
    private final Bitmap.CompressFormat defaultCompressFormat = Bitmap.CompressFormat.JPEG;
    private final Handler mHandler = new Handler();
    /** Whether the "save" button is already clicked. */
    boolean mIsSaving;
    CropHighlightView mCrop;
    /** Whether we are wait the user to pick a face. */
    boolean mWaitingToPickFace;
    /** These are various options can be specified in the intent. */
    @Nullable
    private Uri mOptionSaveUri;
    private int mOptionAspectX;
    private int mOptionAspectY;
    /** crop circle ? (default: rectangle). */
    private boolean mOptionCircleCrop;
    /** Output image size and whether we should scale the output to fit it (or just crop it). */
    private int mOptionOutputX;
    private int mOptionOutputY;
    /** Flag indicating if default crop rect is whole image. */
    private boolean mOptionCropWholeImage;
    private boolean mOptionScale;
    private boolean mOptionScaleUp = true;
    /** Disable face detection. */
    private boolean mOptionNoFaceDetection = true;
    private CropImageView mImageView;
    private Bitmap mBitmap;
    /** runnable that does the actual face detection. */
    private final Runnable mRunFaceDetection = new Runnable() {
        final FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
        float mScale = 1F;
        Matrix mImageMatrix;
        int mNumFaces;

        // For each face, we create a CropHighlightView for it.
        private void handleFace(@NonNull final FaceDetector.Face face) {

            PointF midPoint = new PointF();
            face.getMidPoint(midPoint);
            midPoint.x *= mScale;
            midPoint.y *= mScale;

            Rect imageRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());

            int r = ((int) (face.eyesDistance() * mScale)) * 2;
            int midX = (int) midPoint.x;
            int midY = (int) midPoint.y;
            RectF faceRect = new RectF(midX, midY, midX, midY);
            faceRect.inset(-r, -r);
            if (faceRect.left < 0) {
                faceRect.inset(-faceRect.left, -faceRect.left);
            }

            if (faceRect.top < 0) {
                faceRect.inset(-faceRect.top, -faceRect.top);
            }

            if (faceRect.right > imageRect.right) {
                faceRect.inset(faceRect.right - imageRect.right,
                               faceRect.right - imageRect.right);
            }

            if (faceRect.bottom > imageRect.bottom) {
                faceRect.inset(faceRect.bottom - imageRect.bottom,
                               faceRect.bottom - imageRect.bottom);
            }

            CropHighlightView hv = new CropHighlightView(mImageView);
            hv.setup(CropImageActivity.this, mImageMatrix, imageRect, faceRect,
                     mOptionCircleCrop,
                     mOptionAspectX != 0 && mOptionAspectY != 0);

            mImageView.add(hv);
        }

        // Create a default HighlightView if we found no face in the picture.
        private void makeDefault() {

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            int cropWidth;
            int cropHeight;
            if (mOptionCropWholeImage) {
                cropWidth = width;
                cropHeight = height;
            } else {
                int dv = Math.min(width, height);
                cropWidth = dv;
                cropHeight = dv;
            }

            // Even though we may be set to 'crop-whole-image', we need to obey aspect ratio if set.
            if (mOptionAspectX != 0 && mOptionAspectY != 0) {
                if (mOptionAspectX > mOptionAspectY) {
                    cropHeight = cropWidth * mOptionAspectY / mOptionAspectX;
                } else {
                    cropWidth = cropHeight * mOptionAspectX / mOptionAspectY;
                }
            }

            int left = (width - cropWidth) / 2;
            int top = (height - cropHeight) / 2;

            RectF cropRect = new RectF(left, top, left + cropWidth, top + cropHeight);

            CropHighlightView hv = new CropHighlightView(mImageView);
            hv.setup(CropImageActivity.this, mImageMatrix, imageRect, cropRect,
                     mOptionCircleCrop,
                     mOptionAspectX != 0 && mOptionAspectY != 0);
            mImageView.add(hv);
        }

        // Scale the image down for faster face detection.
        @Nullable
        private Bitmap prepareBitmap() {
            if (mBitmap == null) {
                return null;
            }

            // 256 pixels wide is enough.
            if (mBitmap.getWidth() > 256) {
                mScale = 256.0F / mBitmap.getWidth();
            }
            Matrix matrix = new Matrix();
            matrix.setScale(mScale, mScale);
            return Bitmap.createBitmap(mBitmap, 0, 0,
                                       mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
        }

        public void run() {
            mImageMatrix = mImageView.getImageMatrix();
            Bitmap faceBitmap = prepareBitmap();

            mScale = 1.0F / mScale;
            if (faceBitmap != null && !mOptionNoFaceDetection) {
                FaceDetector detector = new FaceDetector(faceBitmap.getWidth(),
                                                         faceBitmap.getHeight(), mFaces.length);
                mNumFaces = detector.findFaces(faceBitmap, mFaces);
            }

            if (faceBitmap != null && faceBitmap != mBitmap) {
                faceBitmap.recycle();
            }

            mHandler.post(() -> {
                mWaitingToPickFace = mNumFaces > 1;
                if (mNumFaces > 0) {
                    for (int i = 0; i < mNumFaces; i++) {
                        handleFace(mFaces[i]);
                    }
                } else {
                    makeDefault();
                }
                mImageView.invalidate();
                if (mImageView.mHighlightViews.size() == 1) {
                    mCrop = mImageView.mHighlightViews.get(0);
                    mCrop.setFocus(true);
                }

                if (mNumFaces > 1) {
                    UserMessage.showUserMessage(mImageView,
                                                "Multi face crop help not available.");
                }
            });
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_cropimage;
    }

    /**
     * create activity.
     * <p>
     * intent.putExtra(CropIImage.BKEY_SCALE, true);
     * intent.putExtra(CropIImage.BKEY_NO_FACE_DETECTION, true);
     * intent.putExtra(CropIImage.BKEY_WHOLE_IMAGE, cropFrameWholeImage);
     * intent.putExtra(CropIImage.BKEY_IMAGE_ABSOLUTE_PATH, thumbFile.getAbsolutePath());
     * intent.putExtra(CropIImage.BKEY_OUTPUT_ABSOLUTE_PATH, cropped.getAbsolutePath());
     */
    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // Do this first to avoid 'must be first errors'
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        mImageView = findViewById(R.id.coverImage);

        int msgId = checkStorage();
        // tell user if needed.
        if (msgId != 0) {
            UserMessage.showUserMessage(mImageView, msgId);
        }
        Bundle args = getIntent().getExtras();
        if (args != null) {
            if (args.getString(BKEY_CIRCLE_CROP) != null) {
                mOptionCircleCrop = true;
                mOptionAspectX = 1;
                mOptionAspectY = 1;
            }

            String imagePath = args.getString(BKEY_IMAGE_ABSOLUTE_PATH);
            Objects.requireNonNull(imagePath);
            mBitmap = getBitmap(imagePath);

            // Use the "output" parameter if present, otherwise overwrite existing file
            String imgUri = args.getString(BKEY_OUTPUT_ABSOLUTE_PATH);
            if (imgUri == null) {
                imgUri = imagePath;
            }
            mOptionSaveUri = Uri.fromFile(new File(imgUri));

            mOptionAspectX = args.getInt(BKEY_ASPECT_X);
            mOptionAspectY = args.getInt(BKEY_ASPECT_Y);
            mOptionOutputX = args.getInt(BKEY_OUTPUT_X);
            mOptionOutputY = args.getInt(BKEY_OUTPUT_Y);

            mOptionScale = args.getBoolean(BKEY_SCALE, true);
            mOptionScaleUp = args.getBoolean(BKEY_SCALE_UP_IF_NEEDED, true);
            mOptionCropWholeImage = args.getBoolean(BKEY_WHOLE_IMAGE, false);
            mOptionNoFaceDetection = args.getBoolean(BKEY_NO_FACE_DETECTION, true);
        }

        if (mBitmap != null) {
            // Make UI fullscreen.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            // the FAB button saves the image, use 'back' to cancel.
            findViewById(R.id.fab).setOnClickListener(v -> onSaveClicked());
            startFaceDetection();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Nullable
    private Bitmap getBitmap(@NonNull final String path) {
        Uri uri = Uri.fromFile(new File(path));
        InputStream in;
        try {
            in = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in);
        } catch (FileNotFoundException ignored) {
            return null;
        }
    }

    /**
     * Face detection is a partial misnomer.
     * It will check the flag {@link #mOptionNoFaceDetection} and only do ACTUAL face detection
     * if that flag == false. Otherwise, it wil still create a HighlightView which we need.
     */
    private void startFaceDetection() {
        if (isFinishing()) {
            return;
        }

        mImageView.setImageBitmapResetBase(mBitmap, true);

        CropUtil.startBackgroundJob(
                this, null, getString(R.string.progress_msg_please_wait),
                () -> {
                    final CountDownLatch latch = new CountDownLatch(1);
                    final Bitmap b = mBitmap;
                    mHandler.post(() -> {
                        if (b != mBitmap && b != null) {
                            // Do not recycle until mBitmap has been set to the new bitmap!
                            Bitmap toRecycle = mBitmap;
                            mBitmap = b;
                            mImageView.setImageBitmapResetBase(mBitmap, true);
                            toRecycle.recycle();
                        }
                        if (mImageView.getScale() == 1F) {
                            mImageView.center(true, true);
                        }
                        latch.countDown();
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    mRunFaceDetection.run();
                }, mHandler);
    }

    /**
     * TODO: this code needs to change to use the decode/crop/encode single
     * step api so that we don't require that the whole (possibly large)
     * bitmap doesn't have to be read into memory
     */
    private void onSaveClicked() {
        if (mIsSaving) {
            return;
        }

        if (mCrop == null) {
            return;
        }

        mIsSaving = true;

        Rect cropRect = mCrop.getCropRect();
        int width = cropRect.width();
        int height = cropRect.height();

        // If we are circle cropping, we want alpha channel, which is the third param here.
        Bitmap croppedImage = Bitmap.createBitmap(width, height,
                                                  mOptionCircleCrop ? Bitmap.Config.ARGB_8888
                                                                    : Bitmap.Config.RGB_565);
        {
            Canvas canvas = new Canvas(croppedImage);
            Rect dstRect = new Rect(0, 0, width, height);
            canvas.drawBitmap(mBitmap, cropRect, dstRect, null);
        }

        if (mOptionCircleCrop) {
            // OK, so what's all this about?
            // Bitmaps are inherently rectangular but we want to return something that's basically
            // a circle. So we fill in the area around the circle with alpha. Note the all important
            // PorterDuff.Mode.CLEAR (Destination pixels covered by the source are cleared to 0)
            Canvas canvas = new Canvas(croppedImage);
            Path path = new Path();
            path.addCircle(width / 2F, height / 2F, width / 2F, Path.Direction.CW);
            canvas.clipPath(path, Region.Op.DIFFERENCE);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }

        /* If the output is required to a specific size then SCALE or fill */
        if (mOptionOutputX != 0 && mOptionOutputY != 0) {
            if (mOptionScale) {
                /* Scale the image to the required dimensions */
                Bitmap old = croppedImage;
                croppedImage = CropUtil.transform(new Matrix(), croppedImage,
                                                  mOptionOutputX, mOptionOutputY, mOptionScaleUp);
                if (old != croppedImage) {
                    old.recycle();
                }
            } else {

                /*
                 * Don't SCALE the image crop it to the size requested. Create
                 * an new image with the cropped image in the center and the
                 * extra space filled.
                 */

                // Don't SCALE the image but instead fill it, so it's the required dimension
                Bitmap bitmap = Bitmap.createBitmap(mOptionOutputX, mOptionOutputY,
                                                    Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(bitmap);

                Rect srcRect = mCrop.getCropRect();
                Rect dstRect = new Rect(0, 0, mOptionOutputX, mOptionOutputY);

                int dx = (srcRect.width() - dstRect.width()) / 2;
                int dy = (srcRect.height() - dstRect.height()) / 2;

                /* If the srcRect is too big, use the center part of it. */
                srcRect.inset(Math.max(0, dx), Math.max(0, dy));

                /* If the dstRect is too big, use the center part of it. */
                dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

                /* Draw the cropped bitmap in the center */
                canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

                /* Set the cropped bitmap as the new bitmap */
                croppedImage.recycle();
                croppedImage = bitmap;
            }
        }

        // Return the cropped image directly or save it to the specified URI.
        Bundle extras = getIntent().getExtras();
        if (extras != null && (extras.getParcelable(BKEY_DATA) != null
                || extras.getBoolean(BKEY_RETURN_DATA))) {

            Bundle resultExtras = new Bundle();
            resultExtras.putParcelable(BKEY_DATA, croppedImage);
            Intent data = new Intent("inline-data").putExtras(resultExtras);
            setResult(Activity.RESULT_OK, data);
            finish();
        } else {
            // save to the URI in a background task
            final Bitmap bitmap = croppedImage;
            CropUtil.startBackgroundJob(this, null, getString(R.string.progress_msg_saving_image),
                                        () -> saveOutput(bitmap), mHandler);
        }
    }

    private void saveOutput(@NonNull final Bitmap croppedImage) {
        Bundle data = new Bundle();
        if (mOptionSaveUri == null) {
            // we were not asked to save anything, but we're ok with that
            setResult(Activity.RESULT_OK);
        } else {
            Intent intent = new Intent(mOptionSaveUri.toString()).putExtras(data);
            try (OutputStream outputStream = getContentResolver().openOutputStream(
                    mOptionSaveUri)) {
                if (outputStream != null) {
                    croppedImage.compress(defaultCompressFormat, 75, outputStream);
                }
                // we saved the image
                setResult(Activity.RESULT_OK, intent);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
                Logger.error(this, e);
                setResult(Activity.RESULT_CANCELED);
            }
        }
        // clean up and quit.
        croppedImage.recycle();
        finish();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
        super.onDestroy();
    }

    @StringRes
    private int checkStorage() {
        @StringRes
        int msgId = StorageUtils.getMediaStateMessageId();
        if (msgId == 0) {
            // stat the filesystem
            long freeSpace = StorageUtils.getSharedStorageFreeSpace();
            if (freeSpace == StorageUtils.ERROR_CANNOT_STAT) {
                msgId = R.string.error_storage_no_access;
            } else {
                // make an educated guess how many pics we can store.
                if (freeSpace / ESTIMATED_PICTURE_SIZE < 1) {
                    msgId = R.string.error_storage_no_space_left;
                }
            }
        }

    return msgId;
    }
}
