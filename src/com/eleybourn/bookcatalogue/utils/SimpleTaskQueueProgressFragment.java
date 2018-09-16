/*
 * @copyright 2012 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;

import java.util.ArrayList;

/**
 * Fragment Class to wrap a trivial progress dialog around (generally) a single task.
 *
 * @author pjw
 */
public class SimpleTaskQueueProgressFragment extends DialogFragment {
    private static final String BKEY_TITLE = "title";
    private static final String BKEY_IS_INDETERMINATE = "isIndeterminate";
    private static final String BKEY_TASK_ID = "taskId";


    /**
     * The underlying task queue
     */
    private final SimpleTaskQueue mQueue;
    /**
     * Handler so we can detect UI thread
     */
    private final Handler mHandler = new Handler();
    /**
     * List of messages to be sent to the underlying activity, but not yet sent
     */
    private final ArrayList<TaskMessage> mTaskMessages = new ArrayList<>();
    /**
     * List of messages queued; only used if activity not present when showToast() is called
     */
    private ArrayList<String> mMessages = null;
    /**
     * Flag indicating dialog was cancelled
     */
    private boolean mWasCancelled = false;
    /**
     * Max value of progress (for determinate progress)
     */
    private String mMessage = null;
    /**
     * Max value of progress (for determinate progress)
     */
    private int mMax;
    /**
     * Current value of progress (for determinate progress)
     */
    private int mProgress = 0;
    /**
     * Flag indicating underlying field has changed so that progress dialog will be updated
     */
    private boolean mMessageChanged = false;
    /**
     * Flag indicating underlying field has changed so that progress dialog will be updated
     */
    private boolean mProgressChanged = false;
    /**
     * Flag indicating underlying field has changed so that progress dialog will be updated
     */
    private boolean mMaxChanged = false;
    /**
     * Flag indicating underlying field has changed so that progress dialog will be updated
     */
    private boolean mNumberFormatChanged = false;
    /**
     * Format of number part of dialog
     */
    private String mNumberFormat = null;
    /**
     * Unique ID for this task. Can be used like menu or activity IDs
     */
    private int mTaskId;
    /**
     * Flag, defaults to true, that can be set by tasks and is passed to listeners
     */
    private boolean mSuccess = true;
    /**
     * Flag indicating a Refresher has been posted but not run yet
     */
    private boolean mRefresherQueued = false;
    /**
     * Runnable object to refresh the dialog
     */
    private final Runnable mRefresher = new Runnable() {
        @Override
        public void run() {
            synchronized (mRefresher) {
                mRefresherQueued = false;
                updateProgress();
            }
        }
    };

    /**
     * Constructor
     */
    public SimpleTaskQueueProgressFragment() {
        mQueue = new SimpleTaskQueue("FragmentQueue");
		/*
	  Dismiss dialog if all tasks finished
	 */
        SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener = new SimpleTaskQueue.OnTaskFinishListener() {

            @Override
            public void onTaskFinish(@NonNull SimpleTask task, Exception e) {
                // If there are no more tasks, close this dialog
                if (!mQueue.hasActiveTasks()) {
                    queueAllTasksFinished();
                }
            }
        };
        mQueue.setTaskFinishListener(mTaskFinishListener);
    }

    /**
     * Convenience routine to show a dialog fragment and start the task
     *
     * @param context Activity of caller
     * @param message Message to display
     * @param task    Task to run
     */
    public static SimpleTaskQueueProgressFragment runTaskWithProgress(final FragmentActivity context, int message,
                                                                      FragmentTask task, boolean isIndeterminate, int taskId) {
        SimpleTaskQueueProgressFragment frag = SimpleTaskQueueProgressFragment.newInstance(message, isIndeterminate, taskId);
        frag.enqueue(task);
        frag.show(context.getSupportFragmentManager(), null);
        return frag;
    }

