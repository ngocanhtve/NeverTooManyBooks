/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.AlternativeExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

public class CoverBrowserViewModel
        extends ViewModel {

    private static final String TAG = "CoverBrowserViewModel";

    /** Holder for all active tasks, so we can cancel them if needed. */
    private final Collection<AsyncTask> mAllTasks = new HashSet<>();

    /** List of all alternative editions/isbn for the given ISBN. */
    private final MutableLiveData<ArrayList<String>> mEditions = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mGalleryImage = new MutableLiveData<>();

    private final MutableLiveData<FileInfo> mSwitcherImage = new MutableLiveData<>();
    /** ISBN of book to fetch other editions of. */
    private String mBaseIsbn;
    /** Handles downloading, checking and cleanup of files. */
    private CoverBrowserViewModel.FileManager mFileManager;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mBaseIsbn == null) {
            mBaseIsbn = args.getString(DBDefinitions.KEY_ISBN);
            Objects.requireNonNull(mBaseIsbn, "ISBN must be passed in args");
            mFileManager = new FileManager(context, args);
        }
    }

    @Override
    protected void onCleared() {
        cancelAllTasks();

        if (mFileManager != null) {
            mFileManager.purge();
        }
    }

    public FileManager getFileManager() {
        return mFileManager;
    }

    /**
     * Keep track of all tasks so we can remove/cancel at will.
     *
     * @param task to add
     * @param <T>  type of task
     *
     * @return task for chaining
     */
    private <T extends AsyncTask> T addTask(@NonNull final T task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task);
        }
        return task;
    }

    /**
     * Remove a specific task from the set (NOT the queue).
     * This method should be called *after* a task has notified us it finished.
     *
     * @param task to remove
     * @param <T>  type of task
     */
    private <T extends AsyncTask> void removeTask(@Nullable final T task) {
        if (task != null) {
            synchronized (mAllTasks) {
                mAllTasks.remove(task);
            }
        }
    }

    /**
     * Cancel all active tasks.
     */
    private void cancelAllTasks() {
        // cancel any active tasks.
        synchronized (mAllTasks) {
            for (AsyncTask task : mAllTasks) {
                task.cancel(true);
            }
        }
    }

    /**
     * Start a search for alternative editions of the book (using the isbn).
     *
     * @param context Current context
     */
    public void fetchEditions(@NonNull final Context context) {
        ArrayList<Site> sites = SearchSites.getSites(context, SearchSites.ListType.AltEditions);
        addTask(new GetEditionsTask(mBaseIsbn, sites, this)).execute();
    }

    public MutableLiveData<ArrayList<String>> getEditions() {
        return mEditions;
    }

    private void onGetEditionsTaskFinished(@NonNull final GetEditionsTask task,
                                           @Nullable final ArrayList<String> editions) {
        removeTask(task);
        mEditions.setValue(editions);
    }


    /**
     * @param isbn to search for, <strong>must</strong> be valid.
     */
    public void fetchGalleryImage(@NonNull final String isbn) {
        addTask(new GetGalleryImageTask(isbn, mFileManager, this))
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public MutableLiveData<FileInfo> getGalleryImage() {
        return mGalleryImage;
    }

    private void onGetGalleryImageTaskFinished(@NonNull final GetGalleryImageTask task,
                                               @NonNull final FileInfo fileInfo) {
        removeTask(task);
        mGalleryImage.setValue(fileInfo);
    }


    public void fetchSwitcherImage(@NonNull final CoverBrowserViewModel.FileInfo fileInfo) {
        // use the alternative executor, so we get a result back without
        // waiting on the gallery tasks.
        addTask(new GetSwitcherImageTask(fileInfo, mFileManager, this))
                .executeOnExecutor(AlternativeExecutor.THREAD_POOL_EXECUTOR);
    }

    public MutableLiveData<FileInfo> getSwitcherImage() {
        return mSwitcherImage;
    }

    private void onGetSwitcherImageTaskFinished(@NonNull final GetSwitcherImageTask task,
                                                @NonNull final FileInfo fileInfo) {
        removeTask(task);
        mSwitcherImage.setValue(fileInfo);
    }

    /**
     * Value class to return info about a file.
     */
    public static class FileInfo
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<FileInfo> CREATOR = new Creator<FileInfo>() {
            @Override
            public FileInfo createFromParcel(@NonNull final Parcel in) {
                return new FileInfo(in);
            }

            @Override
            public FileInfo[] newArray(final int size) {
                return new FileInfo[size];
            }
        };
        @NonNull
        public final String isbn;
        @Nullable
        public SearchEngine.CoverByIsbn.ImageSize size;
        @Nullable
        public String fileSpec;

        /**
         * Constructor.
         */
        FileInfo(@NonNull final String isbn,
                 @NonNull final SearchEngine.CoverByIsbn.ImageSize size,
                 @NonNull final String fileSpec) {
            this.isbn = isbn;
            this.size = size;
            this.fileSpec = fileSpec;
        }

        /**
         * Failure constructor.
         * <p>
         * Some functions need to return a @NonNull FileInfo with a valid isbn while the fileSpec
         * can be null. Use this constructor to do that.
         */
        FileInfo(@NonNull final String isbn) {
            this.isbn = isbn;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private FileInfo(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            isbn = in.readString();
            fileSpec = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(isbn);
            dest.writeString(fileSpec);
        }

        @NonNull
        @Override
        public String toString() {
            return "FileInfo{"
                   + "isbn='" + isbn + '\''
                   + ", size=" + size
                   + ", fileSpec='" + fileSpec + '\''
                   + '}';
        }
    }

    /**
     * Handles downloading, checking and cleanup of files.
     */
    public static class FileManager {

        /** The minimum side (height/width) and image has to be to be considered valid. */
        static final int MIN_IMAGE_SIDE = 10;

        /**
         * Downloaded files.
         * key = isbn + '_' + size.
         */
        private final Map<String, FileInfo> mFiles = Collections.synchronizedMap(new HashMap<>());

        /** Sites the user wants to search for cover images. */
        private ArrayList<Site> mSearchSitesForCovers;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param args    arguments
         */
        FileManager(@NonNull final Context context,
                    @NonNull final Bundle args) {
            mSearchSitesForCovers = args.getParcelableArrayList(SearchSites.BKEY_COVERS_SITES);
            if (mSearchSitesForCovers == null) {
                mSearchSitesForCovers = SearchSites.getSites(context, SearchSites.ListType.Covers);
            }
        }

        /**
         * Check if a file is an image with an acceptable size.
         *
         * @param file to check
         *
         * @return {@code true} if file is acceptable.
         */
        private boolean isGood(@NonNull final File file) {
            boolean ok = false;

            if (file.exists() && file.length() != 0) {
                try {
                    // Just read the image files to get file size
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opt);
                    // If too small, it's no good
                    ok = opt.outHeight >= MIN_IMAGE_SIDE && opt.outWidth >= MIN_IMAGE_SIDE;
                } catch (@NonNull final RuntimeException e) {
                    // Failed to decode; probably not an image
                    ok = false;
                    Logger.error(TAG, e, "Unable to decode file");
                }
            }

            // cleanup bad files.
            if (!ok) {
                StorageUtils.deleteFile(file);
            }
            return ok;
        }

        /**
         * Download a file according to preference of ImageSize and Site..
         * <p>
         * We loop on ImageSize first, and then for each ImageSize we loop again on Site.<br>
         * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
         * The first Site which has an image is accepted.
         * <p>
         *
         * @param isbn       to search for, <strong>must</strong> be valid.
         * @param imageSizes a list of images sizes in order of preference
         *
         * @return a {@link FileInfo} object with or without a valid fileSpec.
         */
        @NonNull
        @WorkerThread
        FileInfo download(@NonNull final Context appContext,
                          @NonNull final String isbn,
                          @NonNull final SearchEngine.CoverByIsbn.ImageSize... imageSizes) {

            // we will disable sites on the fly for the *current* search without modifying the list.
            @SearchSites.Id
            int currentSearchSites = SearchSites.getEnabledSites(mSearchSitesForCovers);

            // we need to use the size as the outer loop (and not inside of getCoverImage itself).
            // the idea is to check all sites for the same size first.
            // if none respond with that size, try the next size inline.
            // The other way around we might get a site/small-size instead of otherSite/better-size.
            for (SearchEngine.CoverByIsbn.ImageSize size : imageSizes) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = mFiles.get(key);

                // Do we already have a file and is it good ?
                if ((fileInfo != null)
                    && fileInfo.fileSpec != null
                    && !fileInfo.fileSpec.isEmpty()
                    && isGood(new File(fileInfo.fileSpec))) {

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                        Log.d(TAG, "download|FILESYSTEM|fileInfo=" + fileInfo);
                    }
                    // use it
                    return fileInfo;

                } else {
                    // it was present but bad, remove it.
                    mFiles.remove(key);
                }

                for (Site site : mSearchSitesForCovers) {
                    // Should we search this site ?
                    if ((currentSearchSites & site.id) != 0) {
                        SearchEngine engine = site.getSearchEngine();

                        boolean isAvailable = engine instanceof SearchEngine.CoverByIsbn
                                              && engine.isAvailable();
                        if (isAvailable) {
                            fileInfo = download(appContext, (SearchEngine.CoverByIsbn) engine,
                                                isbn, size);
                            if (fileInfo != null) {
                                return fileInfo;
                            }

                            // if the site we just searched only supports one image,
                            // disable it for THIS search
                            if (!((SearchEngine.CoverByIsbn) engine).hasMultipleSizes()) {
                                currentSearchSites &= ~site.id;
                            }
                        } else {
                            // if the site we just searched was not available,
                            // disable it for THIS search
                            currentSearchSites &= ~site.id;
                        }
                    }
                }

                // give up
                mFiles.remove(key);
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(isbn);
        }

        /**
         * Try to get an image from the specified engine.
         *
         * @param appContext   Application context
         * @param searchEngine to use
         * @param isbn         to search for, <strong>must</strong> be valid.
         * @param size         to get
         *
         * @return a FileInfo object with a valid fileSpec, or {@code null} if not found.
         */
        @Nullable
        private FileInfo download(@NonNull final Context appContext,
                                  @NonNull final SearchEngine.CoverByIsbn searchEngine,
                                  @NonNull final String isbn,
                                  @NonNull final SearchEngine.CoverByIsbn.ImageSize size) {

            @Nullable
            File file = searchEngine.getCoverImage(appContext, isbn, size);
            if (file != null && isGood(file)) {
                String key = isbn + '_' + size;
                FileInfo fileInfo = new FileInfo(isbn, size, file.getAbsolutePath());
                mFiles.put(key, fileInfo);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Log.d(TAG, "download|FOUND|fileInfo=" + fileInfo);
                }
                return fileInfo;

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVER_BROWSER) {
                    Log.d(TAG, "download|MISSING"
                               + "|engine=" + appContext.getString(searchEngine.getNameResId())
                               + "|isbn=" + isbn
                               + "|size=" + size);
                }
            }
            return null;
        }

        /**
         * Get the requested FileInfo, if available, otherwise return {@code null}.
         *
         * @param isbn  to search
         * @param sizes required sizes in order to look for. First found is used.
         *
         * @return the FileInfo
         */
        @NonNull
        public FileInfo getFile(@NonNull final String isbn,
                                @NonNull final SearchEngine.CoverByIsbn.ImageSize... sizes) {
            for (SearchEngine.CoverByIsbn.ImageSize size : sizes) {
                FileInfo fileInfo = mFiles.get(isbn + '_' + size);
                if (fileInfo != null && fileInfo.fileSpec != null && !fileInfo.fileSpec.isEmpty()) {
                    return fileInfo;
                }
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(isbn);
        }

        /**
         * Clean up all files.
         */
        void purge() {
            for (FileInfo fileInfo : mFiles.values()) {
                if (fileInfo != null
                    && fileInfo.fileSpec != null
                    && !fileInfo.fileSpec.isEmpty()) {
                    StorageUtils.deleteFile(new File(fileInfo.fileSpec));
                }
            }
            mFiles.clear();
        }
    }

    /**
     * Fetch alternative edition isbn's.
     */
    static class GetEditionsTask
            extends AsyncTask<Void, Void, ArrayList<String>> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final ArrayList<Site> mSites;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;

        /**
         * Constructor.
         *
         * @param isbn         to search for
         * @param taskListener to send results to
         */
        @UiThread
        GetEditionsTask(@NonNull final String isbn,
                        @NonNull final ArrayList<Site> sites,
                        @NonNull final CoverBrowserViewModel taskListener) {
            mIsbn = isbn;
            mSites = sites;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected ArrayList<String> doInBackground(final Void... params) {
            Thread.currentThread().setName("GetEditionsTask " + mIsbn);

            Context context = App.getAppContext();

            ArrayList<String> editions = new ArrayList<>();
            for (Site site : mSites) {
                if (site.isEnabled()) {
                    try {
                        SearchEngine searchEngine = site.getSearchEngine();
                        if (searchEngine instanceof SearchEngine.AlternativeEditions) {
                            editions.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                                    .getAlternativeEditions(context, mIsbn));
                        }
                    } catch (@NonNull RuntimeException ignore) {
                    }
                }
            }
            return editions;
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final ArrayList<String> result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetEditionsTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onPostExecute|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }
    }

    /**
     * Fetch a thumbnail and stick it into the gallery.
     */
    static class GetGalleryImageTask
            extends AsyncTask<Void, Void, FileInfo> {

        @NonNull
        private final String mIsbn;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;

        /**
         * Constructor.
         *
         * @param isbn         to search for, <strong>must</strong> be valid.
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetGalleryImageTask(@NonNull final String isbn,
                            @NonNull final FileManager fileManager,
                            @NonNull final CoverBrowserViewModel taskListener) {
            mIsbn = isbn;
            mFileManager = fileManager;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetGalleryImageTask " + mIsbn);
            Context appContext = App.getAppContext();
            try {
                return mFileManager.download(appContext,
                                             mIsbn,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.CoverByIsbn.ImageSize.Small,
                                             SearchEngine.CoverByIsbn.ImageSize.Medium,
                                             SearchEngine.CoverByIsbn.ImageSize.Large);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mIsbn);
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final FileInfo result) {
            // always callback; even with a bad result.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetGalleryImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onPostExecute|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }

        @Override
        protected void onCancelled(@NonNull final FileInfo result) {
            // let the caller clean up.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetGalleryImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onCancelled|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }
    }

    /**
     * Fetch a full-size image and stick it into the ImageSwitcher.
     */
    static class GetSwitcherImageTask
            extends AsyncTask<Void, Void, FileInfo> {

        @NonNull
        private final FileInfo mFileInfo;
        @NonNull
        private final FileManager mFileManager;
        @NonNull
        private final WeakReference<CoverBrowserViewModel> mTaskListener;


        /**
         * Constructor.
         *
         * @param fileInfo     book to search
         * @param fileManager  for downloads
         * @param taskListener to send results to
         */
        @UiThread
        GetSwitcherImageTask(@NonNull final CoverBrowserViewModel.FileInfo fileInfo,
                             @NonNull final FileManager fileManager,
                             @NonNull final CoverBrowserViewModel taskListener) {
            mFileInfo = fileInfo;
            mFileManager = fileManager;
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected FileInfo doInBackground(final Void... params) {
            Thread.currentThread().setName("GetSwitcherImageTask " + mFileInfo.isbn);
            Context appContext = App.getAppContext();
            try {
                return mFileManager.download(appContext,
                                             mFileInfo.isbn,
                                             // try to get a picture in this order of size.
                                             // Stops at first one found.
                                             SearchEngine.CoverByIsbn.ImageSize.Large,
                                             SearchEngine.CoverByIsbn.ImageSize.Medium,
                                             SearchEngine.CoverByIsbn.ImageSize.Small);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
                // tad annoying... java.io.InterruptedIOException: thread interrupted
                // can be thrown, but for some reason javac does not think so.
            }
            // we failed, but we still need to return the isbn.
            return new FileInfo(mFileInfo.isbn);
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final FileInfo result) {
            // always callback; even with a bad result.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetSwitcherImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onPostExecute|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }

        @Override
        protected void onCancelled(@NonNull final FileInfo result) {
            // let the caller clean up.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetSwitcherImageTaskFinished(this, result);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onCancelled|" + Logger.WEAK_REFERENCE_DEAD);
                }
            }
        }
    }
}
