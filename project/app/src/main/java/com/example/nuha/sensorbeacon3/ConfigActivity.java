package com.example.nuha.sensorbeacon3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import android.os.Bundle;
import android.util.Log;

public class ConfigActivity extends AppCompatActivity {

    String TAG = "ConfigActivity";

    public static class MySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.app_preference, rootKey);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        Log.d(TAG, "onCreate: ConfigActivity");
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setting_container, new MySettingsFragment())
                .commit();

    }
}
