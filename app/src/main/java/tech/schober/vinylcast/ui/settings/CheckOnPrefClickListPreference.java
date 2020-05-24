package tech.schober.vinylcast.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.ListPreference;

import tech.schober.vinylcast.R;

/**
 * Extension of ListPreference so we can check onPreferenceClick method before
 * performing the onClick method. This allows us to intercept the click in SettingsFragment
 * and do a different action instead of opening list dialog (e.g. show a different dialog saying
 * preference can't be modified when recording).
 *
 * Not sure why Android/AndroidX's Preference calls onClick (causing an action to occur)
 * before checking onPreferenceClick...kinda defeats purpose of onPreferenceClick check.
 *
 * https://stackoverflow.com/a/32357583
 */
public class CheckOnPrefClickListPreference extends ListPreference {

    public CheckOnPrefClickListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CheckOnPrefClickListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("RestrictedApi")
    public CheckOnPrefClickListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.dialogPreferenceStyle,
                android.R.attr.dialogPreferenceStyle));
    }

    public CheckOnPrefClickListPreference(Context context) {
        this(context, null);
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
