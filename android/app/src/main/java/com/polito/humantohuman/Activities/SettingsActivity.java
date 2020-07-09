package com.polito.humantohuman.Activities;

import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;
import com.polito.humantohuman.AppLogic;
import com.polito.humantohuman.R;

public class SettingsActivity extends AppCompatActivity {

  Button exitButton;
  Button setServerButton;
  Button privacyPolicyButton;
  TextView setServerEditText;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);
    exitButton = findViewById(R.id.settingsExitButton);
    exitButton.setOnClickListener((view) -> this.finish());
    setServerEditText = findViewById(R.id.settingsSetServerEditText);
    setServerButton = findViewById(R.id.settingsSetServerButton);
    privacyPolicyButton = findViewById(R.id.settingsPrivacyPolicyButton);

    switch (AppLogic.getAppState()) {
      case AppLogic.APPSTATE_NO_EXPERIMENT:
        setServerButton.setEnabled(true);
        privacyPolicyButton.setEnabled(false);
        break;
      case AppLogic.APPSTATE_LOGGING_IN:
        setServerButton.setEnabled(false);
        privacyPolicyButton.setEnabled(false);
        break;
      default:
        setServerButton.setEnabled(false);
        privacyPolicyButton.setEnabled(true);
    }
//    setServerEditText.setText("http://192.168.1.151:8080/experiment/password"); // TODO remove this

    privacyPolicyButton.setOnClickListener((view) -> {
      Intent intent = new Intent(this, PolicyActivity.class);
      startActivity(intent);
    });

    setServerButton.setOnClickListener((view) -> {
      setServerButton.setEnabled(false);
      AppLogic.setServerCredentials(
          setServerEditText.getText().toString(), (error) -> {
            if (error != null) {
              setServerButton.setEnabled(true);
            } else {
              Intent intent = new Intent(this, PolicyActivity.class);
              startActivity(intent);
              finish();
            }
          });
    });
  }

  @Override
  protected void onResume() {
    super.onResume();

    switch (AppLogic.getAppState()) {
      case AppLogic.APPSTATE_NO_EXPERIMENT:
        setServerButton.setEnabled(true);
        privacyPolicyButton.setEnabled(false);
        break;
      case AppLogic.APPSTATE_LOGGING_IN:
        setServerButton.setEnabled(false);
        privacyPolicyButton.setEnabled(false);
        break;
      default:
        setServerButton.setEnabled(false);
        privacyPolicyButton.setEnabled(true);
    }
  }
}
