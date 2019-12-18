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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.FlattenedBooklist;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields.Field;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentModel;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume fields are empty by default when populating them manually.
 */
public class BookDetailsFragment
        extends BookBaseFragment {

    /** log tag. */
    public static final String TAG = "BookDetailsFragment";

    /** the covers. */
    private final ImageView[] mCoverView = new ImageView[2];
    /** Handles cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                populateLoanedToField(data.getString(DBDefinitions.KEY_LOANEE));
            } else {
                // we don't expect/implement any others.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "bookId=" + bookId
                               + "|fieldsChanged=" + fieldsChanged);
                }
            }
        }
    };

    /** The views. */
    private CompoundButton mIsAnthologyCbx;
    private CompoundButton mReadCheckbox;
    private CompoundButton mSignedCbx;
    private RatingBar mRatingView;
    private TextView mAuthorView;
    private TextView mSeriesView;
    private TextView mTitleView;
    private TextView mDescriptionView;
    private TextView mIsbnView;
    private TextView mGenreView;
    private TextView mBookshelvesView;
    private TextView mPricePaidView;
    private TextView mPriceListedView;
    private TextView mLoanedToView;
    private TextView mPagesView;
    private TextView mFormatView;
    private TextView mColorView;
    private TextView mLanguageView;
    private TextView mPublisherView;
    private TextView mDatePublishedView;
    private TextView mPrintRunView;
    private TextView mFirstPubView;
    private TextView mNotesView;
    private TextView mLocationView;
    private TextView mEditionView;
    private TextView mDateAcquiredView;
    private TextView mDateReadStartView;
    private TextView mDateReadEndView;
    /** Switch the user can flick to display/hide the TOC (if present). */
    private CompoundButton mTocButton;
    /** We display/hide the TOC header label as needed. */
    private View mTocLabelView;
    /** The TOC list. */
    private LinearLayout mTocView;
    /** The scroll view encompassing the entire fragment page. */
    private NestedScrollView mTopScrollView;
    /** Registered with the Activity to deliver us gestures. */
    private View.OnTouchListener mOnTouchListener;
    /** Handle next/previous paging in the flattened booklist; called by mOnTouchListener. */
    private GestureDetector mGestureDetector;
    /** Contains the flattened booklist for next/previous paging. */
    private BookDetailsFragmentModel mBookDetailsFragmentModel;
    /** Track on which cover view the context menu was used. */
    private int mContextMenuOpenIndex = -1;

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (LendBookDialogFragment.TAG.equals(childFragment.getTag())) {
            ((LendBookDialogFragment) childFragment).setListener(mBookChangedListener);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_details, container, false);
        mTopScrollView = view.findViewById(R.id.topScroller);

        mReadCheckbox = view.findViewById(R.id.cbx_read);
        mSignedCbx = view.findViewById(R.id.cbx_signed);
        mIsAnthologyCbx = view.findViewById(R.id.cbx_anthology);

        mAuthorView = view.findViewById(R.id.author);
        mSeriesView = view.findViewById(R.id.series);
        mBookshelvesView = view.findViewById(R.id.bookshelves);
        mPriceListedView = view.findViewById(R.id.price_listed);
        mPricePaidView = view.findViewById(R.id.price_paid);
        mLoanedToView = view.findViewById(R.id.loaned_to);
        mTitleView = view.findViewById(R.id.title);
        mDescriptionView = view.findViewById(R.id.description);
        mIsbnView = view.findViewById(R.id.isbn);
        mGenreView = view.findViewById(R.id.genre);
        mPagesView = view.findViewById(R.id.pages);
        mFormatView = view.findViewById(R.id.format);
        mColorView = view.findViewById(R.id.color);
        mLanguageView = view.findViewById(R.id.language);
        mPublisherView = view.findViewById(R.id.publisher);
        mDatePublishedView = view.findViewById(R.id.date_published);
        mPrintRunView = view.findViewById(R.id.print_run);
        mFirstPubView = view.findViewById(R.id.first_publication);
        mRatingView = view.findViewById(R.id.rating);
        mNotesView = view.findViewById(R.id.notes);
        mLocationView = view.findViewById(R.id.location);
        mEditionView = view.findViewById(R.id.edition);
        mDateAcquiredView = view.findViewById(R.id.date_acquired);
        mDateReadStartView = view.findViewById(R.id.read_start);
        mDateReadEndView = view.findViewById(R.id.read_end);

        mTocLabelView = view.findViewById(R.id.lbl_toc);
        mTocView = view.findViewById(R.id.toc);
        mTocButton = view.findViewById(R.id.toc_button);

        mCoverView[0] = view.findViewById(R.id.coverImage0);
        mCoverView[1] = view.findViewById(R.id.coverImage1);

        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {

        mBookDetailsFragmentModel = new ViewModelProvider(this).get(BookDetailsFragmentModel.class);

        // The book will get loaded, fields will be initialised and
        // the population logic will be triggered.
        super.onActivityCreated(savedInstanceState);

        mBookDetailsFragmentModel.init(mBookModel.getDb(), getArguments(),
                                       mBookModel.getBook().getId());

        FloatingActionButton fabButton = mHostActivity.findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_edit);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> startEditBook());

        // ENHANCE: should be replaced by a ViewPager2/FragmentStateAdapter
        // enable the listener for flings
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        mOnTouchListener = (v, event) -> mGestureDetector.onTouchEvent(event);

        // show/hide the TOC as the user flips the switch.
        mTocButton.setOnClickListener(v -> {
            // note that the button is explicitly (re)set.
            // If user clicks to fast it gets out of sync.
            if (mTocView.getVisibility() == View.VISIBLE) {
                // force a scroll; a manual scroll is no longer possible after the TOC closes.
                mTopScrollView.fullScroll(View.FOCUS_UP);
                mTocView.setVisibility(View.GONE);
                mTocButton.setChecked(false);

            } else {
                mTocView.setVisibility(View.VISIBLE);
                mTocButton.setChecked(true);
            }
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @CallSuper
    @Override
    public void onResume() {
        // The parent will kick of the process that triggers {@link #onLoadFieldsFromBook}.
        super.onResume();
        ((BookDetailsActivity) mHostActivity).registerOnTouchListener(mOnTouchListener);
    }

    @Override
    @CallSuper
    public void onPause() {
        ((BookDetailsActivity) mHostActivity).unregisterOnTouchListener(mOnTouchListener);
        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {

            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookModel.putResultData(data);
                    }
                    // onResume will display the changed book.
                    mBookModel.reload();
                }
                break;

            case UniqueId.REQ_BOOK_DUPLICATE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookModel.putResultData(data);

                        long id = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (id != 0) {
                            mBookModel.setBook(id);
                        }
                    }
                    // onResume will display the new book
                    mBookModel.reload();
                    //FIXME: swiping through the flattened booklist will not see
                    // the duplicated book until we go back to BoB.
                    // Easiest solution would be to remove the dup. option from this screen...
                }
                break;

            default: {
                boolean handled = false;
                // handle any cover image request codes
                if (mContextMenuOpenIndex >= 0) {
                    handled = mCoverHandler[mContextMenuOpenIndex]
                            .onActivityResult(requestCode, resultCode, data);
                }

                if (!handled) {
                    super.onActivityResult(requestCode, resultCode, data);
                }
                break;
            }
        }
    }

    @NonNull
    @Override
    Fields getFields() {
        return mBookDetailsFragmentModel.getFields();
    }

    @Override
    protected void initFields() {
        super.initFields();
        Fields fields = mBookDetailsFragmentModel.getFields();
        // already initialised ?
        if (!fields.isEmpty()) {
            return;
        }

        // A DateFieldFormatter can be shared between multiple fields.
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();

        // book fields
        fields.addString(R.id.title, mTitleView, DBDefinitions.KEY_TITLE);

        // defined, but fetched manually
        fields.addString(R.id.author, mAuthorView, "", DBDefinitions.KEY_FK_AUTHOR)
              .setShowHtml(true);

        // defined, but fetched manually
        fields.addString(R.id.series, mSeriesView, "", DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.addString(R.id.isbn, mIsbnView, DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.addString(R.id.description, mDescriptionView, DBDefinitions.KEY_DESCRIPTION)
              .setShowHtml(true)
              .setRelatedFields(R.id.lbl_description);

        fields.addBoolean(R.id.cbx_anthology, mIsAnthologyCbx, Book.HAS_MULTIPLE_WORKS)
              .setRelatedFields(R.id.lbl_anthology);
        // not added here: actual TOC which is non-text

        fields.addString(R.id.genre, mGenreView, DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        fields.addString(R.id.language, mLanguageView, DBDefinitions.KEY_LANGUAGE)
              .setFormatter(new Fields.LanguageFormatter())
              .setRelatedFields(R.id.lbl_language);

        //noinspection ConstantConditions
        fields.addString(R.id.pages, mPagesView, DBDefinitions.KEY_PAGES)
              .setFormatter(new Fields.PagesFormatter(getContext()));

        fields.addString(R.id.format, mFormatView, DBDefinitions.KEY_FORMAT);

        fields.addString(R.id.color, mColorView, DBDefinitions.KEY_COLOR);

        fields.addString(R.id.publisher, mPublisherView, DBDefinitions.KEY_PUBLISHER);

        fields.addString(R.id.date_published, mDatePublishedView, DBDefinitions.KEY_DATE_PUBLISHED)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_date_published);

        fields.addString(R.id.first_publication, mFirstPubView,
                         DBDefinitions.KEY_DATE_FIRST_PUBLICATION)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_first_publication);

        fields.addString(R.id.print_run, mPrintRunView, DBDefinitions.KEY_PRINT_RUN)
              .setRelatedFields(R.id.lbl_print_run);

        // defined, but fetched manually
        fields.addMonetary(R.id.price_listed, mPriceListedView, "", DBDefinitions.KEY_PRICE_LISTED)
              .setRelatedFields(R.id.price_listed_currency, R.id.lbl_price_listed);

        // Personal fields
        fields.addString(R.id.date_acquired, mDateAcquiredView, DBDefinitions.KEY_DATE_ACQUIRED)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_date_acquired);

        fields.addLong(R.id.edition, mEditionView, DBDefinitions.KEY_EDITION_BITMASK)
              .setFormatter(new Fields.BitMaskFormatter(Book.getEditions(getContext())))
              .setRelatedFields(R.id.lbl_edition);

        fields.addString(R.id.location, mLocationView, DBDefinitions.KEY_LOCATION)
              .setRelatedFields(R.id.lbl_location, R.id.lbl_location_long);

        fields.addFloat(R.id.rating, mRatingView, DBDefinitions.KEY_RATING)
              .setRelatedFields(R.id.lbl_rating);

        fields.addString(R.id.notes, mNotesView, DBDefinitions.KEY_PRIVATE_NOTES)
              .setShowHtml(true)
              .setRelatedFields(R.id.lbl_notes);

        fields.addString(R.id.read_start, mDateReadStartView, DBDefinitions.KEY_READ_START)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_read_start);

        fields.addString(R.id.read_end, mDateReadEndView, DBDefinitions.KEY_READ_END)
              .setFormatter(dateFormatter)
              .setRelatedFields(R.id.lbl_read_end);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.addBoolean(R.id.cbx_read, mReadCheckbox, DBDefinitions.KEY_READ);

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        fields.addBoolean(R.id.cbx_signed, mSignedCbx, DBDefinitions.KEY_SIGNED)
              .setRelatedFields(R.id.lbl_signed);

        // defined, but fetched manually
        fields.addMonetary(R.id.price_paid, mPricePaidView, "", DBDefinitions.KEY_PRICE_PAID)
              .setRelatedFields(R.id.price_paid_currency, R.id.lbl_price_paid);

        // defined, but fetched manually
        fields.addString(R.id.bookshelves, mBookshelvesView, "", DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);

        // defined, but fetched manually
