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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStylesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditPreferredStylesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * {@link ListStyle} maintenance.
 */
@SuppressWarnings("MethodOnlyUsedFromInnerClass")
public class PreferredStylesFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "PreferredStylesFragment";
    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;
    /** The adapter for the list. */
    private ListStylesAdapter mListAdapter;
    private PreferredStylesViewModel mVm;

    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {

                /** called if the user flipped the 'isPreferred' status. */
                @Override
                public void onItemRangeChanged(final int positionStart,
                                               final int itemCount) {
                    // The style settings in its SharedPreference file are already stored.
                    // But (some of) the database settings also need to be stored.
                    mVm.updateStyle(mListAdapter.getItem(positionStart));
                    onChanged();
                }

                @Override
                public void onChanged() {
                    prepareMenu(getToolbar().getMenu(), mVm.getSelectedPosition());
                    // We'll save the list order in onPause.
                    mVm.setDirty(true);
                }
            };

    @SuppressLint("NotifyDataSetChanged")
    private final ActivityResultLauncher<EditStyleContract.Input> mEditStyleContract =
            registerForActivityResult(new EditStyleContract(), data -> {
                if (data != null) {
                    //noinspection ConstantConditions
                    @Nullable
                    final ListStyle style = mVm.getStyle(getContext(), data.uuid);

                    if (data.modified) {
                        if (style != null) {
                            mVm.onStyleEdited(style, data.templateUuid);
                        }

                        // always update ALL rows as the order might have changed
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        // The style was not modified. If this was a cloned (new) style,
                        // discard it by deleting the SharedPreferences file
                        if (style != null && style.getId() == 0) {
                            getContext().deleteSharedPreferences(style.getUuid());
                        }
                    }
                }
            });
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final ListStyle selectedStyle = mVm.getSelectedStyle();
                    final String uuid = selectedStyle != null ? selectedStyle.getUuid() : null;
                    final Intent resultIntent = PreferredStylesContract
                            .createResultIntent(uuid, mVm.isDirty());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /** View Binding. */
    private FragmentEditStylesBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditStylesBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_styles_long);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        //noinspection ConstantConditions
        mListAdapter = new ListStylesAdapter(getContext(), mVm.getList(),
                                             vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.list.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.list.setHasFixedSize(true);
        mVb.list.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.list);

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    public void onPause() {
        mVm.updateMenuOrder();
        super.onPause();
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu o prepare
     */
    private void prepareMenu(@NonNull final Menu menu,
                             final int position) {
        final ListStyle style = position == RecyclerView.NO_POSITION
                                ? null : mVm.getStyle(position);

        // only user styles can be edited/deleted
        final boolean isUserStyle = style != null && style.isUserDefined();
        menu.findItem(R.id.MENU_EDIT).setVisible(isUserStyle);
        menu.findItem(R.id.MENU_DELETE).setVisible(isUserStyle);

        // only if a style is selected
        menu.findItem(R.id.MENU_DUPLICATE).setVisible(style != null);
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible(style != null);
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        final ListStyle style = mVm.getStyle(position);

        if (itemId == R.id.MENU_EDIT) {
            mEditStyleContract.launch(EditStyleContract.edit(style));
            return true;

        } else if (itemId == R.id.MENU_DUPLICATE) {
            mEditStyleContract.launch(EditStyleContract.duplicate(style));
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            //noinspection ConstantConditions
            StandardDialogs.deleteStyle(getContext(), style, () -> {
                mVm.deleteStyle(getContext(), style);
                mListAdapter.notifyItemRemoved(position);
                mListAdapter.notifyItemChanged(mVm.findPreferredAndSelect(position));
            });
            return true;

        } else if (itemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection ConstantConditions
            StandardDialogs.purgeBLNS(context, R.string.lbl_style, style.getLabel(context),
                                      () -> mVm.purgeBLNS(style.getId()));
            return true;
        }

        return false;
    }

    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        private final RowEditPreferredStylesBinding vb;

        Holder(@NonNull final RowEditPreferredStylesBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.editing_styles, menu);
            prepareMenu(menu, mVm.getSelectedPosition());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            return PreferredStylesFragment.this.onMenuItemSelected(menuItem,
                                                                   mVm.getSelectedPosition());
        }
    }

    private class ListStylesAdapter
            extends RecyclerViewAdapterBase<ListStyle, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of styles
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        ListStylesAdapter(@NonNull final Context context,
                          @NonNull final List<ListStyle> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowEditPreferredStylesBinding vb = RowEditPreferredStylesBinding
                    .inflate(getLayoutInflater(), parent, false);
            final Holder holder = new Holder(vb);

            // click the checkbox -> toggle the 'preferred' status
            //noinspection ConstantConditions
            holder.mCheckableButton.setOnClickListener(v -> togglePreferredStatus(holder));

            // click -> set the row as 'selected'.
            // Do NOT modify the 'preferred' state of the row here.
            holder.rowDetailsView.setOnClickListener(v -> {
                // first update the previous, now unselected, row.
                notifyItemChanged(mVm.getSelectedPosition());
                // store the newly selected row.
                mVm.setSelectedPosition(holder.getBindingAdapterPosition());
                // update the newly selected row.
                notifyItemChanged(mVm.getSelectedPosition());
            });

            // long-click -> context menu
            holder.rowDetailsView.setOnLongClickListener(v -> {
                final ExtPopupMenu popupMenu = new ExtPopupMenu(getContext())
                        .inflate(R.menu.editing_styles)
                        .setGroupDividerEnabled();
                prepareMenu(popupMenu.getMenu(), holder.getBindingAdapterPosition());

                popupMenu.showAsDropDown(v, menuItem ->
                        onMenuItemSelected(menuItem, holder.getBindingAdapterPosition()));
                return true;
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     @SuppressLint("RecyclerView") final int position) {
            super.onBindViewHolder(holder, position);

            final ListStyle style = getItem(position);

            final Context context = getContext();

            holder.vb.name.setText(style.getLabel(context));
            holder.vb.type.setText(style.getTypeDescription(context));
            holder.vb.groups.setText(style.getGroups().getSummaryText(context));

            // set the 'preferred' state of the current row
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(style.isPreferred());

            // set the 'selected' state of the current row
            holder.itemView.setSelected(position == mVm.getSelectedPosition());
        }

        /**
         * The user clicked the checkable button of the row; i.e. changed the 'preferred' status.
         *
         * <ol>User checked the row:
         * <li>set the row/style 'preferred'</li>
         * <li>set the row 'selected'</li>
         * </ol>
         * <ol>User unchecked the row:
         * <li>set the row to 'not preferred'</li>
         * <li>look up and down in the list to find a 'preferred' row, and set it 'selected'</li>
         * </ol>
         *
         * @param holder the checked row.
         */
        private void togglePreferredStatus(@NonNull final Holder holder) {
            int position = holder.getBindingAdapterPosition();

            // toggle the 'preferred' state of the current position/style
            final ListStyle style = getItem(position);
            final boolean checked = !style.isPreferred();
            style.setPreferred(checked);
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(checked);

            // if the current position/style is no longer 'preferred' but was 'selected' ...
            if (!checked && mVm.getSelectedPosition() == position) {
                // find the best candidate position/style and make that one the 'selected'
                position = mVm.findPreferredAndSelect(position);
            }

            notifyItemChanged(position);
        }

        @Override
        public boolean onItemMove(final int fromPosition,
                                  final int toPosition) {
            mVm.onItemMove(fromPosition, toPosition);
            return super.onItemMove(fromPosition, toPosition);
        }
    }
}
