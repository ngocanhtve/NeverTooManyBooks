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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

/**
 * State engine for the status of an entity.
 */
public class EntityStage {

    @NonNull
    private Stage stage = Stage.Clean;

    private boolean locked;

    void lock() {
        if (!locked) {
            locked = true;
        } else {
            throw new IllegalStateException("Already locked");
        }
    }

    void unlock() {
        if (locked) {
            locked = false;
        } else {
            throw new IllegalStateException("Already unlocked");
        }
    }

    @NonNull
    public Stage getStage() {
        return stage;
    }

    public void setStage(@NonNull final Stage stage) {
        if (locked) {
            return;
        }
        this.stage = stage;
    }

    public enum Stage {
        /** The entity <strong>is not</strong> modified. */
        Clean,
        /** The entity <strong>can</strong> be modified, but that has not been done yet. */
        WriteAble,
        /** The entity <strong>has</strong> been modified. */
        Dirty,
    }
}