    private static SimpleTaskQueueProgressFragment newInstance(int title, boolean isIndeterminate, int taskId) {
        SimpleTaskQueueProgressFragment frag = new SimpleTaskQueueProgressFragment();
        Bundle args = new Bundle();
        args.putInt(BKEY_TITLE, title);
        args.putInt(BKEY_TASK_ID, taskId);
        args.putBoolean(BKEY_IS_INDETERMINATE, isIndeterminate);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Queue a TaskMessage and then try to process the queue.
     */
    private void queueMessage(TaskMessage m) {

        synchronized (mTaskMessages) {
            mTaskMessages.add(m);
        }
        deliverMessages();
    }

    /**
     * Queue a TaskFinished message
     */
    private void queueTaskFinished(FragmentTask t, Exception e) {
        queueMessage(new TaskFinishedMessage(t, e));
    }

    /**
     * Queue an AllTasksFinished message
     */
    private void queueAllTasksFinished() {
        queueMessage(new AllTasksFinishedMessage());
    }

    /**
     * If we have an Activity, deliver the current queue.
     */
    private void deliverMessages() {
        Activity a = getActivity();
        if (a != null) {
            ArrayList<TaskMessage> toDeliver = new ArrayList<>();
            int count;
            do {
                synchronized (mTaskMessages) {
                    toDeliver.addAll(mTaskMessages);
                    mTaskMessages.clear();
                }
                count = toDeliver.size();
                for (TaskMessage m : toDeliver) {
                    try {
                        m.deliver(a);
                    } catch (Exception e) {
                        Logger.logError(e);
                    }
                }
                toDeliver.clear();
            } while (count > 0);
        }
    }

    /**
     * Utility routine to display a Toast message or queue it as appropriate.
     */
    public void showToast(final int id) {
        if (id != 0) {
            // We don't use getString() because we have no guarantee this
            // object is associated with an activity when this is called, and
            // for whatever reason the implementation requires it.
            showToast(BookCatalogueApp.getResourceString(id));
        }
    }

    /**
     * Utility routine to display a Toast message or queue it as appropriate.
     */
    private void showToast(final String message) {
        // Can only display in main thread.
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            synchronized (this) {
                if (this.getActivity() != null) {
                    Toast.makeText(this.getActivity(), message, Toast.LENGTH_LONG).show();
                } else {
                    // Assume the toast message was sent before the fragment was displayed; this
                    // list will be read in onAttach
                    if (mMessages == null)
                        mMessages = new ArrayList<>();
                    mMessages.add(message);
                }
            }
        } else {
            // Post() it to main thread.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    showToast(message);
                }
            });
        }
    }

    /**
     * Post a runnable to the UI thread
     */
    public void post(Runnable r) {
        mHandler.post(r);
    }

    /**
     * Enqueue a task for this fragment
     */
    private void enqueue(FragmentTask task) {
        mQueue.enqueue(new FragmentTaskWrapper(task));
    }

    /**
     * Ensure activity supports event
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        synchronized (this) {
            if (mMessages != null) {
                for (String message : mMessages) {
                    if (message != null && !message.isEmpty())
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
                mMessages.clear();
            }
        }
        //if (! (a instanceof OnSyncTaskCompleteListener))
        //	throw new RuntimeException("Activity " + a.getClass().getSimpleName() + " must implement OnSyncTaskCompleteListener");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // VERY IMPORTANT. We do not want this destroyed!
        setRetainInstance(true);
        mTaskId = getArguments().getInt(BKEY_TASK_ID);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Deliver any outstanding messages
        deliverMessages();

        // If no tasks left, exit
        if (!mQueue.hasActiveTasks()) {
            if (DEBUG_SWITCHES.DEBUG_SQPFragment && BuildConfig.DEBUG) {
                System.out.println("STQPF: Tasks finished while activity absent, closing");
            }
            dismiss();
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        int msg = getArguments().getInt(BKEY_TITLE);
        if (msg != 0) {
            dialog.setMessage(getActivity().getString(msg));
        }
        final boolean isIndet = getArguments().getBoolean(BKEY_IS_INDETERMINATE);
        dialog.setIndeterminate(isIndet);
        if (isIndet) {
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        } else {
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        }

        // We can't use "this.requestUpdateProgress()" because getDialog() will still return null
        if (!isIndet) {
            dialog.setMax(mMax);
            dialog.setProgress(mProgress);
            if (mMessage != null) {
                dialog.setMessage(mMessage);
            }
            setDialogNumberFormat(dialog);
        }

        return dialog;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        mWasCancelled = true;
        mQueue.finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // If task finished, dismiss.
        if (!mQueue.hasActiveTasks())
            dismiss();
    }

    /**
     * Accessor
     */
    public boolean isCancelled() {
        return mWasCancelled;
    }

    /**
     * Accessor
     */
    public boolean getSuccess() {
        return mSuccess;
    }

    /**
     * Accessor
     */
    public void setSuccess(boolean success) {
        mSuccess = success;
    }

    /**
     * Refresh the dialog, or post a refresh to the UI thread
     */
    private void requestUpdateProgress() {
        if (DEBUG_SWITCHES.DEBUG_SQPFragment && BuildConfig.DEBUG) {
            System.out.println("STQPF: " + mMessage + " (" + mProgress + "/" + mMax + ")");
        }
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
            updateProgress();
        } else {
            synchronized (mRefresher) {
                if (!mRefresherQueued) {
                    mHandler.post(mRefresher);
                    mRefresherQueued = true;
                }
            }
        }
    }

    /**
     * Convenience method to step the progress by 1.
     */
    public void step(String message) {
        step(message, 1);
    }

    /**
     * Convenience method to step the progress by the passed delta
     */
    public void step(String message, int delta) {
        synchronized (this) {
            if (message != null) {
                mMessage = message;
                mMessageChanged = true;
            }
            mProgress += delta;
            mProgressChanged = true;
        }
        requestUpdateProgress();
    }

    /**
     * Direct update of message and progress value
     */
    public void onProgress(String message, int progress) {

        synchronized (this) {
            if (message != null) {
                mMessage = message;
                mMessageChanged = true;
            }
            mProgress = progress;
            mProgressChanged = true;
        }

        requestUpdateProgress();
    }

    /**
     * Method, run in the UI thread, that updates the various dialog fields.
     */
    private void updateProgress() {
        ProgressDialog d = (ProgressDialog) getDialog();
        if (d != null) {
            synchronized (this) {
                if (mMaxChanged) {
                    d.setMax(mMax);
                    mMaxChanged = false;
                }
                if (mNumberFormatChanged) {
                    // Called in a separate function so we can set API attributes
                    setDialogNumberFormat(d);
                    mNumberFormatChanged = false;
                }
                if (mMessageChanged) {
                    d.setMessage(mMessage);
                    mMessageChanged = false;
                }

                if (mProgressChanged) {
                    d.setProgress(mProgress);
                    mProgressChanged = false;
                }

            }
        }
    }

    private void setDialogNumberFormat(ProgressDialog d) {
        // previously seen null issue fixed in .. at least API 21 probably earlier
        d.setProgressNumberFormat(mNumberFormat);
    }

    /**
     * Set the progress max value
     */
    public void setMax(int max) {
        mMax = max;
        mMaxChanged = true;
        requestUpdateProgress();
    }

    /**
     * Set the progress number format, if the API will support it
     */
    public void setNumberFormat(String format) {
        synchronized (this) {
            mNumberFormat = format;
            mNumberFormatChanged = true;
        }
        requestUpdateProgress();
    }

    /**
     * Work-around for bug in compatibility library:
     *
     * http://code.google.com/p/android/issues/detail?id=17423
     */
    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    /**
     * Each message has a single method to deliver it and will only be called
     * when the underlying Activity is actually present.
     */
    private interface TaskMessage {
        void deliver(@NonNull final Activity a);
    }

    /**
     * Listener for OnTaskFinished messages
     */
    public interface OnTaskFinishedListener {
        void onTaskFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                            final int taskId, final boolean success, final boolean cancelled,
                            @NonNull final FragmentTask task);
    }

    /**
     * Listener for OnAllTasksFinished messages
     */
    public interface OnAllTasksFinishedListener {
        void onAllTasksFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                                final int taskId, final boolean success, final boolean cancelled);
    }

    /**
     * Interface for 'FragmentTask' objects. Closely based on SimpleTask, but takes the
     * fragment as a parameter to all calls.
     *
     * @author pjw
     */
    public interface FragmentTask {
        /**
         * Run the task in it's own thread
         */
        void run(@NonNull final SimpleTaskQueueProgressFragment fragment, @NonNull final SimpleTaskContext taskContext);

        /**
         * Called in UI thread after task complete  TODO
         */
        void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment, @Nullable final Exception exception);
    }

    /**
     * Trivial implementation of FragmentTask that never calls onFinish(). The setState()/getState()
     * calls can be used to store state info by a caller, eg. if they override requiresOnFinish() etc.
     *
     * @author pjw
     */
    public abstract static class FragmentTaskAbstract implements FragmentTask {
        private int mState = 0;

        @Override
        public void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment, @Nullable final Exception exception) {
            if (exception != null) {
                Logger.logError(exception);
                Toast.makeText(fragment.getActivity(), R.string.unexpected_error, Toast.LENGTH_LONG).show();
            }
        }

        public int getState() {
            return mState;
        }

        public void setState(final int state) {
            mState = state;
        }
    }

    /**
     * TaskFinished message.
     *
     * We only deliver onFinish() to the FragmentTask when the activity is present.
     */
    private class TaskFinishedMessage implements TaskMessage {
        final FragmentTask mTask;
        final Exception mException;

        TaskFinishedMessage(@NonNull final FragmentTask task, @Nullable final Exception e) {
            mTask = task;
            mException = e;
        }

        @Override
        public void deliver(@NonNull final Activity a) {
            try {
                mTask.onFinish(SimpleTaskQueueProgressFragment.this, mException);
            } catch (Exception e) {
                Logger.logError(e);
            }
            try {
                if (a instanceof OnTaskFinishedListener) {
                    ((OnTaskFinishedListener) a).onTaskFinished(SimpleTaskQueueProgressFragment.this, mTaskId, mSuccess, mWasCancelled, mTask);
                }
            } catch (Exception e) {
                Logger.logError(e);
            }

        }

    }

    /**
     * AllTasksFinished message.
     */
    private class AllTasksFinishedMessage implements TaskMessage {

        AllTasksFinishedMessage() {
        }

        @Override
        public void deliver(@NonNull final Activity a) {
            if (a instanceof OnAllTasksFinishedListener) {
                ((OnAllTasksFinishedListener) a).onAllTasksFinished(SimpleTaskQueueProgressFragment.this, mTaskId, mSuccess, mWasCancelled);
            }
            dismiss();
        }

    }

    /**
     * A SimpleTask wrapper for a FragmentTask.
     *
     * @author pjw
     */
    private class FragmentTaskWrapper implements SimpleTask {
        private final FragmentTask mInnerTask;

        FragmentTaskWrapper(@NonNull final FragmentTask task) {
            mInnerTask = task;
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            try {
                mInnerTask.run(SimpleTaskQueueProgressFragment.this, taskContext);
            } catch (Exception e) {
                mSuccess = false;
                throw e;
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            SimpleTaskQueueProgressFragment.this.queueTaskFinished(mInnerTask, e);
        }

    }
}
