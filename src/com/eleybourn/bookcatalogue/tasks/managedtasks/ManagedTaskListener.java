package com.eleybourn.bookcatalogue.tasks.managedtasks;

import androidx.annotation.NonNull;

/**
 * Allows other objects to know when a task completed.
 */
public interface ManagedTaskListener {

    void onTaskFinished(@NonNull final ManagedTask task);
}
