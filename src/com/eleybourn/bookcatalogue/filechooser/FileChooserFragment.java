/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.filechooser;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.filechooser.FileLister.FileListerListener;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter.ViewProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Fragment to display a simple directory/file browser.
 *
 * @author pjw
 */
public class FileChooserFragment extends Fragment implements FileListerListener {
    private static final String BKEY_ROOT_PATH = "rootPath";
    private static final String BKEY_FILE_NAME = "fileName";
    private static final String BKEY_LIST = "list";

    private File mRootPath;

    private EditText mFilenameField;
    private TextView mPathField;

    // Create an empty one in case we are rotated before generated.
    @Nullable
    private ArrayList<FileDetails> mList = new ArrayList<>();

    /** Create a new chooser fragment */
    @NonNull
    public static FileChooserFragment newInstance(@NonNull final File root, @NonNull final String fileName) {
        String path;
        // Turn the passed File into a directory
        if (root.isDirectory()) {
            path = root.getAbsolutePath();
        } else {
            path = root.getParent();
        }

        // Build the fragment and save the details
        FileChooserFragment frag = new FileChooserFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_ROOT_PATH, path);
        args.putString(BKEY_FILE_NAME, fileName);
        frag.setArguments(args);

        return frag;
    }

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(PathChangedListener.class.isInstance(context)))
            throw new RTE.MustImplementException(context, PathChangedListener.class);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_chooser, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Handle the 'up' item; go to the next directory up
        //noinspection ConstantConditions
        getView().findViewById(R.id.row_path_up).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUp();
            }
        });

        mFilenameField = getView().findViewById(R.id.file_name);
        mPathField = getView().findViewById(R.id.path);

        // If it's new, just build from scratch, otherwise, get the saved directory and list
        if (savedInstanceState == null) {
            // getArguments() could be null, but the string we fetch will never be null (may be empty)
            //noinspection ConstantConditions
            mRootPath = new File(getArguments().getString(BKEY_ROOT_PATH));

            String fileName = getArguments().getString(BKEY_FILE_NAME);
            mFilenameField.setText(fileName);
            mPathField.setText(mRootPath.getAbsolutePath());
            tellActivityPathChanged();
        } else {
            mRootPath = new File(Objects.requireNonNull(savedInstanceState.getString(BKEY_ROOT_PATH)));

            ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_LIST);
            Objects.requireNonNull(list);
            this.onGotFileList(mRootPath, list);
        }
    }

    /**
     * Convenience method to tell our activity the path has changed.
     */
    private void tellActivityPathChanged() {
        ((PathChangedListener) requireActivity()).onPathChanged(mRootPath);
    }

    /**
     * Handle the 'Up' action
     */
    private void handleUp() {
        String parent = mRootPath.getParent();
        if (parent == null) {
            //Snackbar.make(this.getView(), R.string.no_parent_directory_found, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(requireActivity(), R.string.no_parent_directory_found);
            return;
        }
        mRootPath = new File(parent);

        tellActivityPathChanged();
    }

    /**
     * Save our root path and list
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putString(BKEY_ROOT_PATH, mRootPath.getAbsolutePath());
        outState.putParcelableArrayList(BKEY_LIST, mList);
        super.onSaveInstanceState(outState);
    }

    /**
     * Accessor
     *
     * @return File
     */
    @NonNull
    public File getSelectedFile() {
        return new File(mRootPath.getAbsolutePath() + File.separator + mFilenameField.getText().toString().trim());
    }

    /**
     * Display the list
     *
     * @param root Root directory
     * @param list List of FileDetails
     */
    @Override
    public void onGotFileList(@NonNull final File root, @NonNull final ArrayList<FileDetails> list) {
        mRootPath = root;
        mPathField.setText(mRootPath.getAbsolutePath());

        // Setup and display the list
        mList = list;
        // We pass no view ID since each item can provide the view id
        DirectoryAdapter adapter = new DirectoryAdapter(requireActivity(), mList);
        //noinspection ConstantConditions
        ListView lv = getView().findViewById(android.R.id.list);
        lv.setAdapter(adapter);
    }

    /**
     * Interface that the containing Activity must implement. Called when user changes path.
     *
     * @author pjw
     */
    public interface PathChangedListener {
        void onPathChanged(File root);
    }

    /** Interface for details of files in current directory */
    public interface FileDetails extends ViewProvider, Parcelable {
        /** Get the underlying File object */
        @NonNull
        File getFile();

        /** Called to fill in the details of this object in the View provided by the ViewProvider implementation */
        void onSetupView(@NonNull final View convertView, @NonNull final Context context);
    }

    /**
     * List Adapter for FileDetails objects
     *
     * @author pjw
     */
    protected class DirectoryAdapter extends SimpleListAdapter<FileDetails> {

        DirectoryAdapter(@NonNull final Context context, @NonNull final ArrayList<FileDetails> items) {
            super(context, 0, items);
        }

        @Override
        protected void onSetupView(@NonNull final View convertView, @NonNull final FileDetails item) {
            item.onSetupView(convertView, requireActivity());
        }

        @Override
        protected void onRowClick(@NonNull final View v, @Nullable final FileDetails item, final int position) {
            if (item != null) {
                if (item.getFile().isDirectory()) {
                    mRootPath = item.getFile();
                    tellActivityPathChanged();
                } else {
                    mFilenameField.setText(item.getFile().getName());
                }
            }
        }
    }

}
