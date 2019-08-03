package com.hardbacknutter.nevertomanybooks.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;

/**
 * Hosting activity for Preference editing.
 */
public class SettingsActivity
        extends BaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.lbl_settings);

        String tag;

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            tag = extras.getString(UniqueId.BKEY_FRAGMENT_TAG, GlobalSettingsFragment.TAG);
        } else {
            tag = GlobalSettingsFragment.TAG;
        }

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag = createFragment(tag);
            frag.setArguments(getIntent().getExtras());
            fm.beginTransaction()
              .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              // add! not replace!
              .add(R.id.main_fragment, frag, tag)
              .commit();
        }
    }

    /**
     * create a new fragment instance from the tag.
     *
     * @param tag name of fragment to instantiate
     *
     * @return new instance
     */
    private Fragment createFragment(@NonNull final String tag) {
        if (GlobalSettingsFragment.TAG.equals(tag)) {
            return new GlobalSettingsFragment();
        } else if (StyleSettingsFragment.TAG.equals(tag)) {
            return new StyleSettingsFragment();
        } else {
            throw new IllegalArgumentException("tag=" + tag);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * If any of the child preference fragments have an xml configuration with nested
     * PreferenceScreen elements, then a click on those will trigger this method.
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    public boolean onPreferenceStartScreen(@NonNull final PreferenceFragmentCompat caller,
                                           @NonNull final PreferenceScreen pref) {

        // start a NEW copy of the same fragment
        //noinspection ConstantConditions
        Fragment frag = createFragment(caller.getTag());

        // and set it to start with the new root key (screen)
        Bundle callerArgs = caller.getArguments();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        if (callerArgs != null) {
            args.putAll(callerArgs);
        }
        frag.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(pref.getKey())
                .replace(R.id.main_fragment, frag, pref.getKey())
                .commit();

        return true;
    }

}
