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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedStorageException;

/**
 * Common base for MutableLiveData / TaskListener driven tasks.
 *
 * @param <Result> the type of the result of the background computation.
 */
abstract class TaskBase<Result>
        implements ProgressListener {

    /** Identifies the task. Passed back in all messages. */
    private final int taskId;
    /** Identifies the task. */
    @NonNull
    private final String taskName;

    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** State of this task. */
    @NonNull
    private Status status = Status.Created;
    /** Use {@link #setExecutor(Executor)} to override. */
    @NonNull
    private Executor executor = ASyncExecutor.SERIAL;

    /** If progress is not indeterminate, the current position. */
    private int progressCurrentPos;
    /** If progress is not indeterminate, the maximum position. */
    private int progressMaxPos;
    /** Flag. */
    @Nullable
    private Boolean indeterminate;

    /**
     * Constructor.
     *
     * @param taskId   a unique task identifier, returned with each message
     * @param taskName a (preferably unique) name used for identification of this task
     */
    TaskBase(final int taskId,
             @NonNull final String taskName) {
        this.taskId = taskId;
        this.taskName = taskName;
    }

    @UiThread
    public void setExecutor(@NonNull final Executor executor) {
        this.executor = executor;
    }

    /**
     * Access for other classes.
     *
     * @return task ID
     */
    @AnyThread
    public int getTaskId() {
        return taskId;
    }

    @NonNull
    String getTaskName() {
        return taskName;
    }

    /**
     * Execute the task.
     * Protected access to force implementations to have a "good method name" to start the task.
     *
     * @throws IllegalStateException if already/still running.
     */
    @UiThread
    protected void execute() {
        synchronized (this) {
            if (status != Status.Created && status != Status.Finished) {
                throw new IllegalStateException("task already running");
            }

            status = Status.Pending;
        }
        executor.execute(() -> {
            status = Status.Running;
            Thread.currentThread().setName(taskName);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
            try {
                final Result result = doWork(context);
                if (isCancelled()) {
                    setTaskCancelled(result);
                } else {
                    setTaskFinished(result);
                }
            } catch (@NonNull final CancellationException e) {
                setTaskCancelled(null);

            } catch (@NonNull final UncheckedStorageException e) {
                //noinspection ConstantConditions
                setTaskFailure(e.getCause());

            } catch (@NonNull final UncheckedIOException e) {
                //noinspection ConstantConditions
                setTaskFailure(e.getCause());

            } catch (@NonNull final Exception e) {
                setTaskFailure(e);
            }

            status = Status.Finished;
        });
    }

    /**
     * The actual 'work' method.
     *
     * @param context The localised Application context
     *
     * @return task result
     *
     * @throws CancellationException if the user cancelled us
     * @throws Exception             depending on implementation
     */
    @Nullable
    @WorkerThread
    protected abstract Result doWork(@NonNull Context context)
            throws CancellationException, Exception;

    /**
     * Called when the task successfully finishes.
     */
    protected abstract void setTaskFinished(@Nullable Result result);

    /**
     * Called when the task was cancelled.
     */
    protected abstract void setTaskCancelled(@Nullable Result result);

    /**
     * Called when the task fails with an Exception.
     */
    protected abstract void setTaskFailure(@NonNull Exception e);

    //FIXME: Potentially unsafe 'if != null then cancel'
    @Override
    @CallSuper
    @AnyThread
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Pull-request to check if this task is / should be cancelled.
     *
     * @return cancel-status
     */
    @Override
    @AnyThread
    public boolean isCancelled() {
        return cancelled.get();
    }

    @AnyThread
    public boolean isRunning() {
        return status == Status.Running;
    }

    /**
     * Send a progress update.
     * Convenience method which builds the {@link TaskProgress} based
     * on the current progress counters and the passed data.
     * <p>
     * Can be called from inside {@link #doWork}.
     *
     * @param delta the relative step in the overall progress count.
     * @param text  (optional) text message
     */
    @WorkerThread
    @Override
    public void publishProgress(final int delta,
                                @Nullable final String text) {
        progressCurrentPos += delta;
        publishProgress(new TaskProgress(taskId, text,
                                         progressCurrentPos, progressMaxPos,
                                         indeterminate));
    }

    /**
     * Only takes effect when the next {@link TaskProgress} is send to the client.
     *
     * @param indeterminate true/false to enable/disable the indeterminate mode
     *                      or {@code null} to tell the receiver to revert back to its initial mode.
     */
    @AnyThread
    @Override
    public void setIndeterminate(@Nullable final Boolean indeterminate) {
        this.indeterminate = indeterminate;
    }

    @AnyThread
    @Override
    public int getMaxPos() {
        return progressMaxPos;
    }

    /**
     * Only takes effect when the next {@link TaskProgress} is send to the client.
     *
     * @param maxPosition value
     */
    @AnyThread
    @Override
    public void setMaxPos(final int maxPosition) {
        progressMaxPos = maxPosition;
    }

    /**
     * Indicates the current status of the task.
     * <p>
     * Created -> Pending -> Running -> Finished.
     * <p>
     * Finished -> Pending -> Running -> Finished.
     */
    public enum Status {
        /** initial status before the task has been queued. */
        Created,
        /** The task has been submitted, and is scheduled to start. */
        Pending,
        /** The task is actively doing work. */
        Running,
        /**
         * The task is finished; it could have done so with success or failure,
         * or it could have been cancelled. Regardless, it's 'done'
         */
        Finished
    }
}
