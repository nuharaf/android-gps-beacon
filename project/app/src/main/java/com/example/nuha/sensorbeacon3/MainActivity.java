package com.example.nuha.sensorbeacon3;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.os.IBinder;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import okhttp3.*;

public class MainActivity extends AppCompatActivity {
    String TAG = "MainActivity";
    BeaconService mService;
    ProgressBar mProgress;
    boolean bound;
    LocalBroadcastManager bus;
    int secretCounter = 0;
    SharedPreferences pref;
    SharedPreferences session;
    EditText eventCode;
    EditText assetName;
    Button createEvent;
    Button useEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate: MainActivity");
        setContentView(R.layout.activity_create_event);
        if (BeaconService.SERVICE_STATUS == BeaconService.SERVICE_RUNNING) {
            Intent i = new Intent(getApplicationContext(), NotificationActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
        pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.app_preference, false);
        session = getSharedPreferences("session", 0);
        eventCode = findViewById(R.id.eventCode);
        assetName = findViewById(R.id.assetName);
        eventCode.setText(session.getString("event", ""));
        createEvent = findViewById(R.id.createEvent);
        useEvent = findViewById(R.id.useEvent);

        ConstraintLayout layout = findViewById(R.id.contraintlayout);
        layout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                secretCounter++;
                if (secretCounter == 10) {
                    Intent i = new Intent(getApplicationContext(), ConfigActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }

            }
        });
        bound = false;
        mProgress = findViewById(R.id.progressBar);
        System.out.println("ABCDEFGHIJKL");
        bus = LocalBroadcastManager.getInstance(getApplicationContext());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("server-request-error");
        intentFilter.addAction("mqtt-connected");
        intentFilter.addAction("mqtt-disconnected");
        bus.registerReceiver(receiver, intentFilter);
        if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: Permission approved");
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: Permission rejected");
                    new AlertDialog.Builder(MainActivity.this, R.style.DialogTheme).setTitle("Alert")
                            .setMessage("Aplikasi tidak bisa berjalan tanpa ijin GPS ?")

                            // Specifying a listener allows you to take an action before dismissing the dialog.
                            // The dialog is automatically dismissed when a dialog button is clicked.
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
                return;

            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BeaconService.LocalBinder binder = (BeaconService.LocalBinder) service;
            mService = binder.getService();
            bound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            Log.d(TAG, "Service disconnected");
            Toast.makeText(getApplicationContext(), "Service Disconnected", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            bound = false;
            Log.d(TAG, "Service unbound");
            Toast.makeText(getApplicationContext(), "Service unbound", Toast.LENGTH_LONG).show();

        }
    };


    @Override
    protected void onPause() {
        super.onPause();
//        if (bound == true) {
//            unbindService(conn);
//            bound = false;
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        Intent i = new Intent(getApplicationContext(), BeaconService.class);
//        bindService(i, conn, 0);
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onREceive" + action);
            if (action == "server-request-error") {
                Toast.makeText(getApplicationContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public void onCreateEventAPI19(View v) {
        Log.i(TAG, "OnCreateEvent");
        mProgress.setVisibility(View.VISIBLE);
        createEvent.setEnabled(false);
        useEvent.setEnabled(false);
        try {
            new Thread(() -> {
                try {
                    String api_server = pref.getString("api_server", "http://10.0.2.2:3000");
                    URL url = new URL(api_server + "/newEventCode");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    if(urlConnection.getResponseCode() == 200){
                        Log.d(TAG, "onResponse: 200");
                        MainActivity.this.runOnUiThread(() -> {
                            mProgress.setVisibility(View.INVISIBLE);
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        String eventcode = readStream(in);
                        Log.d(TAG, "onResponse: created event code : " + eventcode);
                        String id = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                                Settings.Secure.ANDROID_ID);
                        String name = assetName.getText().toString();
                        name = name.contentEquals("") ? id : name;
                        session.edit().putString("event", eventcode).putString("name", name).commit();
                        Intent i = new Intent(getApplicationContext(), BeaconService.class);
                        i.putExtra("clientId", eventcode + "-" + id);
                        i.putExtra("username", name);
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(i);
                        } else {
                            startService(i);
                        }
                        finish();
                    }
                    else {
                        MainActivity.this.runOnUiThread(() -> {
                            mProgress.setVisibility(View.INVISIBLE);
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        bus.sendBroadcast(new Intent("server-request-error"));
                    }
                }
                catch (MalformedURLException e){
                    System.out.println(e);
                }
                catch (IOException e){
                    Log.d(TAG, "onFailure: ");
                    MainActivity.this.runOnUiThread(() -> {
                        mProgress.setVisibility(View.INVISIBLE);
                        createEvent.setEnabled(true);
                        useEvent.setEnabled(true);
                        bus.sendBroadcast(new Intent("server-request-error"));
                    });
                }
            }).start();


        }
        catch (Exception e){
            System.out.println(e);
        }

    }

    public void onCreateEvent(View v) {
        Log.i(TAG, "OnCreateEvent");
        mProgress.setVisibility(View.VISIBLE);
        createEvent.setEnabled(false);
        useEvent.setEnabled(false);
        OkHttpClient client = new OkHttpClient();
        try {
            String api_server = pref.getString("api_server", "http://10.0.2.2:3000");
            client.newCall(new Request.Builder()
                    .url(api_server + "/newEventCode")
                    .build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d(TAG, "onFailure: ");
                    MainActivity.this.runOnUiThread(() -> {
                        stopProgressBar();
                        createEvent.setEnabled(true);
                        useEvent.setEnabled(true);
                        bus.sendBroadcast(new Intent("server-request-error"));
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 200) {
                        Log.d(TAG, "onResponse: 200");
                        MainActivity.this.runOnUiThread(() -> {
                            stopProgressBar();
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        String eventcode = response.body().string();
                        Log.d(TAG, "onResponse: created event code : " + eventcode);
                        String id = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                                Settings.Secure.ANDROID_ID);
                        String name = assetName.getText().toString();
                        name = name.contentEquals("") ? id : name;
                        session.edit().putString("event", eventcode).putString("name", name).commit();
                        Intent i = new Intent(getApplicationContext(), BeaconService.class);
                        i.putExtra("clientId", eventcode + "-" + id);
                        i.putExtra("username", name);
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(i);
                        } else {
                            startService(i);
                        }

                        finish();
                    } else {
                        MainActivity.this.runOnUiThread(() -> {
                            stopProgressBar();
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        bus.sendBroadcast(new Intent("server-request-error"));
                    }
                }

                void stopProgressBar() {
                    mProgress.setVisibility(View.INVISIBLE);
                }
            });
        } catch (Exception e) {
            System.out.println(e);
        }
    }


    public void onUseEvent(View v) {
        Log.i(TAG, "OnUseEvent");
        String event = eventCode.getText().toString();
        if (event.contentEquals("")) {
            eventCode.requestFocus();
            return;
        }
        mProgress.setVisibility(View.VISIBLE);
        createEvent.setEnabled(false);
        useEvent.setEnabled(false);
        OkHttpClient client = new OkHttpClient();
        try {
            String api_server = pref.getString("api_server", "http://10.0.2.2:3000");
            client.newCall(new Request.Builder()
                    .url(api_server + "/checkEventCode/" + event)
                    .build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d(TAG, "onFailure: ");
                    MainActivity.this.runOnUiThread(() -> {
                        stopProgressBar();
                        createEvent.setEnabled(true);
                        useEvent.setEnabled(true);
                        bus.sendBroadcast(new Intent("server-request-error"));
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 200) {
                        Log.d(TAG, "onResponse: 200");
                        MainActivity.this.runOnUiThread(() -> {
                            stopProgressBar();
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        if (response.body().string().contentEquals("1")) {
                            String id = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                                    Settings.Secure.ANDROID_ID);
                            String name = assetName.getText().toString();
                            name = name.contentEquals("") ? id : name;
                            session.edit().putString("event", event).putString("name", name).commit();
                            Intent i = new Intent(getApplicationContext(), BeaconService.class);
                            Bundle param = new Bundle();
                            i.putExtra("clientId", event + "-" + id);
                            i.putExtra("username", name);
                            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(i);
                            } else {
                                startService(i);
                            }
                            finish();
                        } else {
                            MainActivity.this.runOnUiThread(() -> {
                                stopProgressBar();
                                createEvent.setEnabled(true);
                                useEvent.setEnabled(true);
                            });
                            bus.sendBroadcast(new Intent("no-eventcode"));
                        }
                    } else {
                        MainActivity.this.runOnUiThread(() -> {
                            stopProgressBar();
                            createEvent.setEnabled(true);
                            useEvent.setEnabled(true);
                        });
                        bus.sendBroadcast(new Intent("server-request-error"));
                    }

                }

                void stopProgressBar() {
                    mProgress.setVisibility(View.INVISIBLE);
                }
            });
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}


