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
package com.hardbacknutter.nevertoomanybooks.search;

import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.Result;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "BookSearchByIsbnFrag";
    private static final String BKEY_SCANNER_ACTIVITY_STARTED = TAG + ":started";

    /** flag indicating the scanner Activity is already started. */
    private boolean scannerActivityStarted;
    /** View Binding. */
    private FragmentBooksearchByIsbnBinding vb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;
    private SearchBookByIsbnViewModel vm;
    @Nullable
    private BarcodeScanner scanner;

    /** Importing a list of ISBN. */
    private final ActivityResultLauncher<String> openUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(),
                    o -> o.ifPresent(this::onOpenUri));

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            scannerActivityStarted = savedInstanceState.getBoolean(BKEY_SCANNER_ACTIVITY_STARTED,
                    false);
        }

        vm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        vm.init(getArguments());
        vm.onScanQueueUpdate().observe(getViewLifecycleOwner(), this::onQueueUpdated);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_search_isbn);

        vb.isbn.setText(coordinator.getIsbnSearchText());

        vb.keypad.key0.setOnClickListener(v -> vb.isbn.onKey('0'));
        vb.keypad.key1.setOnClickListener(v -> vb.isbn.onKey('1'));
        vb.keypad.key2.setOnClickListener(v -> vb.isbn.onKey('2'));
        vb.keypad.key3.setOnClickListener(v -> vb.isbn.onKey('3'));
        vb.keypad.key4.setOnClickListener(v -> vb.isbn.onKey('4'));
        vb.keypad.key5.setOnClickListener(v -> vb.isbn.onKey('5'));
        vb.keypad.key6.setOnClickListener(v -> vb.isbn.onKey('6'));
        vb.keypad.key7.setOnClickListener(v -> vb.isbn.onKey('7'));
        vb.keypad.key8.setOnClickListener(v -> vb.isbn.onKey('8'));
        vb.keypad.key9.setOnClickListener(v -> vb.isbn.onKey('9'));
        vb.keypad.keyX.setOnClickListener(v -> vb.isbn.onKey('X'));

        vb.isbnDel.setOnClickListener(v -> vb.isbn.onKey(KeyEvent.KEYCODE_DEL));
        vb.isbnDel.setOnLongClickListener(v -> {
            vb.isbn.setText("");
            return true;
        });

        // The search preference determines the level here; NOT the 'edit book'
        final ISBN.Validity isbnValidityCheck = coordinator.isStrictIsbn() ? ISBN.Validity.Strict
                                                                           : ISBN.Validity.None;

        isbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnCleanupTextWatcher);

        isbnValidationTextWatcher =
                new ISBN.ValidationTextWatcher(vb.lblIsbn, vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnValidationTextWatcher);

        //noinspection ConstantConditions
        vb.keypad.btnSearch.setOnClickListener(
                btn -> onBarcodeEntered(vb.isbn.getText().toString().trim()));

        vb.btnClearQueue.setOnClickListener(v -> vm.clearQueue());

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            Site.promptToRegister(getContext(), coordinator.getSiteList(),
                    "searchByIsbn", this::afterOnViewCreated);
        } else {
            afterOnViewCreated();
        }

        vb.btnStopScanning.setOnClickListener(v -> stopScanner());

    }

    /** After a successful scan/search, the data is offered for editing. */
    private final ActivityResultLauncher<Long> editExistingBookLauncher =
            registerForActivityResult(new EditBookByIdContract(),
                                      o -> o.ifPresent(this::onBookEditingDone));

    /**
     * Start scanner activity.
     */
    private void startScannerActivity() {
        if (!scannerActivityStarted) {
            scannerActivityStarted = true;
            //noinspection ConstantConditions
            scannerActivityLauncher.launch(ScannerContract.createDefaultOptions(getContext()));
        }
    }

    private final ActivityResultLauncher<ScanOptions> scannerActivityLauncher =
            registerForActivityResult(new ScannerContract(), o -> {
                scannerActivityStarted = false;
                if (o.isPresent()) {
                    onBarcodeScanned(o.get());
                } else {
                    stopScanner();
                }
            });

    @Override
    @NonNull
    protected Bundle getResultData() {
        return vm.getResultData();
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentBooksearchByIsbnBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    /**
     * Search with ISBN or, if allowed, with a generic code.
     *
     * @param code to search for
     */
    private void prepareSearch(@NonNull final ISBN code) {
        coordinator.setIsbnSearchText(code.asText());

        // See if ISBN already exists in our database, if not then start the search.
        final ArrayList<Pair<Long, String>> existingIds = vm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            startSearch();

        } else {
            // always quit scanning as the safe option, the user can restart the scanner,
            // or restart the queue processing at will.
            stopScanner();

            // we always use the first one... really should offer the user a choice.
            final long firstFound = existingIds.get(0).first;
            // Show the "title (isbn)" with a caution message
            final String msg = getString(R.string.a_bracket_b_bracket,
                    existingIds.get(0).second, code.asText())
                               + "\n\n" + getString(R.string.confirm_duplicate_book_message);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(msg)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (d, w) -> onClearSearchCriteria())
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w)
                            -> editExistingBookLauncher.launch(firstFound))
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> startSearch())
                    .create()
                    .show();
        }
    }

    private void afterOnViewCreated() {
        if (vm.isAutoStart()) {
            startScanner();
        }
    }

    @Override
    void onSearchCancelled(@NonNull final LiveDataEvent<TaskResult<Bundle>> message) {
        super.onSearchCancelled(message);
        // Quit scan mode until the user manually starts it again
        stopScanner();
    }

    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        super.onBookEditingDone(data);
        if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Single) {
            // scan another book until the user cancels
            startScanner();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_SCANNER_ACTIVITY_STARTED, scannerActivityStarted);
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        coordinator.setIsbnSearchText(vb.isbn.getText().toString().trim());
    }

    private void startScanner() {
        if (BuildConfig.EMBEDDED_BARCODE_SCANNER) {
            startScannerEmbedded();
        } else {
            startScannerActivity();
        }
    }

    private void startScannerEmbedded() {
        vb.barcodeScannerGroup.setVisibility(View.VISIBLE);
        if (scanner == null) {
            //noinspection ConstantConditions
            scanner = new BarcodeScanner.Builder()
                    .setCodeFamily(BarcodeFamily.Product)
                    .build(getContext(), getViewLifecycleOwner(),
                            vb.cameraPreview.getSurfaceProvider());

            if (vb.cameraViewFinder.isShowResultPoints()) {
                scanner.setResultPointListener(vb.cameraViewFinder);
            }

            getLifecycle().addObserver(scanner);
        }

        scanner.startScan(new DecoderResultListener() {
            @Nullable
            private String lastCode;

            @Override
            public void onResult(@NonNull final Result result) {
                final String barCode = result.getText();
                if (barCode != null && !barCode.isBlank()) {
                    if (!barCode.equals(lastCode)) {
                        lastCode = barCode;
                        onBarcodeScanned(barCode);
                    }
                } else {
                    stopScanner();
                }
            }

            @Override
            public void onError(@NonNull final String s,
                                @NonNull final Exception e) {
                stopScanner();
                getLifecycle().removeObserver(scanner);
                scanner = null;
            }
        });
    }


    private void onBarcodeScanned(@NonNull final String barCode) {
        final boolean strictIsbn = coordinator.isStrictIsbn();
        final ISBN code = new ISBN(barCode, strictIsbn);

        if (code.isValid(strictIsbn)) {
            if (strictIsbn) {
                //noinspection ConstantConditions
                SoundManager.onValidBarcodeBeep(getContext());
            }

            if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Batch) {
                // batch mode, queue the code, go scan next book
                vm.addToQueue(code);
                startScanner();

            } else {
                // single-scan mode, quit scanning, go search online for the book
                stopScanner();
                vb.isbn.setText(code.asText());
                prepareSearch(code);
            }
        } else {
            //noinspection ConstantConditions
            SoundManager.onInvalidBarcodeBeep(getContext());
            showError(vb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code,
                    code.asText()));

            if (vm.getScannerMode() == SearchBookByIsbnViewModel.ScanMode.Batch) {
                // batch mode, ignore the code, go scan next book
                startScanner();
            } else {
                // single-scan mode, quit scanning, let the user edit the code
                stopScanner();
                vb.isbn.setText(code.asText());
            }
        }
    }

    private void stopScanner() {
        if (BuildConfig.EMBEDDED_BARCODE_SCANNER) {
            if (scanner != null) {
                scanner.stopScanning();
            }
            vb.barcodeScannerGroup.setVisibility(View.GONE);
        }
        vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Off);
    }


    /**
     * The user entered a barcode and clicked the search button.
     */
    private void onBarcodeEntered(final String barCode) {
        final boolean strictIsbn = coordinator.isStrictIsbn();
        final ISBN code = new ISBN(barCode, strictIsbn);

        if (code.isValid(strictIsbn)) {
            prepareSearch(code);
        } else {
            showError(vb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code, barCode));
        }
    }


    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        //mVb.isbn.setText("");
    }

    private void onOpenUri(@NonNull final Uri uri) {
        //noinspection ConstantConditions
        if (!vm.readQueue(getContext(), uri, coordinator.isStrictIsbn())) {
            Snackbar.make(vb.getRoot(), R.string.error_import_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Refresh the queue view(s) and show/hide the 'clear' button.
     *
     * @param queue to display; can be empty
     */
    private void onQueueUpdated(@NonNull final List<ISBN> queue) {
        if (vb.queue.getChildCount() > 0) {
            vb.queue.removeAllViews();
        }

        queue.forEach(code -> {
            final Chip chip = new Chip(getContext(), null, R.attr.appChipInputStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setOnClickListener(v -> {
                final ISBN clickedCode = removeFromQueue(v);
                vb.isbn.setText(clickedCode.asText());
                prepareSearch(clickedCode);
            });
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setTag(code);
            chip.setText(code.asText());
            vb.queue.addView(chip);
        });

        updateQueueViewsVisibility();
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBKey.TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            showError(vb.lblIsbn, R.string.warning_no_matching_book_found);
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }

    private void updateQueueViewsVisibility() {
        final int visibility = vb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        // The queue Chips and the 'clear queue' button
        vb.queueGroup.setVisibility(visibility);
    }

    @NonNull
    private ISBN removeFromQueue(@NonNull final View chip) {
        final ISBN code = (ISBN) chip.getTag();
        // remove and update view manually to avoid flicker
        vm.removeFromQueue(code);
        vb.queue.removeView(chip);
        updateQueueViewsVisibility();
        return code;
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.search_by_isbn, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT)
                .setChecked(coordinator.isStrictIsbn());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_BARCODE_SCAN) {
                vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Single);
                startScanner();
                return true;

            } else if (itemId == R.id.MENU_BARCODE_SCAN_BATCH) {
                vm.setScannerMode(SearchBookByIsbnViewModel.ScanMode.Batch);
                startScanner();
                return true;

            } else if (itemId == R.id.MENU_BARCODE_IMPORT) {
                // See remarks in
                // {@link com.hardbacknutter.nevertoomanybooks.backup.ImportFragment}
                openUriLauncher.launch("*/*");
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                final boolean checked = !menuItem.isChecked();
                coordinator.setStrictIsbn(checked);

                final ISBN.Validity validity = checked ? ISBN.Validity.Strict : ISBN.Validity.None;
                isbnCleanupTextWatcher.setValidityLevel(validity);
                isbnValidationTextWatcher.setValidityLevel(validity);
                return true;
            }

            return false;
        }
    }

}
