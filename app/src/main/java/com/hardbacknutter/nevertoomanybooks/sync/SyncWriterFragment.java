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
package com.hardbacknutter.nevertoomanybooks.sync;

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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncExportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

public class SyncWriterFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SyncWriterFragment";
    private ToolbarMenuProvider toolbarMenuProvider;
    /** The ViewModel. */
    private SyncWriterViewModel vm;
    /** View Binding. */
    private FragmentSyncExportBinding vb;
    @Nullable
    private ProgressDelegate progressDelegate;

    @SuppressWarnings("TypeMayBeWeakened")
    @NonNull
    public static Fragment create(@NonNull final SyncServer syncServer) {
        final Fragment fragment = new SyncWriterFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(SyncServer.BKEY_SITE, syncServer);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(SyncWriterViewModel.class);
        vm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSyncExportBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(toolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(vm.getSyncWriterHelper().getSyncServer().getLabelResId());

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        vm.onWriteDataCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        vm.onWriteDataFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        vm.onWriteDataFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        vb.cbxBooks.setChecked(true);
        vb.cbxBooks.setEnabled(true);

        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vm.getSyncWriterHelper().setRecordType(isChecked, RecordType.Cover);
            toolbarMenuProvider.onPrepareMenu(getToolbar().getMenu());
        });

        vb.infExportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        vb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> vm
                .getSyncWriterHelper()
                .setIncremental(checkedId == vb.rbExportNewAndUpdated.getId()));

        vb.cbxDeleteRemovedBooks.setOnCheckedChangeListener((v, isChecked) -> vm
                .getSyncWriterHelper().setDeleteLocalBooks(isChecked));

        if (!vm.isRunning()) {
            // The task is NOT yet running.
            // Show either the full-options screen or the quick-options dialog
            if (vm.isQuickOptionsAlreadyShown()) {
                showOptions();
            } else {
                showQuickOptions();
            }
        }
    }

    private void showQuickOptions() {
        vm.setQuickOptionsAlreadyShown();

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(vm.getSyncWriterHelper().getSyncServer().getLabelResId())
                .setMessage(R.string.action_synchronize)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.btn_options, (d, w) -> {
                    d.dismiss();
                    showOptions();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    vm.startExport();
                })
                .create()
                .show();
    }

    /**
     * Export Step 1b: Show the full options screen to the user.
     */
    private void showOptions() {
        final SyncWriterHelper helper = vm.getSyncWriterHelper();

        final Set<RecordType> recordTypes = helper.getRecordTypes();
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final boolean incremental = helper.isIncremental();
        vb.rbExportAll.setChecked(!incremental);
        vb.rbExportNewAndUpdated.setChecked(incremental);

        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void onExportCancelled(
            @NonNull final LiveDataEvent<TaskResult<SyncWriterResults>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.DELAY_LONG_MS);
        });
    }

    private void onExportFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_unknown));

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_export_failed)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        });
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(
            @NonNull final LiveDataEvent<TaskResult<SyncWriterResults>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::requireResult).ifPresent(results -> {
            final List<String> items = extractExportedItems(results);
            if (items.isEmpty()) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.menu_backup_and_export)
                        .setMessage(R.string.warning_no_matching_book_found)
                        .setPositiveButton(R.string.action_done, (d, w) -> {
                            //noinspection ConstantConditions
                            getActivity().setResult(Activity.RESULT_OK);
                            getActivity().finish();
                        })
                        .create()
                        .show();
            } else {
                final String itemList = items
                        .stream()
                        .map(s -> getString(R.string.list_element, s))
                        .collect(Collectors.joining("\n"));

                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.progress_end_export_successful)
                        .setMessage(itemList)
                        .setPositiveButton(R.string.action_done, (d, w) -> {
                            final Intent resultIntent = new Intent()
                                    .putExtra(SyncContractBase.BKEY_RESULT,
                                              SyncContractBase.RESULT_WRITE_DONE);
                            //noinspection ConstantConditions
                            getActivity().setResult(Activity.RESULT_OK, resultIntent);
                            getActivity().finish();
                        })
                        .create()
                        .show();
            }
        });
    }

    @NonNull
    private List<String> extractExportedItems(@NonNull final SyncWriterResults result) {
        final List<String> items = new ArrayList<>();

        if (result.getBookCount() > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_books),
                                String.valueOf(result.getBookCount())));
        }
        if (result.getCoverCount() > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_covers),
                                String.valueOf(result.getCoverCount())));
        }
        return items;
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection ConstantConditions
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.menu_backup_and_export)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection ConstantConditions
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.toolbar_action_go, menu);

            final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
            final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
            button.setText(menuItem.getTitle());
            button.setOnClickListener(v -> onMenuItemSelected(menuItem));

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ACTION_CONFIRM)
                .setEnabled(vm.isReadyToGo());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                vm.startExport();
                return true;
            }
            return false;
        }
    }
}
