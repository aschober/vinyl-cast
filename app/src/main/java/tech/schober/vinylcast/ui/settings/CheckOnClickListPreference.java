package tech.schober.vinylcast.ui.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * Extension of ListPreference so we can check onPreferenceClick method before
 * performing the onClick method. This allows us to intercept the click in SettingsFragment
 * and do a different action instead of opening list dialog (e.g. show a different dialog saying
 * preference can't be modified when recording).
 *
 * Not sure why Android/AndroidX's ListPreference calls onClick (causing an action to occur)
 * before checking onPreferenceClick...kinda defeats purpose of onPreferenceClick check.
 *
 * https://stackoverflow.com/a/32357583
 */
public class CheckOnClickListPreference extends ListPreference {

    public CheckOnClickListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CheckOnClickListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckOnClickListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckOnClickListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        // first check onPreferenceClick before continuing
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            return;
        }

        super.onClick();
    }
}
