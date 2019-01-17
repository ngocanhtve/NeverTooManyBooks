package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.List;

/**
 * an SQL WHERE clause (column IN (a,b,c,...).
 *
 * @param <T> type the elements of the 'IN' list.
 */
public class ListOfValuesFilter<T>
        implements Filter {

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final DomainDefinition mDomain;

    /** CSV list of values */
    @NonNull
    private final String mCriteria;

    public ListOfValuesFilter(@NonNull final TableDefinition table,
                              @NonNull final DomainDefinition domain,
                              @NonNull final List<T> list) {
        mTable = table;
        mDomain = domain;

        StringList<T> au = new StringList<>();
        mCriteria = au.encode(list);
    }

    @Override
    @NonNull
    public String getExpression(@Nullable final String uuid) {
        return '(' + mTable.dot(mDomain) + " IN (" + mCriteria + "))";
    }
}
