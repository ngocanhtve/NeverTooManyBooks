/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataEditor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.date.DatePickerResultsListener;
import com.hardbacknutter.nevertoomanybooks.dialogs.date.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.utils.DateFormatUtils;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;

public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor<Book> {

    /** Log tag. */
    private static final String TAG = "EditBookBaseFragment";

    /** Tag for MaterialDatePicker. */
    private static final String TAG_DATE_PICKER_SINGLE = TAG + ":datePickerSingle";
    /** Tag for MaterialDatePicker. */
    private static final String TAG_DATE_PICKER_RANGE = TAG + ":datePickerRange";

    /** The view model. */
    EditBookFragmentViewModel mFragmentVM;

    /** Dialog listener (strong reference). */
    private final DatePickerResultsListener
            mPartialDatePickerResultsListener = (year, month, day) -> {
        final int fieldId = mFragmentVM.getCurrentDialogFieldId()[0];
        String date = DateFormatUtils.isoPartialDate(year, month, day);
        onDateSet(fieldId, date);
    };

    /** Dialog listener (strong reference). */
    private final MaterialPickerOnPositiveButtonClickListener<Long>
            mDatePickerListener = selection -> {
        final int fieldId = mFragmentVM.getCurrentDialogFieldId()[0];
        onDateSet(fieldId, selection);
    };

    /** Dialog listener (strong reference). */
    private final MaterialPickerOnPositiveButtonClickListener<Pair<Long, Long>>
            mDateRangePickerListener = selection -> {
        final int fieldIdStart = mFragmentVM.getCurrentDialogFieldId()[0];
        onDateSet(fieldIdStart, selection.first);
        final int fieldIdEnd = mFragmentVM.getCurrentDialogFieldId()[1];
        onDateSet(fieldIdEnd, selection.second);
    };

    /**
     * Convenience wrapper.
     * <p>
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    <T, V extends View> Field<T, V> getField(@IdRes final int id)
            throws IllegalArgumentException {
        return getFields().getField(id);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String fragmentTag = getTag();
        Objects.requireNonNull(fragmentTag, ErrorMsg.NULL_FRAGMENT_TAG);

        //noinspection ConstantConditions
        mFragmentVM = new ViewModelProvider(getActivity())
                .get(fragmentTag, EditBookFragmentViewModel.class);

        mFragmentVM.init();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFragmentVM.onUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mFragmentVM.onNeedsGoodreads().observe(getViewLifecycleOwner(), needs -> {
            if (needs != null && needs) {
                final Context context = getContext();
                //noinspection ConstantConditions
                RequestAuthTask.prompt(context, mFragmentVM.getGoodreadsTaskListener(context));
            }
        });

        if (mFragmentVM.shouldInitFields()) {
            onInitFields(getFields());
            mFragmentVM.setFieldsAreInitialised();
        }
    }

    /**
     * Init all Fields, and add them the fields collection.
     * <p>
     * Note that Field views are <strong>NOT AVAILABLE</strong>.
     * <p>
     * The fields will be populated in {@link #onPopulateViews}
     *
     * @param fields the local fields collection to add your fields to
     */
    @CallSuper
    void onInitFields(@NonNull final Fields fields) {
    }

    @Override
    public void onResume() {

        // Not sure this is really needed; but it does no harm.
        // In theory, the editing fragment can trigger an internet search,
        // which after it comes back, brings along new data to be transferred to the book.
        // BUT: that new data would not be in the fragment arguments?
        //TODO: double check having book-data bundle in onResume.
        if (mBookViewModel.getBook().isNew()) {
            //noinspection ConstantConditions
            mBookViewModel.addFieldsFromBundle(getContext(), getArguments());
        }

        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);

        if (childFragment instanceof PartialDatePickerDialogFragment) {
            ((PartialDatePickerDialogFragment) childFragment)
                    .setListener(mPartialDatePickerResultsListener);

        } else if (TAG_DATE_PICKER_SINGLE.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((MaterialDatePicker<Long>) childFragment)
                    .addOnPositiveButtonClickListener(mDatePickerListener);

        } else if (TAG_DATE_PICKER_RANGE.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((MaterialDatePicker<Pair<Long, Long>>) childFragment)
                    .addOnPositiveButtonClickListener(mDateRangePickerListener);
        }
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * This is always done, even when the user 'cancel's the edit.
     * The latter will result in a "are you sure" where they can 'cancel the cancel'
     * and continue with all data present.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        // Avoid saving a 2nd time after the user has initiated saving.
        if (!mBookViewModel.isSaved()) {
            onSaveFields(mBookViewModel.getBook());

            //noinspection ConstantConditions
            mBookViewModel.setUnfinishedEdits(getTag(), hasUnfinishedEdits());
        }
        super.onPause();
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Field} into the given {@link DataManager} e.g. the {@link Book}
     * <p>
     * Called from {@link #onPause()}.
     * Override as needed.
     * <p>
     * {@inheritDoc}
     */
    @CallSuper
    @Override
    public void onSaveFields(@NonNull final Book book) {
        getFields().getAll(book);
    }

    /**
     * Setup an adapter for the AutoCompleteTextView, using the (optional) formatter.
     *
     * @param fieldId view to connect
     * @param list    with auto complete values
     */
    void addAutocomplete(@IdRes final int fieldId,
                         @NonNull final List<String> list) {
        final Field<?, ?> field = getField(fieldId);
        // only bother when it's in use and we have a list
        //noinspection ConstantConditions
        if (field.isUsed(getContext()) && !list.isEmpty()) {
            final AutoCompleteTextView view = (AutoCompleteTextView) field.getAccessor().getView();
            //noinspection unchecked
            final Fields.FormattedDiacriticArrayAdapter adapter =
                    new Fields.FormattedDiacriticArrayAdapter(
                            getContext(), list,
                            (FieldFormatter<String>) field.getAccessor().getFormatter());
            //noinspection ConstantConditions
            view.setAdapter(adapter);
        }
    }

    /**
     * Setup a date picker for selecting a (full) date range.
     * <p>
     * Clicking on the start-date field will allow the user to set just the start-date.
     * Clicking on the end-date will prompt to select both the start and end dates.
     * <p>
     * If only one field is used, this method diverts to {@link #addDatePicker}.
     *
     * @param dialogTitleIdSpan  title of the dialog box if both start and end-dates are used.
     * @param dialogTitleIdStart title of the dialog box if the end-date is not in use
     * @param fieldStartDate     to setup for the start-date
     * @param dialogTitleIdEnd   title of the dialog box if the start-date is not in use
     * @param fieldEndDate       to setup for the end-date
     * @param todayIfNone        if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addDateRangePicker(@StringRes final int dialogTitleIdSpan,
                            @StringRes final int dialogTitleIdStart,
                            @NonNull final Field<String, TextView> fieldStartDate,
                            @StringRes final int dialogTitleIdEnd,
                            @NonNull final Field<String, TextView> fieldEndDate,
                            final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (fieldStartDate.isUsed(getContext()) && fieldEndDate.isUsed(getContext())) {

            // single date picker for the start-date
            addDatePicker(fieldStartDate, dialogTitleIdStart, true);

            // date-span for the end-date
            //noinspection ConstantConditions
            fieldEndDate.getAccessor().getView().setOnClickListener(v -> {
                Long timeStart = DateUtils.parseTime(fieldStartDate.getAccessor().getValue(),
                                                     todayIfNone);
                Long timeEnd = DateUtils.parseTime(fieldEndDate.getAccessor().getValue(),
                                                   todayIfNone);
                // sanity check
                if (timeStart != null && timeEnd != null && timeStart > timeEnd) {
                    Long tmp = timeStart;
                    timeStart = timeEnd;
                    timeEnd = tmp;
                }

                final MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder
                        .dateRangePicker()
                        .setTitleText(dialogTitleIdSpan)
                        .setSelection(new Pair<>(timeStart, timeEnd))
                        .build();
                mFragmentVM.setCurrentDialogFieldId(fieldStartDate.getId(), fieldEndDate.getId());
                picker.show(getChildFragmentManager(), TAG_DATE_PICKER_RANGE);
            });

        } else if (fieldStartDate.isUsed(getContext())) {
            addDatePicker(fieldStartDate, dialogTitleIdStart, todayIfNone);
        } else if (fieldEndDate.isUsed(getContext())) {
            addDatePicker(fieldEndDate, dialogTitleIdEnd, todayIfNone);
        }
    }

    /**
     * Setup a date picker for selecting a single, full date.
     *
     * @param field         to setup
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, we'll default to today's date.
     */
    void addDatePicker(@NonNull final Field<String, TextView> field,
                       @StringRes final int dialogTitleId,
                       final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> {
                final Long selection = DateUtils.parseTime(field.getAccessor().getValue(),
                                                           todayIfNone);
                final MaterialDatePicker<Long> picker = MaterialDatePicker.Builder
                        .datePicker()
                        .setTitleText(dialogTitleId)
                        .setSelection(selection)
                        .build();
                mFragmentVM.setCurrentDialogFieldId(field.getId());
                picker.show(getChildFragmentManager(), TAG_DATE_PICKER_SINGLE);
            });
        }
    }

    /**
     * Setup a date picker for selecting a partial date.
     *
     * @param field         to setup
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, we'll default to today's date.
     */
    @SuppressWarnings("SameParameterValue")
    void addPartialDatePicker(@NonNull final Field<String, TextView> field,
                              @StringRes final int dialogTitleId,
                              final boolean todayIfNone) {
        //noinspection ConstantConditions
        if (field.isUsed(getContext())) {
            //noinspection ConstantConditions
            field.getAccessor().getView().setOnClickListener(v -> {
                DialogFragment picker = PartialDatePickerDialogFragment.newInstance(
                        dialogTitleId, field.getAccessor().getValue(), todayIfNone);
                mFragmentVM.setCurrentDialogFieldId(field.getId());
                picker.show(getChildFragmentManager(), PartialDatePickerDialogFragment.TAG);
            });
        }
    }

    private void onDateSet(@IdRes final int fieldId,
                           @Nullable final Long selection) {
        String value;
        if (selection != null) {
            value = DateFormatUtils.isoLocalDate(selection);
        } else {
            value = "";
        }
        onDateSet(fieldId, value);
    }

    private void onDateSet(@IdRes final int fieldId,
                           @NonNull final String value) {
        Field<String, TextView> field = getField(fieldId);
        field.getAccessor().setValue(value);
        field.onChanged(true);

        if (fieldId == R.id.read_end) {
            getField(R.id.cbx_read).getAccessor().setValue(true);
        }
    }
}
