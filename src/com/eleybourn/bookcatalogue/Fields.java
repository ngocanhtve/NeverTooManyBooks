/*
 * @copyright 2011 Philip Warner
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

import android.app.Activity;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 *
 * Features provides are:
 * <ul>
 * <li> handling of visibility via preferences
 * <li> handling of 'group' visibility via the 'group' property of a field.
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work as
 * expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.
 * <li> Custom data accessors and formatters to provide application-specific data rules.
 * <li> validation: calling validate will call user-defined or predefined validation routines and
 * return success or failure. The text of any exceptions will be available after the call.
 * <li> simplified loading of data from a Cursor.
 * <li> simplified extraction of data to a {@link ContentValues} collection.
 * </ul>
 *
 * Formatters and Accessors
 *
 * It is up to each accessor to decide what to do with any formatters defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 *
 * On a set(), the accessor should call format() function then apply the value
 *
 * On a get() the accessor should retrieve the value and apply the extract() function.
 *
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 *
 * Data Flow
 *
 * Data flows to and from a view as follows:
 * IN  (with formatter): (Cursor or other source) -> format() (via accessor) -> transform (in accessor) -> View
 * IN  ( no formatter ): (Cursor or other source) -> transform (in accessor) -> View
 * OUT (with formatter): (Cursor or other source) -> transform (in accessor) -> extract (via accessor) -> validator -> (ContentValues or Object)
 * OUT ( no formatter ): (Cursor or other source) -> transform (in accessor) -> validator -> (ContentValues or Object)
 *
 * Usage Note:
 *
 * 1. Which Views to Add?
 *
 * It is not necessary to add every control to the 'Fields' collection, but as a general rule
 * any control that displays data from a database, or related derived data, or labels for such
 * data should be added.
 *
 * Typical controls NOT added, are 'Save' and 'Cancel' buttons, or other controls whose
 * interactions are purely functional.
 *
 * 2. Handlers?
 *
 * The add() method of Fields returns a new {@link Field} object which exposes the 'View' member;
 * this can be used to perform view-specific tasks like setting onClick() handlers.
 *
 * TODO: Rationalize the use of this collection with the {@link DataManager}.
 *
 * @author Philip Warner
 */
public class Fields extends ArrayList<Fields.Field> {
    public static final long serialVersionUID = 1L;
    /** Prefix for all preferences */
    private final static String PREFS_FIELD_VISIBILITY = "field_visibility_";

    /** The activity related to this object. */
    @NonNull
    private final FieldsContext mContext;

    /** The last validator exception caught by this object */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener = null;

