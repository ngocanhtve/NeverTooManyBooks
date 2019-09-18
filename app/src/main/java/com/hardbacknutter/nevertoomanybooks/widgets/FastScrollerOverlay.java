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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView;

/**
 * A text overlay for use with a RecyclerView when the fast scroller is active.
 * <p>
 * We support two options:
 * <ol>
 * <li>Using a standard RecyclerView:
 * <ul>
 * <li>Set the standard FastScroller attributes on your RecyclerView xml</li>
 * <li>Use {@link RecyclerView#addItemDecoration} to add this class</li>
 * <li>WARNING: USES REFLECTION TO GET THE 'mState' MEMBER OF THE FastScroller!</li>
 * </ul>
 * </li>
 * <li>Use a custom {@link CFSRecyclerView}:
 * <ul>
 * <li>Must set {@link CFSRecyclerView} attributes for enabling the FastScroller.</li>
 * <li>Do NOT add this class as an ItemDecorator</li>
 * <li>Does NOT use reflection!</li>
 * <li>Uses a custom modified copy of the 'real' FastScroller with a hack in onDrawOver.</li>
 * </ul>
 * </li>
 * </ol>
 * <strong>
 * The current code in this app checks on the view NOT being a CFSRecyclerView
 * before it adds the decorator.
 * <p>
 * So switching between the two solutions is limited to editing the XML and making sure
 * {@link #___COMPILE_WITH_CFS_SUPPORT} is set to 'true'
 * </strong>
 * <p>
 * The text size is hardcoded to Large/Medium and the color to textColorPrimary
 */
