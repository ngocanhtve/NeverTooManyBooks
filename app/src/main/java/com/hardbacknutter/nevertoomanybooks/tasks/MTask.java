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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.MutableLiveData;

import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * The base for a task which uses {@link MutableLiveData} for the results.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class MTask<Result>
        extends TaskBase<Result> {

    private final MutableLiveData<FinishedMessage<Result>> mFinishedObservable =
            new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Result>> mCancelObservable =
            new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mFailureObservable =
            new MutableLiveData<>();
    private final MutableLiveData<ProgressMessage> mProgressObservable =
            new MutableLiveData<>();

    /**
     * Constructor.
     *
     * @param taskId   a unique task identifier, returned with each message
     * @param taskName a (preferably unique) name used for identification of this task
     */
    @UiThread
    protected MTask(final int taskId,
                    @NonNull final String taskName) {
        super(taskId, taskName);
    }

    /**
     * Called when the task successfully finishes.
     *
     * @return the {@link Result} which can be considered to be complete and correct.
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onFinished() {
        return mFinishedObservable;
    }

    protected void onFinished(@NonNull final FinishedMessage<Result> message) {
        mFinishedObservable.postValue(message);
    }

    /**
     * Called when the task was cancelled.
     *
     * @return the {@link Result}. It will depend on the implementation how
     * complete/correct (if at all) this result is.
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Result>> onCancelled() {
        return mCancelObservable;
    }

    protected void onCancelled(@NonNull final FinishedMessage<Result> message) {
        mCancelObservable.postValue(message);
    }

    /**
     * Called when the task fails with an Exception.
     *
     * @return the result is the Exception
     */
    @NonNull
    public MutableLiveData<FinishedMessage<Exception>> onFailure() {
        return mFailureObservable;
    }

    protected void onFailure(@NonNull final Exception e) {
        mFailureObservable.postValue(new FinishedMessage<>(getTaskId(), e));
    }

    /**
     * Forwards progress messages for the client to display.
     *
     * @return a {@link ProgressMessage} with the progress counter, a text message, ...
     */
    @NonNull
    public MutableLiveData<ProgressMessage> onProgressUpdate() {
        return mProgressObservable;
    }

    @Override
    public void publishProgress(@NonNull final ProgressMessage message) {
        mProgressObservable.postValue(message);
    }
}