    /**
     * Constructor
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    Fields(@NonNull final Fragment fragment) {
        super();
        mContext = new FragmentContext(fragment);
    }

    /**
     * Constructor
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    @SuppressWarnings("unused")
    Fields(@NonNull final Activity activity) {
        super();
        mContext = new ActivityContext(activity);
    }

    /**
     * This should NEVER happen, but it does. See Issue 505. So we need more info about why & when.
     *
     * // Allow for the (apparent) possibility that the view may have been removed due
     * // to a tab change or similar. See Issue 505.
     *
     * Every field MUST have an associated View object, but sometimes it is not found.
     * When not found, the app crashes.
     *
     * The following code is to help diagnose these cases, not avoid them.
     *
     * NOTE: 	This does NOT entirely fix the problem, it gathers debug info.
     * but we have implemented one work-around
     *
     * Work-around #1:
     *
     * It seems that sometimes the afterTextChanged() event fires after the text field
     * is removed from the screen. In this case, there is no need to synchronize the values
     * since the view is gone.
     */
    private static void debugNullView(@NonNull final Field field) {
        String msg = "NULL View: col=" + field.column + ", id=" + field.id + ", group=" + field.group;
        Fields fields = field.getFields();
        if (fields == null) {
            msg += ". Fields is NULL.";
        } else {
            msg += ". Fields is valid.";
            FieldsContext context = fields.getContext();
            msg += ". Context is " + context.getClass().getCanonicalName() + ".";
            Object ownerContext = context.dbgGetOwnerContext();
            if (ownerContext == null) {
                msg += ". Owner is NULL.";
            } else {
                msg += ". Owner is " + ownerContext.getClass().getCanonicalName() + " (" + ownerContext + ")";
            }
        }
        throw new IllegalStateException("Unable to get associated View object\n" + msg);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isVisible(@NonNull final String fieldName) {
        return BookCatalogueApp.Prefs.getBoolean(PREFS_FIELD_VISIBILITY + fieldName, true);
    }

    public static void setVisibility(final String fieldName, final boolean isVisible) {
        BookCatalogueApp.Prefs.putBoolean(PREFS_FIELD_VISIBILITY + fieldName, isVisible);
    }

    /**
     * @param listener the listener for field changes
     *
     * @return original listener
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @Nullable
    public AfterFieldChangeListener setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        AfterFieldChangeListener old = mAfterFieldChangeListener;
        mAfterFieldChangeListener = listener;
        return old;
    }

    /**
     * Accessor for related Activity
     *
     * @return Activity for this collection.
     */
    @NonNull
    private FieldsContext getContext() {
        return mContext;
    }

    /**
     * Provides access to the underlying arrays get() method.
     */
    @SuppressWarnings("unused")
    @CallSuper
    public Field getItem(final int index) {
        return super.get(index);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId        Layout ID
     * @param sourceColumn   Source DB column (can be blank)
     * @param fieldValidator Field Validator (can be null)
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn,
                     @Nullable final FieldValidator fieldValidator) {
        return add(fieldId, sourceColumn, sourceColumn, fieldValidator, null);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId        Layout ID
     * @param sourceColumn   Source DB column (can be blank)
     * @param fieldValidator Field Validator (can be null)
     * @param formatter      Formatter to use
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn,
                     @Nullable final FieldValidator fieldValidator,
                     @Nullable final FieldFormatter formatter) {
        return add(fieldId, sourceColumn, sourceColumn, fieldValidator, formatter);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     * @param fieldValidator  Field Validator (can be null)
     *
     * @return The resulting Field.
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn,
                     @NonNull final String visibilityGroup,
                     @Nullable final FieldValidator fieldValidator) {
        return add(fieldId, sourceColumn, visibilityGroup, fieldValidator, null);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     * @param fieldValidator  Field Validator (can be null)
     * @param formatter       Formatter to use
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn,
                     @NonNull final String visibilityGroup,
                     @Nullable final FieldValidator fieldValidator,
                     @Nullable final FieldFormatter formatter) {
        Field field = new Field(this, fieldId, sourceColumn, visibilityGroup, fieldValidator, formatter);
        this.add(field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID
     *
     * @return Associated Field.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public Field getField(@IdRes final int fieldId) {
        for (Field f : this) {
            if (f.id == fieldId) {
                return f;
            }
        }
        throw new IllegalArgumentException("fieldId=" + fieldId);
    }

    /**
     * Convenience function: For an AutoCompleteTextView, set the adapter
     *
     * @param fieldId Layout ID of View
     * @param adapter Adapter to use
     */
    public void setAdapter(@IdRes final int fieldId, @NonNull final ArrayAdapter<String> adapter) {
        Field field = getField(fieldId);
        TextView textView = field.getView();
        if (textView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) textView).setAdapter(adapter);
        }
    }

    /**
     * For a View that supports onClick() (all of them?), set the listener.
     *
     * @param fieldId  Layout ID of View
     * @param listener onClick() listener.
     */
    void setListener(@IdRes final int fieldId, @NonNull final View.OnClickListener listener) {
        getField(fieldId).getView().setOnClickListener(listener);
    }

    /**
     * Load all fields from the passed cursor
     *
     * @param cursor Cursor to load Field objects from.
     */
    public void setAll(@NonNull final Cursor cursor) {
        for (Field field : this) {
            field.set(cursor);
        }
    }

    /**
     * Load all fields from the passed bundle
     *
     * @param values Bundle to load Field objects from.
     */
    public void setAll(@NonNull final Bundle values) {
        for (Field field : this) {
            field.set(values);
        }
    }

    /**
     * Load all fields from the passed {@link DataManager}
     *
     * @param data Cursor to load Field objects from.
     */
    public void setAll(@NonNull final DataManager data) {
        for (Field field : this) {
            field.set(data);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager} (ie. 'get' them *into* the {@link DataManager}).
     *
     * @param data Cursor to load Field objects from.
     */
    void getAllInto(@NonNull final DataManager data) {
        for (Field field : this) {
            if (!field.column.isEmpty()) {
                field.getValue(data);
            }
        }
    }

    /**
     * Internal utility routine to perform one loop validating all fields.
     *
     * @param values          The Bundle to fill in/use.
     * @param crossValidating Options indicating if this is a cross validation pass.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(@NonNull final Bundle values, final boolean crossValidating) {
        boolean isOk = true;
        for (Field field : this) {
            if (field.validator != null) {
                try {
                    field.validator.validate(this, field, values, crossValidating);
                } catch (ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                    // Always save the value...even if invalid. Or at least try to.
                    if (!crossValidating) {
                        try {
                            values.putString(field.column, field.getValue().toString().trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {
                if (!field.column.isEmpty()) {
                    field.getValue(values);
                }
            }
        }
        return isOk;
    }

    /**
     * Reset all field visibility based on user preferences
     */
    @SuppressWarnings("WeakerAccess")
    public void resetVisibility() {
        FieldsContext context = getContext();
        for (Field field : this) {
            field.resetVisibility(context);
        }
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     *
     * @param values The Bundle collection to fill
     *
     * @return boolean True if all validation passed.
     */
    public boolean validate(@NonNull final Bundle values) {
        boolean isOk = true;
        mValidationExceptions.clear();

        // First, just validate individual fields with the cross-val flag set false
        if (!validate(values, false)) {
            isOk = false;
        }

        // Now re-run with cross-val set to true.
        if (!validate(values, true)) {
            isOk = false;
        }

        // Finally run the local cross-validation
        for (FieldCrossValidator validator : mCrossValidators) {
            try {
                validator.validate(this, values);
            } catch (ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }
        return isOk;
    }

    /**
     * Retrieve the text message associated with the last validation exception t occur.
     *
     * @return res The resource manager to use when looking up strings.
     */
    @NonNull
    @SuppressWarnings("unused")
    public String getValidationExceptionMessage(@NonNull final Resources res) {
        if (mValidationExceptions.size() == 0) {
            return "No error";
        } else {
            StringBuilder message = new StringBuilder();
            Iterator<ValidatorException> i = mValidationExceptions.iterator();
            int cnt = 1;
            if (i.hasNext()) {
                message.append("(").append(cnt).append(") ").append(i.next().getFormattedMessage(res));
            }
            while (i.hasNext()) {
                cnt++;
                message.append(" (").append(cnt).append(") ").append(i.next().getFormattedMessage(res)).append("\n");
            }
            return message.toString();
        }
    }

    /**
     * Append a cross-field validator to the collection. These will be applied after
     * the field-specific validators have all passed.
     *
     * @param v An instance of FieldCrossValidator to append
     */
    @SuppressWarnings("WeakerAccess")
    public void addCrossValidator(@NonNull final FieldCrossValidator v) {
        mCrossValidators.add(v);
    }

    public interface AfterFieldChangeListener {
        void afterFieldChange(@NonNull final Field field, @Nullable final String newValue);
    }

    private interface FieldsContext {
        Object dbgGetOwnerContext();

        @Nullable
        View findViewById(@IdRes int id);
    }

    /**
     * Interface for view-specific accessors. One of these will be implemented for each view type that
     * is supported.
     *
     * @author Philip Warner
     */
    public interface FieldDataAccessor {
        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field  which defines the View details
         * @param cursor with data to load.
         */
        void set(@NonNull final Field field, @NonNull final Cursor cursor);

        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void set(@NonNull final Field field, @NonNull final Bundle values);

        /**
         * Passed a Field and a DataManager get the column from the data manager and set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void set(@NonNull final Field field, @NonNull final DataManager values);

        /**
         * Passed a Field and a String, use the string to set the view value.
         *
         * @param field which defines the View details
         * @param value to set.
         */
        void set(@NonNull final Field field, @NonNull final String value);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed values collection.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void get(@NonNull final Field field, @NonNull final Bundle values);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed DataManager.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void get(@NonNull final Field field, @NonNull final DataManager values);

        /**
         * Get the the value from the view associated with Field and return it as am Object.
         *
         * @param field associated with the View object
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        Object get(@NonNull final Field field);
    }

    /**
     * Interface for all field-level validators. Each field validator is called twice; once
     * with the crossValidating flag set to false, then, if all validations were successful,
     * they are all called a second time with the flag set to true. This is an alternate
     * method of applying cross-validation.
     *
     * @author Philip Warner
     */
    public interface FieldValidator {
        /**
         * Validation method. Must throw a ValidatorException if validation fails.
         *
         * @param fields          The Fields object containing the Field being validated
         * @param field           The Field to validate
         * @param values          A ContentValues collection to store the validated value.
         *                        On a cross-validation pass this collection will have all
         *                        field values set and can be read.
         * @param crossValidating Options indicating if this is the cross-validation pass.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(@NonNull final Fields fields,
                      @NonNull final Field field,
                      @NonNull final Bundle values,
                      final boolean crossValidating);
    }

    /**
     * Interface for all cross-validators; these are applied after all field-level validators
     * have succeeded.
     *
     * @author Philip Warner
     */
    public interface FieldCrossValidator {
        /**
         * @param fields The Fields object containing the Field being validated
         * @param values A Bundle collection with all validated field values.
         */
        void validate(@NonNull final Fields fields, @NonNull final Bundle values);
    }

    /**
     * Interface definition for Field formatters.
     *
     * @author Philip Warner
     */
    public interface FieldFormatter {
        /**
         * // Format a string for applying to a View
         *
         * @param source Input value
         *
         * @return The formatted value. If the source as null, should return "" (and log an error)
         */
        @NonNull
        String format(@NonNull final Field field, @Nullable final String source);

        /**
         * Extract a formatted string from the display version
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        String extract(@NonNull final Field field, @NonNull final String source);
    }

    /**
     * Implementation that stores and retrieves data from a string variable.
     * Only used when a Field fails to find a layout.
     *
     * @author Philip Warner
     */
    static public class StringDataAccessor implements FieldDataAccessor {
        @NonNull
        private String mLocalValue = "";

        @Override
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        @Override
        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        @Override
        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        @Override
        public void set(@NonNull final Field field, @NonNull final String value) {
            mLocalValue = field.format(value);
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @NonNull
        @Override
        public Object get(@NonNull final Field field) {
            return field.extract(mLocalValue);
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is
     * displayed properly.
     *
     * @author Philip Warner
     */
    static public class TextViewAccessor implements FieldDataAccessor {
        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        TextViewAccessor() {
        }

        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final String value) {
            mRawValue = value;
            TextView view = field.getView();
            if (mFormatHtml) {
                view.setText(Html.fromHtml(field.format(value)));
                view.setFocusable(true);
                view.setTextIsSelectable(true);
                view.setAutoLinkMask(Linkify.ALL);
            } else {
                view.setText(field.format(value));
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            values.putString(field.column, mRawValue.trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            values.putString(field.column, mRawValue.trim());
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            return mRawValue;
        }

        /**
         * Set the TextViewAccessor to support HTML.
         */
        @SuppressWarnings("WeakerAccess")
        public void setShowHtml(final boolean showHtml) {
            mFormatHtml = showHtml;
        }

    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * Just uses for defined formatter and setText() and getText().
     *
     * @author Philip Warner
     */
    static public class EditTextAccessor implements FieldDataAccessor {
        private boolean mIsSetting = false;

        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final String value) {
            synchronized (this) {
                if (mIsSetting) {
                    return; // Avoid recursion now we watch text
                }
                mIsSetting = true;
            }
            try {
                TextView view = field.getView();
                String newVal = field.format(value);
                String oldVal = view.getText().toString().trim();

                if (newVal.equals(oldVal)) {
                    return;
                }
                view.setText(newVal);
            } finally {
                mIsSetting = false;
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            TextView view = field.getView();
            values.putString(field.column, field.extract(view.getText().toString()).trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            try {
                TextView view = field.getView();
                dataManager.putString(field.column, field.extract(view.getText().toString()).trim());
            } catch (Exception e) {
                throw new RuntimeException("Unable to save data", e);
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            TextView view = field.getView();
            return field.extract(view.getText().toString().trim());
        }
    }

    /**
     * Checkable accessor. Attempt to convert data to/from a boolean.
     *
     * {@link Checkable} covers more then just a Checkbox
     * * CheckBox, RadioButton, Switch, ToggleButton extend CompoundButton, implements Checkable
     * * CheckedTextView extends TextView, implements Checkable
     *
     * @author Philip Warner
     */
    static public class CheckableAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String value) {
            Checkable cb = field.getView();
            if (value != null) {
                try {
                    cb.setChecked(Datum.toBoolean(field.format(value), true));
                } catch (Exception e) {
                    cb.setChecked(false);
                }
            } else {
                cb.setChecked(false);
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                values.putString(field.column, field.extract(cb.isChecked() ? "1" : "0"));
            } else {
                values.putBoolean(field.column, cb.isChecked());
            }
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.extract(cb.isChecked() ? "1" : "0"));
            } else {
                dataManager.putBoolean(field.column, cb.isChecked());
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                return field.formatter.extract(field, (cb.isChecked() ? "1" : "0"));
            } else {
                return cb.isChecked() ? 1 : 0;
            }
        }
    }

    /**
     * RatingBar accessor. Attempt to convert data to/from a Float.
     *
     * @author Philip Warner
     */
    static public class RatingBarAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                ratingBar.setRating(Float.parseFloat(field.formatter.format(field, cursor.getString(cursor.getColumnIndex(field.column)))));
            } else {
                ratingBar.setRating(cursor.getFloat(cursor.getColumnIndex(field.column)));
            }
        }

        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String value) {
            RatingBar ratingBar = field.getView();
            Float f = 0.0f;
            try {
                f = Float.parseFloat(field.format(value));
            } catch (NumberFormatException ignored) {
            }
            ratingBar.setRating(f);
        }

        public void get(@NonNull final Field field, @NonNull final Bundle bundle) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                bundle.putString(field.column, field.extract("" + ratingBar.getRating()));
            } else {
                bundle.putFloat(field.column, ratingBar.getRating());
            }
        }

        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.extract("" + ratingBar.getRating()));
            } else {
                dataManager.putFloat(field.column, ratingBar.getRating());
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            RatingBar ratingBar = field.getView();
            return ratingBar.getRating();
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     *
     * @author Philip Warner
     */
    static public class SpinnerAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager values) {
            set(field, values.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String value) {
            Spinner spinner = field.getView();
            String s = field.format(value);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(s)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            values.putString(field.column, getValue(field));
        }

        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            values.putString(field.column, getValue(field));
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            return field.extract(getValue(field));
        }

        @NonNull
        private String getValue(@NonNull final Field field) {
            Spinner spinner = field.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return selItem.toString().trim();
            } else {
                return "";
            }

        }
    }

    /**
     * Formatter for date fields. On failure just return the raw string.
     *
     * @author Philip Warner
     */
    static public class DateFieldFormatter implements FieldFormatter {
        /**
         * Display as a human-friendly date
         */
        @NonNull
        public String format(@NonNull final Field field, @Nullable final String source) {
            if (source == null) {
                Logger.error("source was null");
                return "";
            }
            try {
                java.util.Date d = DateUtils.parseDate(source);
                if (d != null) {
                    return DateUtils.toPrettyDate(d);
                }
            } catch (Exception ignore) {
            }
            return source;
        }

        /**
         * Extract as an SQL date.
         */
        @NonNull
        public String extract(@NonNull final Field field, @NonNull final String source) {
            try {
                java.util.Date d = DateUtils.parseDate(source);
                if (d != null) {
                    return DateUtils.toSqlDateOnly(d);
                }
            } catch (Exception ignore) {
            }
            return source;
        }
    }

    /**
     * Formatter for boolean fields. On failure just return the raw string or blank
     *
     * @author Philip Warner
     */
    static public class BinaryYesNoEmptyFormatter implements FieldFormatter {

        private Resources mRes;

        /**
         * @param res resources so we can get 'yes'/'no'
         */
        @SuppressWarnings("WeakerAccess")
        public BinaryYesNoEmptyFormatter(@NonNull final Resources res) {
            mRes = res;
        }

        /**
         * Display as a human-friendly yes/no string
         */
        @NonNull
        public String format(@NonNull final Field field, @Nullable final String source) {
            if (source == null) {
                Logger.error("source was null");
                return "";
            }
            try {
                boolean val = Datum.toBoolean(source, false);
                return mRes.getString(val ? R.string.yes : R.string.no);
            } catch (IllegalArgumentException e) {
                return source;
            }
        }

        /**
         * Extract as a boolean string
         */
        @NonNull
        public String extract(@NonNull final Field field, @NonNull final String source) {
            try {
                return Datum.toBoolean(source, false) ? "1" : "0";
            } catch (IllegalArgumentException e) {
                return source;
            }
        }
    }

    private class ActivityContext implements FieldsContext {
        @NonNull
        private final WeakReference<Activity> mActivity;

        ActivityContext(@NonNull final Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public Object dbgGetOwnerContext() {
            return mActivity.get();
        }

        @Override
        public View findViewById(@IdRes final int id) {
            return mActivity.get().findViewById(id);
        }
    }

    private class FragmentContext implements FieldsContext {
        @NonNull
        private final WeakReference<Fragment> mFragment;

        FragmentContext(@NonNull final Fragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        public Object dbgGetOwnerContext() {
            return mFragment.get();
        }

        @Override
        @Nullable
        public View findViewById(@IdRes final int id) {
            if (mFragment.get() == null) {
                if (/* always show debug */ BuildConfig.DEBUG) {
                    Logger.debug("Fragment is NULL");
                }
                return null;
            }
            final View view = mFragment.get().getView();
            if (view == null) {
                if (/* always show debug */ BuildConfig.DEBUG) {
                    Logger.debug("View is NULL");
                }
                return null;
            }

            return view.findViewById(id);
        }
    }

    /**
     * Field definition contains all information and methods necessary to manage display and
     * extraction of data in a view.
     *
     * @author Philip Warner
     */
    public class Field {
        /** Field ID */
        @IdRes
        public final int id;
        /** database column name (can be blank) */
        @NonNull
        public final String column;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views */
        @NonNull
        public final String group;
        /** Validator to use (can be null) */
        @Nullable
        public final FieldValidator validator;
        /** Owning collection */
        @NonNull
        final WeakReference<Fields> mFields;
        /** Has the field been set to invisible **/
        public boolean visible;
        /**
         * Option indicating that even though field has a column name, it should NOT be fetched
         * from a Cursor. This is usually done for synthetic fields needed when saving the data
         */
        @SuppressWarnings("WeakerAccess")
        public boolean doNoFetch = false;

        /** FieldFormatter to use (can be null) */
        FieldFormatter formatter;
        /** Accessor to use (automatically defined) */
        private FieldDataAccessor mAccessor;

        /** Optional field-specific tag object */
        @Nullable
        private Object mTag = null;

        /**
         * Constructor.
         *
         * @param fields              Parent object
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         * @param fieldValidator      Validator. Can be null.
         * @param fieldFormatter      Formatter. Can be null.
         */
        Field(@NonNull final Fields fields,
              @IdRes final int fieldId,
              @NonNull final String sourceColumn,
              @NonNull final String visibilityGroupName,
              @Nullable final FieldValidator fieldValidator,
              @Nullable final FieldFormatter fieldFormatter) {

            mFields = new WeakReference<>(fields);
            id = fieldId;
            column = sourceColumn;
            group = visibilityGroupName;
            formatter = fieldFormatter;
            validator = fieldValidator;

            // Lookup the view
            final View view = fields.getContext().findViewById(this.id);

            // Set the appropriate accessor
            if (view == null) {
                mAccessor = new StringDataAccessor();
            } else {
                if (view instanceof Spinner) {
                    mAccessor = new SpinnerAccessor();
                } else if (view instanceof Checkable) {
                    mAccessor = new CheckableAccessor();
                    addTouchSignalsDirty(view);
                } else if (view instanceof EditText) {
                    mAccessor = new EditTextAccessor();
                    EditText et = (EditText) view;
                    et.addTextChangedListener(
                            new TextWatcher() {
                                @Override
                                public void afterTextChanged(@NonNull Editable arg0) {
                                    Field.this.setValue(arg0.toString());
                                }

                                @Override
                                public void beforeTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }

                                @Override
                                public void onTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }
                            }
                    );
                } else if (view instanceof Button) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof TextView) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof ImageView) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof RatingBar) {
                    mAccessor = new RatingBarAccessor();
                    addTouchSignalsDirty(view);
                } else {
                    throw new RTE.IllegalTypeException(view.getClass().getCanonicalName());
                }
                visible = isVisible(group);
                if (!visible) {
                    view.setVisibility(View.GONE);
                }
            }
        }

        /**
         * If a text field, set the TextViewAccessor to support HTML.
         * Call this before loading the field.
         */
        @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
        @NonNull
        public Field setShowHtml(final boolean showHtml) {
            if (mAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mAccessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * Reset one fields visibility based on user preferences
         */
        private void resetVisibility(@Nullable final FieldsContext context) {
            if (context == null) {
                return;
            }
            // Lookup the view
            final View view = context.findViewById(this.id);
            if (view != null) {
                visible = isVisible(group);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        private void addTouchSignalsDirty(@NonNull final View view) {
            // Touching this is considered a change
            //TODO We need to introduce a better way to handle this.
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, @NonNull MotionEvent event) {
                    if (MotionEvent.ACTION_UP == event.getAction()) {
                        if (mAfterFieldChangeListener != null) {
                            mAfterFieldChangeListener.afterFieldChange(Field.this, null);
                        }
                    }
                    return false;
                }
            });
        }

        /**
         * Accessor; added for debugging only. Try not to use!
         *
         * @return all fields
         */
        @Nullable
        protected Fields getFields() {
            return mFields.get();
        }

        /**
         * Get the view associated with this Field, if available.
         *
         * @return Resulting View
         *
         * @throws NullPointerException if view is not found, which should never happen
         *
         * @see #debugNullView
         */
        @SuppressWarnings("unchecked")
        @NonNull
        <T extends View> T getView() {
            Fields fields = mFields.get();

            T view = (T) fields.getContext().findViewById(this.id);
            if (view == null) {
                debugNullView(this);
                throw new NullPointerException("view is NULL");
            }
            return view;
        }

        /**
         * Return the current value of the tag field.
         *
         * @return Current value of tag.
         */
        @Nullable
        public Object getTag() {
            return mTag;
        }

        /**
         * Set the current value of the tag field.
         */
        public void setTag(@NonNull final Object tag) {
            mTag = tag;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public Object getValue() {
            return mAccessor.get(this);
        }

        /**
         * Set the value to the passed string value.
         *
         * @param s New value
         */
        public void setValue(@NonNull final String s) {
            mAccessor.set(this, s);
            if (mAfterFieldChangeListener != null) {
                mAfterFieldChangeListener.afterFieldChange(this, s);
            }
        }

        /**
         * Get the current value of this field and put into the Bundle collection.
         **/
        public void getValue(@NonNull final Bundle values) {
            mAccessor.get(this, values);
        }

        /**
         * Get the current value of this field and put into the Bundle collection.
         **/
        public void getValue(@NonNull final DataManager data) {
            mAccessor.get(this, data);
        }

        /**
         * Utility function to call the formatters format() method if present,
         * or just return the raw value.
         *
         * @param s String to format
         *
         * @return The formatted value. If the source as null, should return "" (and log an error)
         */
        @NonNull
        public String format(@Nullable final String s) {
            if (s == null) {
                return "";
            }
            if (formatter == null) {
                return s;
            }
            return formatter.format(this, s);
        }

        /**
         * Utility function to call the formatters extract() method if present,
         * or just return the raw value.
         */
        @SuppressWarnings("WeakerAccess")
        public String extract(@NonNull final String s) {
            if (formatter == null) {
                return s;
            }
            return formatter.extract(this, s);
        }

        /**
         * Set the value of this field from the passed cursor. Useful for getting access to
         * raw data values from the database.
         */
        public void set(@NonNull final Cursor cursor) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, cursor);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        /**
         * Set the value of this field from the passed Bundle. Useful for getting access to
         * raw data values from a saved data bundle.
         */
        public void set(@NonNull final Bundle bundle) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, bundle);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        /**
         * Set the value of this field from the passed Bundle. Useful for getting access to
         * raw data values from a saved data bundle.
         */
        public void set(@NonNull final DataManager dataManager) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, dataManager);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }
    }
}