public class FastScrollerOverlay
        extends RecyclerView.ItemDecoration {

    /**
     * false: we only support standard RecyclerView.
     * true: we support BOTH standard RecyclerView and CFSRecyclerView.
     */
    private static final boolean ___COMPILE_WITH_CFS_SUPPORT = true;

    /**
     * The overlay size is based on SIZE_MULTIPLIER * mPrimaryTextSize.
     * This allows for 2 lines of text with margins.
     */
    private static final int SIZE_MULTIPLIER = 3;

    /** Drawable for the overlay. */
    @NonNull
    private final Drawable mOverlayDrawable;
    /** The paint used for the text (e.g. color, size, style). */
    @NonNull
    private final TextPaint mTextPaint;
    /** Text size for 1st line. Also ued to base the overlay size on. */
    private final float mPrimaryTextSize;
    /** Text size for 2nd line. Smaller then 1st. */
    private final float mSecondaryTextSize;
    /** The current size. */
    private final int mOverlaySize;
    /** Used for temporary computations. */
    @NonNull
    private final RectF mTmpRect = new RectF();
    /** cached local storage for the decent. (avoid repeated native OS calls). */
    private final float mTextDecent;
    /** cached local storage for the bounds. */
    private Rect mOverlayBoundsSingleLine;
    /** cached local storage for the bounds. */
    private Rect mOverlayBoundsTwoLines;
    /** available space for the text, before eclipsing it. */
    private float mTextAvail;

    /** The x-coordinate of the origin of the text being drawn. */
    private float mTextX;
    /** The y-coordinate of the baseline of the text being drawn. */
    private float mTextY;
    /** previous width of the RecyclerView. Used to avoid recomputing coordinates. */
    private int oldw;
    /** previous height of the RecyclerView. Used to avoid recomputing coordinates. */
    private int oldh;

    /** Whether we have Reflection access to the scroller <strong>and</strong> the mState. */
    private Boolean mHasFastScroller;
    /** Reflection access. */
    private Object mFastScroller;
    /** Reflection access. */
    private Field mFastScrollerStateField;


    /**
     * Constructor.
     *
     * @param context            Current context
     * @param overlayDrawableRes drawable for the overlay.
     */
    public FastScrollerOverlay(@NonNull final Context context,
                               @DrawableRes final int overlayDrawableRes) {
        //noinspection ConstantConditions
        this(context, context.getDrawable(overlayDrawableRes));
    }

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param overlayDrawable drawable for the overlay.
     */
    public FastScrollerOverlay(@NonNull final Context context,
                               @NonNull final Drawable overlayDrawable) {

        mOverlayDrawable = overlayDrawable;

        mPrimaryTextSize = getTextSize(context, android.R.attr.textAppearanceLarge);
        mSecondaryTextSize = getTextSize(context, android.R.attr.textAppearanceMedium);
        @ColorInt
        int textColor = getColor(context, android.R.attr.textColorPrimary);

        // Determine the overlay size
        mOverlaySize = (int) (SIZE_MULTIPLIER * mPrimaryTextSize);

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(mPrimaryTextSize);
        mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTextPaint.setColor(textColor);

        mTextDecent = mTextPaint.descent();
    }

    @ColorInt
    private static int getColor(@NonNull final Context context,
                                @SuppressWarnings("SameParameterValue") @AttrRes final int attr) {
        Resources.Theme theme = context.getTheme();
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return context.getResources().getColor(tv.resourceId, theme);
    }

    private static int getTextSize(@NonNull final Context context,
                                   @AttrRes final int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);

        int[] textSizeAttr = new int[]{android.R.attr.textSize};
        int indexOfAttrTextSize = 0;
        TypedArray ta = context.obtainStyledAttributes(tv.data, textSizeAttr);
        int textSize = ta.getDimensionPixelSize(indexOfAttrTextSize, -1);
        ta.recycle();

        return textSize;
    }

    /**
     * Recalculate the overlay position and bounds.
     */
    private void computeOverlayCoordinates(final int w,
                                           final int h) {
        if (w == oldw && h == oldh) {
            return;
        }
        oldw = w;
        oldh = h;

        // 75% of total available width
        mTmpRect.left = w / 8f;
        mTmpRect.right = mTmpRect.left + w * 0.75f;
        // 10% from top
        mTmpRect.top = h / 10f;
        mTmpRect.bottom = mTmpRect.top + mOverlaySize;

        // same effect, but 1 param calls 4 param method, so skip a step.
        //mOverlayDrawable.setBounds(mOverlayBoundsSingleLine);
        mOverlayDrawable.setBounds((int) mTmpRect.left, (int) mTmpRect.top,
                                   (int) mTmpRect.right, (int) mTmpRect.bottom);

        mTextX = (int) (mTmpRect.left + mTmpRect.right) / 2f;
        mTextY = (int) (mTmpRect.bottom + mTmpRect.top) / 2f
                 + mOverlaySize / (SIZE_MULTIPLIER * 2f);

        // allow 80% of the space to be used.
        mTextAvail = (mTmpRect.right - mTmpRect.left) * 0.8f;

        // cache the bounds for 1 and 2 lines of text.
        mOverlayBoundsSingleLine = mOverlayDrawable.copyBounds();
        // Compute the size to fit 2 lines.
        int height = mOverlayBoundsSingleLine.bottom + mOverlayBoundsSingleLine.height() / 2;
        mOverlayBoundsTwoLines = new Rect(mOverlayBoundsSingleLine.left,
                                          mOverlayBoundsSingleLine.top,
                                          mOverlayBoundsSingleLine.right,
                                          height);
    }

    /**
     * Draw the overlay.
     *
     * @param c      Canvas to draw into
     * @param parent RecyclerView this ItemDecoration is drawing into
     * @param state  The current state of RecyclerView.
     *
     * @throws ClassCastException   if the adapter does not implement {@link SectionIndexerV2}
     *                              or if the LayoutManager is not a LinearLayoutManager.
     * @throws NullPointerException if the LayoutManager is not set.
     */
    @Override
    public void onDrawOver(@NonNull final Canvas c,
                           @NonNull final RecyclerView parent,
                           @NonNull final RecyclerView.State state)
            throws ClassCastException, NullPointerException {

        // onDrawOver seems to be called at init time, before we had a chance to set the adapter.
        SectionIndexerV2 sectionIndexerV2 = (SectionIndexerV2) parent.getAdapter();
        if (sectionIndexerV2 == null) {
            return;
        }

        if (___COMPILE_WITH_CFS_SUPPORT) {
            if (!fastScrollerIsDragging(parent) && (!(parent instanceof CFSRecyclerView))) {
                parent.smoothScrollBy(0, 1);
                return;
            }
        } else {
            if (!fastScrollerIsDragging(parent)) {
                parent.smoothScrollBy(0, 1);
                return;
            }
        }

        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) parent.getLayoutManager();
        @SuppressWarnings("ConstantConditions")
        int firstPosition = linearLayoutManager.findFirstCompletelyVisibleItemPosition();
        int lastPosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
        // get the one in the middle of the visible list.
        int position = firstPosition + ((lastPosition - firstPosition) / 2);

        // the unformatted line(s) of text to display.
        String[] rawText = sectionIndexerV2.getSectionText(parent.getContext(), position);
        if (rawText != null && rawText.length != 0) {
            // ellipsize and "un-null" as needed.
            String[] formattedText = new String[rawText.length];
            for (int i = 0; i < rawText.length; i++) {
                if (rawText[i] != null && !rawText[i].isEmpty()) {
                    formattedText[i] = TextUtils.ellipsize(rawText[i], mTextPaint, mTextAvail,
                                                           TextUtils.TruncateAt.END).toString();
                } else {
                    formattedText[i] = "";
                }
            }

            // set coordinates.
            computeOverlayCoordinates(parent.getWidth(), parent.getHeight());

            // Draw the overlay.
            if (formattedText.length == 1 || formattedText[1].isEmpty()) {
                // single line of text
                mOverlayDrawable.draw(c);
            } else {
                // Expand the box to fit the 2 lines.
                mOverlayDrawable.setBounds(mOverlayBoundsTwoLines);
                mOverlayDrawable.draw(c);
                // restore bounds
                mOverlayDrawable.setBounds(mOverlayBoundsSingleLine);
            }

            // Draw the 1st line with base of text at - descent : so it is vertically centred
            c.drawText(formattedText[0], mTextX, mTextY - mTextDecent, mTextPaint);

            if (formattedText.length > 1 && !formattedText[1].isEmpty()) {
                // Draw the 2nd line smaller than 1st.
                mTextPaint.setTextSize(mSecondaryTextSize);
                // but use the original text size for the y coordinate!
                c.drawText(formattedText[1], mTextX, mTextY + mPrimaryTextSize, mTextPaint);
                // restore text size
                mTextPaint.setTextSize(mPrimaryTextSize);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean fastScrollerIsDragging(@NonNull final RecyclerView recyclerView) {

        try {
            // first try to enable access if we did not try before.
            if (mHasFastScroller == null) {
                mHasFastScroller = enableFastScrollerAccess(recyclerView);
            }

            // if we did get access, check the state; STATE_DRAGGING == 2.
            if (mHasFastScroller) {
                return mFastScrollerStateField.getInt(mFastScroller) == 2;
            }

        } catch (@NonNull final IllegalAccessException e) {
            // should not happen.... yeah right!
            Logger.warn(this, "fastScrollerIsDragging", e.getLocalizedMessage());
            mHasFastScroller = false;
        }

        return false;
    }

    /**
     * Using reflection, find the decorator we need and enable access to it.
     *
     * @param recyclerView to zen.
     *
     * @return {@code true} if we got access as desired.
     */
    private boolean enableFastScrollerAccess(@NonNull final RecyclerView recyclerView) {
        try {

            for (int index = 0; index < recyclerView.getItemDecorationCount(); index++) {
                RecyclerView.ItemDecoration decor = recyclerView.getItemDecorationAt(index);
                if (decor.getClass()
                         .getName().equals("androidx.recyclerview.widget.FastScroller")) {
                    mFastScroller = decor;
                    mFastScrollerStateField = mFastScroller.getClass().getDeclaredField("mState");
                    mFastScrollerStateField.setAccessible(true);
                    return true;
                }
            }
        } catch (@NonNull final NoSuchFieldException | SecurityException e) {
            Logger.warn(this, "enableFastScrollerAccess", e.getLocalizedMessage());
        }

        return false;
    }

    /**
     * Replacement for {@link android.widget.SectionIndexer}.
     * <p>
     * This class is a better interface that just gets text for rows as needed rather
     * than having to build a huge index at start.
     */
    public interface SectionIndexerV2 {

        @Nullable
        String[] getSectionText(@NonNull Context context,
                                int position);
    }
}