//        fields.addString(R.id.loaned_to, mLoanedToView, "", DBDefinitions.KEY_LOANEE);
    }

    /**
     * <p>
     * At this point we're told to load our local (to the fragment) fields from the Book.
     * </p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        populateAuthorListField();
        populateSeriesListField();
        populateBookshelvesField();
        populatePriceFields();

        // handle non-text fields
        populateLoanedToField(mBookModel.getLoanee());
        populateToc();

        setupCoverViews(0, ImageUtils.SCALE_LARGE);
        setupCoverViews(1, ImageUtils.SCALE_SMALL);

        // hide unwanted and empty fields
        showOrHideFields(true);

        // Hide the Publication section label if none of the publishing fields are shown.
        setSectionLabelVisibility(R.id.lbl_publication_section,
                                  R.id.publisher,
                                  R.id.date_published,
                                  R.id.price_listed,
                                  R.id.first_publication);

        // Hide the Notes label if none of the notes fields are shown.
        setSectionLabelVisibility(R.id.lbl_notes,
                                  R.id.notes,
                                  R.id.lbl_edition,
                                  R.id.lbl_signed,
                                  R.id.lbl_date_acquired,
                                  R.id.lbl_price_paid,
                                  R.id.lbl_read_start,
                                  R.id.lbl_read_end,
                                  R.id.lbl_location);
    }

    private void setupCoverViews(final int cIdx,
                                 @ImageUtils.Scale final int scale) {

        mCoverHandler[cIdx] = new CoverHandler(this, mProgressBar,
                                               mBookModel.getBook(), mIsbnView, cIdx,
                                               mCoverView[cIdx], scale);
        mCoverHandler[cIdx].setImage();

        // Allow zooming by clicking on the image;
        // If there is no actual image, bring up the context menu instead.
        mCoverView[cIdx].setOnClickListener(v -> {
            File image = mCoverHandler[cIdx].getCoverFile();
            if (image.exists()) {
                ZoomedImageDialogFragment.show(getParentFragmentManager(), image);
            } else {
                mContextMenuOpenIndex = cIdx;
                mCoverHandler[cIdx].onCreateContextMenu();
            }
        });

        mCoverView[cIdx].setOnLongClickListener(v -> {
            mContextMenuOpenIndex = cIdx;
            mCoverHandler[cIdx].onCreateContextMenu();
            return true;
        });
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.co_book, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        Book book = mBookModel.getBook();

        boolean isSaved = book.getId() > 0;
        boolean isRead = book.getBoolean(Book.IS_READ);
        boolean isAvailable = mBookModel.isAvailable();

        menu.findItem(R.id.MENU_BOOK_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isSaved && isRead);

        //noinspection ConstantConditions
        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(GoodreadsManager.isShowSyncMenus(getContext()));

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        boolean lendingIsUsed = App.isUsed(DBDefinitions.KEY_LOANEE);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD)
            .setVisible(lendingIsUsed && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE)
            .setVisible(lendingIsUsed && isSaved && !isAvailable);

        MenuHandler.prepareOptionalMenus(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        Book book = mBookModel.getBook();

        switch (item.getItemId()) {

            case R.id.MENU_BOOK_EDIT: {
                startEditBook();
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                String title = book.getString(DBDefinitions.KEY_TITLE);
                List<Author> authors = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                //noinspection ConstantConditions
                StandardDialogs.deleteBookAlert(getContext(), title, authors, () -> {
                    mBookModel.deleteBook(getContext());

                    Intent resultData = mBookModel.getActivityResultData();
                    mHostActivity.setResult(Activity.RESULT_OK, resultData);
                    mHostActivity.finish();
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                Intent dupIntent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;
            }
            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle 'read' status of the book
                Field<Boolean> field = getFields().getField(R.id.cbx_read);
                field.setValue(mBookModel.toggleRead());
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                //noinspection ConstantConditions
                LendBookDialogFragment.newInstance(getContext(), book)
                                      .show(getChildFragmentManager(), LendBookDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mBookModel.deleteLoan();
                populateLoanedToField(null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                //noinspection ConstantConditions
                startActivity(Intent.createChooser(book.getShareBookIntent(getContext()),
                                                   getString(R.string.menu_share_this)));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG)
                        .show();
                new SendOneBookTask(book.getId(), mBookModel.getGoodreadsTaskListener())
                        .execute();
                return true;
            }
            /* ********************************************************************************** */

            default:
                //noinspection ConstantConditions
                if (MenuHandler.handleOpenOnWebsiteMenus(getContext(), item, book)) {
                    return true;
                }

                // MENU_BOOK_UPDATE_FROM_INTERNET handled in super
                return super.onOptionsItemSelected(item);
        }
    }

    private void startEditBook() {
        Intent editIntent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, mBookModel.getBook().getId());
        startActivityForResult(editIntent, UniqueId.REQ_BOOK_EDIT);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_LOAN_DELETE:
                mBookModel.deleteLoan();
                populateLoanedToField(null);
                return true;

            default:
                return super.onContextItemSelected(menuItem);
        }
    }

    /**
     * The author field is a single csv String.
     */
    private void populateAuthorListField() {
        Book book = mBookModel.getBook();

        ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        Field<String> field = getFields().getField(R.id.author);
        field.setValue(Csv.join("<br>", list, true, "• ",
                                this::formatAuthor));
    }

    /**
     * The formatter for the {@link Csv#join} used by {@link #populateAuthorListField()}.
     *
     * @param author to format
     *
     * @return HTML formatted author with optional type
     */
    private String formatAuthor(@NonNull final Author author) {
        final Context context = getContext();
        //noinspection ConstantConditions
        String authorLabel = author.getLabel(context);
        if (App.isUsed(DBDefinitions.KEY_AUTHOR_TYPE)) {
            String type = author.getTypeLabels(context);
            if (!type.isEmpty()) {
                authorLabel += " <small><i>" + type + "</i></small>";
            }
        }

        return authorLabel;
    }

    /**
     * The Series field is a single String with line-breaks between multiple Series.
     * Each line will be prefixed with a "• "
     */
    private void populateSeriesListField() {
        Book book = mBookModel.getBook();

        ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);

        Field<String> field = getFields().getField(R.id.series);
        //noinspection ConstantConditions
        field.setValue(Csv.join("\n", list, true, "• ",
                                series -> series.getLabel(getContext())));
    }

    /**
     * The bookshelves field is a single csv String.
     */
    private void populateBookshelvesField() {
        Book book = mBookModel.getBook();

        ArrayList<Bookshelf> list = book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        Field<String> field = getFields().getField(R.id.bookshelves);
        field.setValue(Csv.join(", ", list, Bookshelf::getName));
    }

    /**
     * We need to use the <strong>current</strong> currency code, so we cannot define (easily)
     * the formatter in {@link #initFields()}.
     * <p>
     * Using a formatter object is a little overkill, but this leaves future changes easier.
     */
    private void populatePriceFields() {
        Book book = mBookModel.getBook();

        Fields.MonetaryFormatter listedFormatter = new Fields.MonetaryFormatter()
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        getFields().getField(R.id.price_listed)
                   .setFormatter(listedFormatter)
                   .setValue(book.getDouble(DBDefinitions.KEY_PRICE_LISTED));

        Fields.MonetaryFormatter paidFormatter = new Fields.MonetaryFormatter()
                .setCurrencyCode(book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));
        getFields().getField(R.id.price_paid)
                   .setFormatter(paidFormatter)
                   .setValue(book.getDouble(DBDefinitions.KEY_PRICE_PAID));
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * <strong>Note:</strong> we pass in the loanee and handle visibility local as this
     * method can be called from anywhere.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLoanedToField(@Nullable final String loanee) {
//        Field<String> field = getFields().getField(R.id.loaned_to);
        if (loanee != null && !loanee.isEmpty()) {
            mLoanedToView.setText(getString(R.string.lbl_loaned_to_name, loanee));
            mLoanedToView.setVisibility(View.VISIBLE);
            mLoanedToView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /**
                 * yes, icons are not supported here, but:
                 * TODO: convert to MenuPicker context menu.... if I can be bothered.
                 */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull
                                                final ContextMenu.ContextMenuInfo menuInfo) {
                    Resources r = getResources();
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                             r.getInteger(R.integer.MENU_ORDER_LENDING),
                             R.string.menu_loan_return_book)
                        .setIcon(R.drawable.ic_people);
                }
            });
        } else {
            mLoanedToView.setVisibility(View.GONE);
            mLoanedToView.setText("");
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc() {
        Book book = mBookModel.getBook();

        // we can get called more than once (when user moves sideways to another book),
        // so clear and hide/disable the view before populating it.
        // Actual visibility is handled after building the list.
        mTocView.removeAllViews();
        mTocView.setVisibility(View.GONE);
        mTocButton.setChecked(false);

        ArrayList<TocEntry> tocList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        if (!tocList.isEmpty()) {
            Context context = getContext();
            for (TocEntry item : tocList) {
                View rowView = getLayoutInflater().inflate(R.layout.row_toc_entry_with_author,
                                                           mTocView, false);

                TextView titleView = rowView.findViewById(R.id.title);
                TextView authorView = rowView.findViewById(R.id.author);
                TextView firstPubView = rowView.findViewById(R.id.year);

                titleView.setText(item.getTitle());

                // optional
                if (authorView != null) {
                    //noinspection ConstantConditions
                    authorView.setText(item.getAuthor().getLabel(context));
                }
                // optional
                if (firstPubView != null) {
                    String year = item.getFirstPublication();
                    if (year.isEmpty()) {
                        firstPubView.setVisibility(View.GONE);
                    } else {
                        firstPubView.setVisibility(View.VISIBLE);
                        //noinspection ConstantConditions
                        firstPubView.setText(context.getString(R.string.brackets, year));
                    }
                }
                mTocView.addView(rowView);
            }

            mTocLabelView.setVisibility(View.VISIBLE);
            mTocButton.setVisibility(View.VISIBLE);

        } else {
            mTocLabelView.setVisibility(View.GONE);
            mTocButton.setVisibility(View.GONE);
        }
    }


    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     *
     * <a href="https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures">
     * https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures</a>
     */
    private class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(@NonNull final MotionEvent e1,
                               @NonNull final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {

            FlattenedBooklist fbl = mBookDetailsFragmentModel.getFlattenedBooklist();
            if (fbl == null) {
                return false;
            }

            // Make sure we have considerably more X-velocity than Y-velocity;
            // otherwise it might be a scroll.
            if (Math.abs(velocityX / velocityY) > 2) {
                boolean moved;
                // Work out which way to move, and do it.
                if (velocityX > 0) {
                    moved = fbl.movePrev();
                } else {
                    moved = fbl.moveNext();
                }

                if (moved) {
                    long bookId = fbl.getBookId();
                    // only reload if it's a new book
                    if (bookId != mBookModel.getBook().getId()) {
                        mBookModel.moveTo(bookId);
                        loadFields();
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean onDown(@NonNull final MotionEvent e) {
            return true;
        }
    }
}
