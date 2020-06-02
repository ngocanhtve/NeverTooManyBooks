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

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyChar;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * <a href="https://github.com/konmik/nucleus/blob/master/nucleus-test-kit/src/main/java/mocks/BundleMock.java">BundleMock</a>
 * <p>
 * ADDED 2020-02-02: allow storing null values
 * doAnswer(put).when(bundle).putString(anyString(), isNull());
 */
public final class BundleMock {

    public static Bundle mock() {
        return mock(new HashMap<>());
    }

    @SuppressWarnings({"SuspiciousMethodCalls", "ResultOfMethodCallIgnored", "unchecked"})
    private static Bundle mock(@NonNull final HashMap<String, Object> map) {

        Answer<Object> unsupported = invocation -> {
            throw new UnsupportedOperationException();
        };
        Answer<Object> put = invocation -> {
            map.put((String) invocation.getArguments()[0], invocation.getArguments()[1]);
            return null;
        };
        Answer<Object> get = invocation -> map.get(invocation.getArguments()[0]);
        Answer<Object> getOrDefault = invocation -> {
            Object key = invocation.getArguments()[0];
            return map.containsKey(key) ? map.get(key) : invocation.getArguments()[1];
        };

        Bundle bundle = Mockito.mock(Bundle.class);

        doAnswer(invocation -> map.size()).when(bundle).size();
        doAnswer(invocation -> map.isEmpty()).when(bundle).isEmpty();
        doAnswer(invocation -> {
            map.clear();
            return null;
        }).when(bundle).clear();

        doAnswer(invocation -> map.containsKey(invocation.getArguments()[0]))
                .when(bundle).containsKey(anyString());
        doAnswer(invocation -> map.get(invocation.getArguments()[0]))
                .when(bundle).get(anyString());
        doAnswer(invocation -> {
            map.remove(invocation.getArguments()[0]);
            return null;
        }).when(bundle).remove(anyString());

        doAnswer(invocation -> map.keySet()).when(bundle).keySet();

        doAnswer(invocation -> BundleMock.class.getSimpleName() + "{map=" + map.toString() + "}")
                .when(bundle).toString();

        doAnswer(put).when(bundle).putBoolean(anyString(), anyBoolean());
        when(bundle.getBoolean(anyString())).thenAnswer(get);
        when(bundle.getBoolean(anyString(), anyBoolean())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putByte(anyString(), anyByte());
        when(bundle.getByte(anyString())).thenAnswer(get);
        when(bundle.getByte(anyString(), anyByte())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putChar(anyString(), anyChar());
        when(bundle.getChar(anyString())).thenAnswer(get);
        when(bundle.getChar(anyString(), anyChar())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putInt(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putLong(anyString(), anyLong());
        when(bundle.getLong(anyString())).thenAnswer(get);
        when(bundle.getLong(anyString(), anyLong())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putDouble(anyString(), anyDouble());
        when(bundle.getDouble(anyString())).thenAnswer(get);
        when(bundle.getDouble(anyString(), anyDouble())).thenAnswer(getOrDefault);

        // ADDED 2020-02-02: allow storing null values
        doAnswer(put).when(bundle).putString(anyString(), isNull());
        doAnswer(put).when(bundle).putString(anyString(), anyString());
        when(bundle.getString(anyString())).thenAnswer(get);
        when(bundle.getString(anyString(), anyString())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBooleanArray(anyString(), any(boolean[].class));
        when(bundle.getBooleanArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putLongArray(anyString(), any(long[].class));
        when(bundle.getLongArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putDoubleArray(anyString(), any(double[].class));
        when(bundle.getDoubleArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntArray(anyString(), any(int[].class));
        when(bundle.getIntArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putInt(anyString(), anyInt());
        when(bundle.getInt(anyString())).thenAnswer(get);
        when(bundle.getInt(anyString(), anyInt())).thenAnswer(getOrDefault);

        doAnswer(unsupported).when(bundle).putAll(any(Bundle.class));
        when(bundle.hasFileDescriptors()).thenAnswer(unsupported);

        doAnswer(put).when(bundle).putShort(anyString(), anyShort());
        when(bundle.getShort(anyString())).thenAnswer(get);
        when(bundle.getShort(anyString(), anyShort())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putFloat(anyString(), anyFloat());
        when(bundle.getFloat(anyString())).thenAnswer(get);
        when(bundle.getFloat(anyString(), anyFloat())).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putCharSequence(anyString(), any(CharSequence.class));
        when(bundle.getCharSequence(anyString())).thenAnswer(get);
        when(bundle.getCharSequence(anyString(), any(CharSequence.class))).thenAnswer(getOrDefault);

        doAnswer(put).when(bundle).putBundle(anyString(), any(Bundle.class));
        when(bundle.getBundle(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelable(anyString(), any(Parcelable.class));
        when(bundle.getParcelable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArray(anyString(), any(Parcelable[].class));
        when(bundle.getParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putParcelableArrayList(anyString(), any(ArrayList.class));
        when(bundle.getParcelableArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSparseParcelableArray(anyString(), any(SparseArray.class));
        when(bundle.getSparseParcelableArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putSerializable(anyString(), any(Serializable.class));
        when(bundle.getSerializable(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putIntegerArrayList(anyString(), any(ArrayList.class));
        when(bundle.getIntegerArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putStringArrayList(anyString(), any(ArrayList.class));
        when(bundle.getStringArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArrayList(anyString(), any(ArrayList.class));
        when(bundle.getCharSequenceArrayList(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharArray(anyString(), any(char[].class));
        when(bundle.getCharArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putByteArray(anyString(), any(byte[].class));
        when(bundle.getByteArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putShortArray(anyString(), any(short[].class));
        when(bundle.getShortArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putFloatArray(anyString(), any(float[].class));
        when(bundle.getFloatArray(anyString())).thenAnswer(get);

        doAnswer(put).when(bundle).putCharSequenceArray(anyString(), any(CharSequence[].class));
        when(bundle.getCharSequenceArray(anyString())).thenAnswer(get);

        return bundle;
    }
}
