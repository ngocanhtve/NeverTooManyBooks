/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.ParcelableEntity;
import com.hardbacknutter.nevertoomanybooks.widgets.ChecklistRecyclerAdapter;

/**
 * Replacement for the AlertDialog with checkbox setup.
 */
public class MultiChoiceDialogFragment
        extends DialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "MultiChoiceDialogFragment";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    /** Argument. */
    private static final String BKEY_FIELD_ID = TAG + ":fieldId";
    /** Argument. */
    private static final String BKEY_ALL_IDS = TAG + ":ids";
    private static final String BKEY_ALL_LABELS = TAG + ":labels";
    /** Argument. */
    private static final String BKEY_SELECTED = TAG + ":selected";
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    @IdRes
    private int mFieldId;
    @Nullable
    private String mDialogTitle;
    @Nullable
    private String mDialogMessage;

    /** The list of items to display. */
    private List<Long> mItemIds;
    private List<String> mItemLabels;
    /** The selected items. */
    private Set<Long> mSelectedItems;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        mDialogTitle = args.getString(BKEY_DIALOG_TITLE, getString(R.string.action_edit));
        mDialogMessage = args.getString(BKEY_DIALOG_MESSAGE, null);
        mFieldId = args.getInt(BKEY_FIELD_ID);

        mItemIds = Arrays.stream(Objects.requireNonNull(
                                 args.getLongArray(BKEY_ALL_IDS), BKEY_ALL_IDS))
                         .boxed().collect(Collectors.toList());
        mItemLabels = Arrays.stream(Objects.requireNonNull(
                                    args.getStringArray(BKEY_ALL_LABELS), BKEY_ALL_LABELS))
                            .collect(Collectors.toList());

        args = savedInstanceState != null ? savedInstanceState : args;

        mSelectedItems = Arrays.stream(Objects.requireNonNull(
                                       args.getLongArray(BKEY_SELECTED), BKEY_SELECTED))
                               .boxed().collect(Collectors.toSet());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        final View view = getLayoutInflater().inflate(R.layout.dialog_edit_checklist, null);

        final TextView messageView = view.findViewById(R.id.message);
        if (mDialogMessage != null && !mDialogMessage.isEmpty()) {
            messageView.setText(mDialogMessage);
            messageView.setVisibility(View.VISIBLE);
        } else {
            messageView.setVisibility(View.GONE);
        }

        final Context context = getContext();

        //noinspection ConstantConditions
        final ChecklistRecyclerAdapter<Long, String> adapter =
                new ChecklistRecyclerAdapter<>(context, mItemIds, mItemLabels, mSelectedItems,
                                               (id, checked) -> {
                                                   if (checked) {
                                                       mSelectedItems.add(id);
                                                   } else {
                                                       mSelectedItems.remove(id);
                                                   }
                                               });

        final RecyclerView listView = view.findViewById(R.id.item_list);
        listView.setAdapter(adapter);

        return new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setTitle(mDialogTitle)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> saveChanges())
                .create();
    }

    private void saveChanges() {
        Launcher.setResult(this, mRequestKey, mFieldId,
                           mSelectedItems.stream().mapToLong(o -> o).toArray());
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLongArray(BKEY_SELECTED, mSelectedItems.stream().mapToLong(o -> o).toArray());
    }

    public abstract static class Launcher<T extends ParcelableEntity>
            implements FragmentResultListener {

        private static final String FIELD_ID = "fieldId";
        private static final String SELECTED = "selected";
        private String mRequestKey;
        private FragmentManager mFragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IdRes final int fieldId,
                              @NonNull final long[] selectedItems) {
            final Bundle result = new Bundle(2);
            result.putInt(FIELD_ID, fieldId);
            result.putLongArray(SELECTED, selectedItems);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            mFragmentManager = fragmentManager;
            mRequestKey = requestKey;
            mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param context       Current context
         * @param dialogTitle   the dialog title
         * @param fieldId       this dialog operates on
         *                      (one launcher can serve multiple fields)
         * @param allItems      list of all possible items
         * @param selectedItems list of item which are currently selected
         */
        public void launch(@NonNull final Context context,
                           @NonNull final String dialogTitle,
                           @IdRes final int fieldId,
                           @NonNull final List<T> allItems,
                           @NonNull final List<T> selectedItems) {

            final Bundle args = new Bundle(6);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(BKEY_DIALOG_TITLE, dialogTitle);
            args.putInt(BKEY_FIELD_ID, fieldId);

            args.putLongArray(BKEY_ALL_IDS, allItems
                    .stream().mapToLong(Entity::getId).toArray());
            args.putStringArray(BKEY_ALL_LABELS, allItems
                    .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

            args.putLongArray(BKEY_SELECTED, selectedItems
                    .stream().mapToLong(Entity::getId).toArray());

            final DialogFragment frag = new MultiChoiceDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getInt(FIELD_ID),
                     Arrays.stream(Objects.requireNonNull(result.getLongArray(SELECTED), SELECTED))
                           .boxed().collect(Collectors.toSet()));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fieldId       this destination field id
         * @param selectedItems the set of <strong>checked</strong> items
         */
        public abstract void onResult(@IdRes int fieldId,
                                      @NonNull Set<Long> selectedItems);
    }
}
