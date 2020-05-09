package tech.schober.vinylcast.ui.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.CheckBoxPreference;

/**
 * Extension of CheckBoxPreference so we can check onPreferenceClick method before
 * performing the onClick method. This allows us to intercept the click in SettingsFragment
 * and do a different action instead of toggling checkbox (e.g. show a different dialog saying
 * preference can't be modified when recording).
 *
 * Not sure why Android/AndroidX's ListPreference calls onClick (causing an action to occur)
 * before checking onPreferenceClick...kinda defeats purpose of onPreferenceClick check.
 *
 * https://stackoverflow.com/a/32357583
 */
public class CheckOnClickCheckboxPreference extends CheckBoxPreference {
    public CheckOnClickCheckboxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CheckOnClickCheckboxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CheckOnClickCheckboxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckOnClickCheckboxPreference(Context context) {
        super(context);
    }

    @Override
    public void performClick() {

        if (!isEnabled() || !isSelectable()) {
            return;
        }

        // first check onPreferenceClick before continuing to onClick
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener().onPreferenceClick(this)) {
            return;
        }

        onClick();
    }
}
