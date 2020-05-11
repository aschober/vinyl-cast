package tech.schober.vinylcast.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import tech.schober.vinylcast.R;

public class InfoButtonListPreferencePref extends CheckOnPrefClickListPreference {
    private static final String TAG = "InfoButtonListPreference";

    private View mView = null;
    private boolean mShowInfoButton = true;
    private ImageButton.OnClickListener imageButtonClickListener;

    public InfoButtonListPreferencePref(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWidgetLayoutResource(R.layout.pref_widget_info_button);
    }

    public InfoButtonListPreferencePref(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("RestrictedApi")
    public InfoButtonListPreferencePref(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.dialogPreferenceStyle,
                android.R.attr.dialogPreferenceStyle));
    }

    public InfoButtonListPreferencePref(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mView = holder.itemView;
        ImageButton infoButton = mView.findViewById(R.id.pref_image_button);
        if (mShowInfoButton) {
            infoButton.setVisibility(View.VISIBLE);
        } else {
            infoButton.setVisibility(View.GONE);
        }
        infoButton.setOnClickListener(imageButtonClickListener);
    }

    public boolean getShowInfoButton() {
        return mShowInfoButton;
    }

    public void setShowInfoButton(boolean showInfoButton) {
        this.mShowInfoButton = showInfoButton;
    }

    public ImageButton.OnClickListener getImageButtonClickListener() {
        return imageButtonClickListener;
    }

    public void setImageButtonClickListener(ImageButton.OnClickListener imageButtonClickListener) {
        this.imageButtonClickListener = imageButtonClickListener;
    }

    public View getView() {
        return mView;
    }

    /**
     * Helper method to trigger a refresh of the provided PrefernceFragment's listview
     * holding audioEncodingPref to make sure the info button gets redrawn with latest state
     * @param parentPrefFragment the PreferenceFragment that is holding this instance
     *                           of InfoButtonListPreference
     */
    public void notifyPreferenceListItemChanged(PreferenceFragmentCompat parentPrefFragment) {
        RecyclerView prefListView = parentPrefFragment.getListView();
        if (prefListView != null) {
            int position = prefListView.getChildAdapterPosition(getView());
            if (position != RecyclerView.NO_POSITION) {
                prefListView.getAdapter().notifyItemChanged(position);
            }
        }
    }
}
