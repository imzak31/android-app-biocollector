package com.orsys.agorabiocollector;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;


/*
 Created by healer on 6/13/2019
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new LoginFragment())
                .commit();

    }

    public void buttonLoginClick(View view) {
        Intent intent = new Intent(this, HomeActivity.class);
        //startActivityForResult(intent, SCAN_FINGER);
        startActivity(intent);
    }

    public static class LoginFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}
