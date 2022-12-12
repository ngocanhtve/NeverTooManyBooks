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

package com.hardbacknutter.org.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/*
Public Domain.
*/

/**
 * JSONWriter provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. No whitespace is
 * added, so the results are ready for transmission or storage. Each instance of
 * JSONWriter can produce one JSON text.
 * <p>
 * A JSONWriter instance provides a {@code value} method for appending
 * values to the
 * text, and a {@code key}
 * method for adding keys before values in objects. There are {@code array}
 * and {@code endArray} methods that make and bound array values, and
 * {@code object} and {@code endObject} methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting a cascade style. For example, <pre>
 * new JSONWriter(myWriter)
 *     .object()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject();</pre> which writes <pre>
 * {"JSON":"Hello, World!"}</pre>
 * <p>
 * The first method called must be {@code array} or {@code object}.
 * There are no methods for adding commas or colons. JSONWriter adds them for
 * you. Objects and arrays can be nested up to 200 levels deep.
 * <p>
 * This can sometimes be easier than using a JSONObject to build a string.
 *
 * @author JSON.org
 * @version 2016-08-08
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class JSONWriter {

    private static final int maxdepth = 200;
    /**
     * The object/array stack.
     */
    private final JSONObject[] stack;
    /**
     * The current mode. Values:
     * 'a' (array),
     * 'd' (done),
     * 'i' (initial),
     * 'k' (key),
     * 'o' (object).
     */
    protected char mode;
    /**
     * The writer that will receive the output.
     */
    protected Appendable writer;
    /**
     * The comma flag determines if a comma should be output before the next
     * value.
     */
    private boolean comma;
    /**
     * The stack top index. A value of 0 indicates that the stack is empty.
     */
    private int top;

    /**
     * Make a fresh JSONWriter. It can be used to build one JSON text.
     *
     * @param w an appendable object
     */
    public JSONWriter(final Appendable w) {
        this.comma = false;
        this.mode = 'i';
        this.stack = new JSONObject[maxdepth];
        this.top = 0;
        this.writer = w;
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value The value to be serialized.
     *
     * @return a printable, displayable, transmittable representation of the
     * object, beginning with <code>{</code>&nbsp;<small>(left
     * brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     * brace)</small>.
     *
     * @throws JSONException If the value is or contains an invalid number.
     */
    @NonNull
    public static String valueToString(@Nullable final Object value)
            throws JSONException {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONString) {
            final String object;
            try {
                object = ((JSONString) value).toJSONString();
            } catch (final Exception e) {
                throw new JSONException(e);
            }
            if (object != null) {
                return object;
            }
            throw new JSONException("Bad value from toJSONString: null");
        }
        if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. Fractions or Complex
            final String numberAsString = JSONObject.numberToString((Number) value);
            if (JSONObject.NUMBER_PATTERN.matcher(numberAsString).matches()) {
                // Close enough to a JSON number that we will return it unquoted
                return numberAsString;
            }
            // The Number value is not a valid JSON number.
            // Instead we will quote it as a string
            return JSONObject.quote(numberAsString);
        }
        if (value instanceof Boolean || value instanceof JSONObject
            || value instanceof JSONArray) {
            return value.toString();
        }
        if (value instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) value;
            return new JSONObject(map).toString();
        }
        if (value instanceof Collection) {
            final Collection<?> coll = (Collection<?>) value;
            return new JSONArray(coll).toString();
        }
        if (value.getClass().isArray()) {
            return new JSONArray(value).toString();
        }
        if (value instanceof Enum<?>) {
            return JSONObject.quote(((Enum<?>) value).name());
        }
        return JSONObject.quote(value.toString());
    }

    /**
     * Append a value.
     *
     * @param string A string value.
     *
     * @return this
     *
     * @throws JSONException If the value is out of sequence.
     */
    private JSONWriter append(final CharSequence string)
            throws JSONException {
        if (string == null) {
            throw new JSONException("Null pointer");
        }
        if (this.mode == 'o' || this.mode == 'a') {
            try {
                if (this.comma && this.mode == 'a') {
                    this.writer.append(',');
                }
                this.writer.append(string);
            } catch (final IOException e) {
                // Android as of API 25 does not support this exception constructor
                // however we won't worry about it. If an exception is happening here
                // it will just throw a "Method not found" exception instead.
                throw new JSONException(e);
            }
            if (this.mode == 'o') {
                this.mode = 'k';
            }
            this.comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence.");
    }

    /**
     * Begin appending a new array. All values until the balancing
     * {@code endArray} will be appended to this array. The
     * {@code endArray} method must be called to mark the array's end.
     *
     * @return this
     *
     * @throws JSONException If the nesting is too deep, or if the object is
     *                       started in the wrong place (for example as a key or after
     *                       the end of the outermost array or object).
     */
    public JSONWriter array()
            throws JSONException {
        if (this.mode == 'i' || this.mode == 'o' || this.mode == 'a') {
            this.push(null);
            this.append("[");
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced array.");
    }

    /**
     * End something.
     *
     * @param m Mode
     * @param c Closing character
     *
     * @return this
     *
     * @throws JSONException If unbalanced.
     */
    private JSONWriter end(final char m,
                           final char c)
            throws JSONException {
        if (this.mode != m) {
            throw new JSONException(m == 'a'
                                    ? "Misplaced endArray."
                                    : "Misplaced endObject.");
        }
        this.pop(m);
        try {
            this.writer.append(c);
        } catch (final IOException e) {
            // Android as of API 25 does not support this exception constructor
            // however we won't worry about it. If an exception is happening here
            // it will just throw a "Method not found" exception instead.
            throw new JSONException(e);
        }
        this.comma = true;
        return this;
    }

    /**
     * End an array. This method most be called to balance calls to
     * {@code array}.
     *
     * @return this
     *
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endArray()
            throws JSONException {
        return this.end('a', ']');
    }

    /**
     * End an object. This method most be called to balance calls to
     * {@code object}.
     *
     * @return this
     *
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endObject()
            throws JSONException {
        return this.end('k', '}');
    }

    /**
     * Append a key. The key will be associated with the next value. In an
     * object, every value must be preceded by a key.
     *
     * @param string A key string.
     *
     * @return this
     *
     * @throws JSONException If the key is out of place. For example, keys
     *                       do not belong in arrays or if the key is null.
     */
    public JSONWriter key(final String string)
            throws JSONException {
        if (string == null) {
            throw new JSONException("Null key.");
        }
        if (this.mode == 'k') {
            try {
                final JSONObject topObject = this.stack[this.top - 1];
                // don't use the built in putOnce method to maintain Android support
                if (topObject.has(string)) {
                    throw new JSONException("Duplicate key \"" + string + "\"");
                }
                topObject.put(string, true);
                if (this.comma) {
                    this.writer.append(',');
                }
                this.writer.append(JSONObject.quote(string));
                this.writer.append(':');
                this.comma = false;
                this.mode = 'o';
                return this;
            } catch (final IOException e) {
                // Android as of API 25 does not support this exception constructor
                // however we won't worry about it. If an exception is happening here
                // it will just throw a "Method not found" exception instead.
                throw new JSONException(e);
            }
        }
        throw new JSONException("Misplaced key.");
    }

    /**
     * Begin appending a new object. All keys and values until the balancing
     * {@code endObject} will be appended to this object. The
     * {@code endObject} method must be called to mark the object's end.
     *
     * @return this
     *
     * @throws JSONException If the nesting is too deep, or if the object is
     *                       started in the wrong place (for example as a key or
     *                       after the end of the outermost array or object).
     */
    public JSONWriter object()
            throws JSONException {
        if (this.mode == 'i') {
            this.mode = 'o';
        }
        if (this.mode == 'o' || this.mode == 'a') {
            this.append("{");
            this.push(new JSONObject());
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced object.");

    }

    /**
     * Pop an array or object scope.
     *
     * @param c The scope to close.
     *
     * @throws JSONException If nesting is wrong.
     */
    private void pop(final char c)
            throws JSONException {
        if (this.top <= 0) {
            throw new JSONException("Nesting error.");
        }
        final char m = this.stack[this.top - 1] == null ? 'a' : 'k';
        if (m != c) {
            throw new JSONException("Nesting error.");
        }
        this.top -= 1;
        this.mode = this.top == 0
                    ? 'd'
                    : this.stack[this.top - 1] == null ? 'a' : 'k';
    }

    /**
     * Push an array or object scope.
     *
     * @param jo The scope to open.
     *
     * @throws JSONException If nesting is too deep.
     */
    private void push(@Nullable final JSONObject jo)
            throws JSONException {
        if (this.top >= maxdepth) {
            throw new JSONException("Nesting too deep.");
        }
        this.stack[this.top] = jo;
        this.mode = jo == null ? 'a' : 'k';
        this.top += 1;
    }

    /**
     * Append either the value {@code true} or the value
     * {@code false}.
     *
     * @param b A boolean.
     *
     * @return this
     *
     * @throws JSONException if a called function has an error
     */
    public JSONWriter value(final boolean b)
            throws JSONException {
        return this.append(b ? "true" : "false");
    }

    /**
     * Append a double value.
     *
     * @param d A double.
     *
     * @return this
     *
     * @throws JSONException If the number is not finite.
     */
    public JSONWriter value(final double d)
            throws JSONException {
        return this.value(Double.valueOf(d));
    }

    /**
     * Append a long value.
     *
     * @param l A long.
     *
     * @return this
     *
     * @throws JSONException if a called function has an error
     */
    public JSONWriter value(final long l)
            throws JSONException {
        return this.append(Long.toString(l));
    }


    /**
     * Append an object value.
     *
     * @param object The object to append. It can be null, or a Boolean, Number,
     *               String, JSONObject, or JSONArray, or an object that implements JSONString.
     *
     * @return this
     *
     * @throws JSONException If the value is out of sequence.
     */
    public JSONWriter value(final Object object)
            throws JSONException {
        return this.append(valueToString(object));
    }
}
