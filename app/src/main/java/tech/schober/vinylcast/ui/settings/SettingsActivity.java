package tech.schober.vinylcast.ui.settings;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;

import tech.schober.vinylcast.R;
import tech.schober.vinylcast.VinylCastService;
import tech.schober.vinylcast.ui.VinylCastActivity;

public class SettingsActivity extends VinylCastActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
        super.onServiceConnected(className, service);
        ((SettingsFragment) getSupportFragmentManager().getFragments().get(0)).onServiceConnected(className, service);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        super.onServiceDisconnected(className);
        ((SettingsFragment) getSupportFragmentManager().getFragments().get(0)).onServiceDisconnected(className);
    }

    VinylCastService.VinylCastBinder getVinylCastBinder() {
        return this.binder;
    }
}
