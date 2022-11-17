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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.BlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataCrossValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DoubleValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.LongValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.NonBlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.OrValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Class to manage a set of related data.
 * It's basically an extended Bundle with support for validators, Money and Bit types, parsing, ...
 *
 * <ul>
 *      <li>mRawData: stores the actual data</li>
 *      <li>mValidatorsMap: validators applied at 'save' time</li>
 *      <li>mCrossValidators: cross-validators applied at 'save' time</li>
 * </ul>
 */
public class DataManager
        implements DataHolder {

    /** re-usable validator. */
    protected static final DataValidator LONG_VALIDATOR = new LongValidator();
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    /** re-usable validator. */
    protected static final DataValidator PRICE_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new DoubleValidator());

    /** Log tag. */
    private static final String TAG = "DataManager";
    /** DataValidators. */
    private final Map<String, DataValidator> validatorsMap = new HashMap<>();
    /** DataValidators. Same key as {@link #validatorsMap}; value: @StringRes. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Map<String, Integer> validatorErrorIdMap = new HashMap<>();

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final Collection<DataCrossValidator> crossValidators = new ArrayList<>();
    /** The validator exceptions caught by this object. */
    private final Collection<ValidatorException> validationExceptions = new ArrayList<>();

    /** Raw data storage. */
    @NonNull
    private final Bundle rawData;

    /**
     * Constructor.
     */
    protected DataManager() {
        rawData = ServiceLocator.newBundle();
    }

    /**
     * Constructor for Mock tests. Loads the bundle <strong>without</strong> type checks.
     */
    @VisibleForTesting
    protected DataManager(@NonNull final Bundle rawData) {
        this.rawData = rawData;
    }

    /**
     * Clear all data in this instance.
     */
    protected void clearData() {
        rawData.clear();
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return Set.copyOf(rawData.keySet());
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data object to remove.
     */
    public void remove(@NonNull final String key) {
        rawData.remove(key);
    }

    /**
     * Check if the underlying data contains the specified key.
     *
     * @param key Key of data object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean contains(@NonNull final String key) {
        return rawData.containsKey(key);
    }


    /**
     * Store all passed values in our collection (with type checking).
     *
     * @param src bundle to copy from
     */
    protected void putAll(@NonNull final Bundle src) {
        for (final String key : src.keySet()) {
            put(key, src.get(key));
        }
    }

    /**
     * Store all passed values in our collection.
     * <p>
     * See the comments on methods in {@link android.database.CursorWindow}
     * for info on type conversions which explains our use of getLong/getDouble.
     * <ul>
     *      <li>booleans -> long (0,1)</li>
     *      <li>int -> long</li>
     *      <li>float -> double</li>
     *      <li>date -> string</li>
     * </ul>
     *
     * @param cursor an already positioned Cursor to read from
     */
    protected void putAll(@NonNull final Cursor cursor) {
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            final String name = cursor.getColumnName(i);
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_STRING:
                    rawData.putString(name, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    // a null becomes 0
                    rawData.putLong(name, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    // a null becomes 0.0
                    rawData.putDouble(name, cursor.getDouble(i));
                    break;

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(name, cursor.getBlob(i));
                    break;

                case Cursor.FIELD_TYPE_NULL:
                    // discard any fields with null values.
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(cursor.getType(i)));
            }
        }
    }

    /**
     * Store a Object value. The object will be cast to one of the supported types.
     * <p>
     * An ArrayList is only supported with Parcelable content.
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @throws IllegalArgumentException for unsupported types.
     */
    public void put(@NonNull final String key,
                    @Nullable final Object value) {

        if (value instanceof String) {
            rawData.putString(key, (String) value);
        } else if (value instanceof Integer) {
            rawData.putInt(key, (int) value);
        } else if (value instanceof Long) {
            rawData.putLong(key, (long) value);
        } else if (value instanceof Double) {
            rawData.putDouble(key, (double) value);
        } else if (value instanceof Float) {
            rawData.putFloat(key, (float) value);
        } else if (value instanceof Boolean) {
            rawData.putBoolean(key, (boolean) value);

        } else if (value instanceof ArrayList) {
            //noinspection unchecked
            putParcelableArrayList(key, (ArrayList<Parcelable>) value);

        } else if (value instanceof Money) {
            putMoney(key, (Money) value);

        } else if (value instanceof Serializable) {
            putSerializable(key, (Serializable) value);

        } else if (value == null) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "put|key=`" + key + "`|value=<NULL>");
            }
            putNull(key);

        } else {
            throw new IllegalArgumentException("put|key=`" + key + "`|value=" + value);
        }
    }

    /**
     * Get the data object specified by the passed key.
     *
     * @param key Key of data object
     *
     * @return Data object, or {@code null} when not present or the value is {@code null}
     */
    @Nullable
    public Object get(@NonNull final String key) {
        if (DBKey.MONEY_KEYS.contains(key)) {
            try {
                return getMoney(key);
            } catch (@NonNull final NumberFormatException ignore) {
                //TEST: should we really ignore this, next step will return raw value.
                if (BuildConfig.DEBUG /* always */) {
                    Logger.d(TAG, "get",
                             "NumberFormatException"
                             + "|name=" + key
                             + "|value=`" + rawData.get(key) + '`');
                }
            }
        }
        return rawData.get(key);
    }


    /**
     * Get a boolean value.
     *
     * @param key Key of data object
     *
     * @return a boolean value.
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public boolean getBoolean(@NonNull final String key)
            throws NumberFormatException {
        return ParseUtils.toBoolean(rawData.get(key));
    }

    /**
     * Store a boolean value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        rawData.putBoolean(key, value);
    }

    /**
     * Get an int value.
     *
     * @param key Key of data object
     *
     * @return an int value; {@code null} or empty becomes 0
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public int getInt(@NonNull final String key)
            throws NumberFormatException {
        return (int) ParseUtils.toLong(rawData.get(key));
    }

    /**
     * Store an int value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        rawData.putInt(key, value);
    }

    /**
     * Get a long value.
     *
     * @param key Key of data object
     *
     * @return a long value; {@code null} or empty becomes 0
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public long getLong(@NonNull final String key)
            throws NumberFormatException {
        return ParseUtils.toLong(rawData.get(key));
    }

    /**
     * Store a long value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        rawData.putLong(key, value);
    }

    /**
     * Get a double value.
     *
     * @param key Key of data object
     *
     * @return a double value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public double getDouble(@NonNull final String key)
            throws NumberFormatException {
        return ParseUtils.toDouble(rawData.get(key), null);
    }

    /**
     * Store a double value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putDouble(@NonNull final String key,
                          final double value) {
        rawData.putDouble(key, value);
    }

    /**
     * Get a float value.
     *
     * @param key Key of data object
     *
     * @return a float value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public float getFloat(@NonNull final String key)
            throws NumberFormatException {
        return ParseUtils.toFloat(rawData.get(key), null);
    }

    /**
     * Store a float value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putFloat(@NonNull final String key,
                         final float value) {
        rawData.putFloat(key, value);
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defaultValue) {
        final Object o = rawData.get(key);
        if (o == null) {
            return defaultValue;
        } else {
            return o.toString().trim();
        }
    }

    /**
     * Store a String value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putString(@NonNull final String key,
                          @NonNull final String value) {
        rawData.putString(key, value);
    }

    /**
     * Get a {@link Money} value.
     *
     * @param key Key of data object
     *
     * @return value
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Nullable
    public Money getMoney(@NonNull final String key)
            throws NumberFormatException {
        if (rawData.containsKey(key)) {
            return new Money(getDouble(key), getString(key + DBKey.CURRENCY_SUFFIX));
        } else {
            return null;
        }
    }

    /**
     * Store a {@link Money} value.
     *
     * @param key   Key of data object
     * @param money to store
     */
    public void putMoney(@NonNull final String key,
                         @NonNull final Money money) {
        rawData.putDouble(key, money.doubleValue());
        if (money.getCurrency() != null) {
            rawData.putString(key + DBKey.CURRENCY_SUFFIX, money.getCurrencyCode());
        }
    }

    /**
     * Get a {@link Parcelable} {@link ArrayList} from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        Object o = rawData.get(key);
        if (o == null) {
            o = new ArrayList<>();
            //noinspection unchecked
            rawData.putParcelableArrayList(key, (ArrayList<T>) o);
        }
        //noinspection unchecked
        return (ArrayList<T>) o;
    }

    /**
     * Store a {@link Parcelable} {@link ArrayList} in the collection.
     * <p>
     * If possible, AVOID using this method directly.
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of objects in the list
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final String key,
                                                              @NonNull final ArrayList<T> value) {
        rawData.putParcelableArrayList(key, value);
    }

    @Nullable
    public <T extends Parcelable> T getParcelable(@NonNull final String key) {
        return rawData.getParcelable(key);
    }

    /**
     * Store a {@link Parcelable} in the collection.
     * <p>
     * If possible, AVOID using this method directly.
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of object
     */
    public <T extends Parcelable> void putParcelable(@NonNull final String key,
                                                     @NonNull final T value) {
        rawData.putParcelable(key, value);
    }

    /**
     * Get a {@link Serializable} object from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The data
     */
    @SuppressWarnings("unused")
    @Nullable
    protected <T extends Serializable> T getSerializable(@NonNull final String key) {
        //noinspection unchecked
        return (T) rawData.getSerializable(key);
    }

    /**
     * Store a {@link Serializable} object in the collection.
     *
     * @param key   Key of data object
     * @param value to store
     */
    private void putSerializable(@NonNull final String key,
                                 @NonNull final Serializable value) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "putSerializable|key=" + key
                       + "|type=" + value.getClass().getCanonicalName(), new Throwable());
        }
        rawData.putSerializable(key, value);
    }

    /**
     * Store a {@code null} value.
     *
     * @param key Key of data object
     */
    public void putNull(@NonNull final String key) {
        rawData.putString(key, null);
    }


    /**
     * Add a validator for the specified key.
     * <p>
     * Accepts only one validator for a key. Setting a second one will override the first.
     *
     * @param key             Key for the data
     * @param validator       Validator
     * @param errorLabelResId string resource id for a user visible message
     */
    protected void addValidator(@NonNull final String key,
                                @NonNull final DataValidator validator,
                                @StringRes final int errorLabelResId) {
        validatorsMap.put(key, validator);
        validatorErrorIdMap.put(key, errorLabelResId);
    }

    /**
     * Add a cross validator.
     *
     * @param validator Validator
     */
    protected void addCrossValidator(@NonNull final DataCrossValidator validator) {
        crossValidators.add(validator);
    }

    /**
     * Loop through and apply validators.
     * <p>
     * {@link ValidatorException} are added to {@link #validationExceptions}
     * Use {@link #getValidationExceptionMessage} for the results.
     *
     * @param context Current context
     *
     * @return {@code true} if all validation passed.
     */
    public boolean validate(@NonNull final Context context) {

        boolean isOk = true;
        validationExceptions.clear();

        for (final Map.Entry<String, DataValidator> entry : validatorsMap.entrySet()) {
            final String key = entry.getKey();
            try {
                entry.getValue()
                     .validate(context, this, key,
                               Objects.requireNonNull(validatorErrorIdMap.get(key), key));
            } catch (@NonNull final ValidatorException e) {
                validationExceptions.add(e);
                isOk = false;
            }
        }

        for (final DataCrossValidator crossValidator : crossValidators) {
            try {
                crossValidator.validate(context, this);
            } catch (@NonNull final ValidatorException e) {
                validationExceptions.add(e);
                isOk = false;
            }
        }
        return isOk;
    }

    /**
     * Retrieve the text message associated with the validation exceptions (if any).
     *
     * @param context Current context
     *
     * @return a user displayable list of error messages, or {@code null} if none present
     */
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Context context) {
        if (validationExceptions.isEmpty()) {
            return null;

        } else {
            final StringBuilder msg = new StringBuilder();
            int i = 0;
            for (final ValidatorException e : validationExceptions) {
                msg.append(" (").append(++i).append(") ")
                   .append(e.getUserMessage(context))
                   .append('\n');
            }
            return msg.toString();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "DataManager{"
               + "rawData=" + rawData
               + ", validationExceptions=" + validationExceptions
               + ", validatorsMap=" + validatorsMap
               + ", crossValidators=" + crossValidators
               + '}';
    }
}
