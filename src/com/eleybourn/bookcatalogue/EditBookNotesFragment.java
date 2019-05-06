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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;

/**
 * This class is called by {@link EditBookFragment} and displays the Notes Tab.
 */
public class EditBookNotesFragment
        extends EditBookBaseFragment
        implements
        CheckListEditorDialogFragment.OnCheckListEditorResultsListener<Integer>,
        PartialDatePickerDialogFragment.OnPartialDatePickerResultsListener {

    /** Fragment manager tag. */
    public static final String TAG = EditBookNotesFragment.class.getSimpleName();


    //<editor-fold desc="Fragment startup">

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * <ul>
     * <li>{@link #onLoadFieldsFromBook} from base class onResume</li>
     * <li>{@link #onSaveFieldsToBook} from base class onPause</li>
     * </ul>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        ViewUtils.fixFocusSettings(getView());
    }

    @CallSuper
    @Override
    protected void initFields() {
        super.initFields();

        // multiple use
        Fields.FieldFormatter dateFormatter = new Fields.DateFieldFormatter();
        // ENHANCE: Add a partial date validator. Or not.
        //FieldValidator blankOrDateValidator = new Fields.OrValidator(
        //     new Fields.BlankValidator(), new Fields.DateValidator());

        Field field;

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.read, DBDefinitions.KEY_READ)
               .getView().setOnClickListener(v -> {
            // when user sets 'read', also set the read-end date to today (unless set before)
            Checkable cb = (Checkable) v;
            if (cb.isChecked()) {
                Field end = mFields.getField(R.id.read_end);
                if (end.getValue().toString().trim().isEmpty()) {
                    end.setValue(DateUtils.localSqlDateForToday());
                }
            }
        });

        // no DataAccessor needed, the Fields CheckableAccessor takes care of this.
        mFields.add(R.id.signed, DBDefinitions.KEY_SIGNED);

        mFields.add(R.id.rating, DBDefinitions.KEY_RATING);

        mFields.add(R.id.notes, DBDefinitions.KEY_NOTES);

        mFields.add(R.id.price_paid, DBDefinitions.KEY_PRICE_PAID);
        field = mFields.add(R.id.price_paid_currency, DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        initValuePicker(field, R.string.lbl_currency, R.id.btn_price_paid_currency,
                        mBookBaseFragmentModel.getPricePaidCurrencyCodes());

        field = mFields.add(R.id.location, DBDefinitions.KEY_LOCATION);
        initValuePicker(field, R.string.lbl_location, R.id.btn_location,
                        mBookBaseFragmentModel.getLocations());

        field = mFields.add(R.id.edition, DBDefinitions.KEY_EDITION_BITMASK)
                       .setFormatter(new Fields.BookEditionsFormatter());
        //noinspection ConstantConditions
        initCheckListEditor(getTag(), field, R.string.lbl_edition,
                            () -> mBookBaseFragmentModel.getBook().getEditableEditionList());

        field = mFields.add(R.id.date_acquired, DBDefinitions.KEY_DATE_ACQUIRED)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(getTag(), field, R.string.lbl_date_acquired, true);

        field = mFields.add(R.id.read_start, DBDefinitions.KEY_READ_START)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(getTag(), field, R.string.lbl_read_start, true);

        field = mFields.add(R.id.read_end, DBDefinitions.KEY_READ_END)
                       .setFormatter(dateFormatter);
        initPartialDatePicker(getTag(), field, R.string.lbl_read_end, true);

        mFields.addCrossValidator(new Fields.FieldCrossValidator() {
            private static final long serialVersionUID = -3288341939109142352L;

            public void validate(@NonNull final Fields fields,
                                 @NonNull final Bundle values)
                    throws ValidatorException {
                String start = values.getString(DBDefinitions.KEY_READ_START);
                if (start == null || start.isEmpty()) {
                    return;
                }
                String end = values.getString(DBDefinitions.KEY_READ_END);
                if (end == null || end.isEmpty()) {
                    return;
                }
                if (start.compareToIgnoreCase(end) > 0) {
                    throw new ValidatorException(R.string.vldt_read_start_after_end);
                }
            }
        });
    }

    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(final boolean setAllFrom) {
        super.onLoadFieldsFromBook(setAllFrom);

        // Restore default visibility
        showHideFields(false);
    }

    //</editor-fold>

    //<editor-fold desc="Field editors callbacks">
    @Override
    public void onCheckListEditorSave(final int destinationFieldId,
                                      @NonNull final List<CheckListItem<Integer>> list) {

        if (destinationFieldId == R.id.edition) {
            mBookBaseFragmentModel.getBook().putEditions(new Book.EditionCheckListItem().extractList(list));
            mFields.getField(destinationFieldId)
                   .setValue(mBookBaseFragmentModel.getBook().getString(DBDefinitions.KEY_EDITION_BITMASK));
        }
    }

    @Override
    public void onPartialDatePickerSave(final int destinationFieldId,
                                        @Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        mFields.getField(destinationFieldId).setValue(DateUtils.buildPartialDate(year, month, day));
    }

    //</editor-fold>

}
