package com.example.nuha.sensorbeacon3;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class NotificationActivity extends AppCompatActivity {
    BeaconService mService;
    String TAG = "NotificationActivity";

    TextView networkStatus;
    TextView location;
    TextView mqttStatus;
    TextView eventCode;
    TextView clientId;
    TextView username;
    Button pauseStart;


    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BeaconService.LocalBinder binder = (BeaconService.LocalBinder) service;
            mService = binder.getService();
            Location lastLocation = mService.getCurrentLocation();
            if (lastLocation != null) {
                location.setText(lastLocation.getProvider());
            }
            String _clientId = mService.getClientId();
            Log.d(TAG, "onServiceConnected: " + _clientId);
            String[] tok = _clientId.split(Pattern.quote("-"));
            eventCode.setText(tok[0]);
            clientId.setText(tok[1]);
            username.setText(mService.getUserName());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case "location-update":
                    location.setText(intent.getStringExtra("provider"));
                    break;
                case "mqtt-connected":
                case "mqtt-reconnected":
                    mqttStatus.setText("connected");
                    break;
                case "mqtt-disconnected":
                    mqttStatus.setText("disconnected");
                    break;

            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        Log.d(TAG, "onCreate: NotificationActivity");
        if (BeaconService.SERVICE_STATUS != BeaconService.SERVICE_RUNNING) {
            finish();
        }
        Intent i = new Intent(getApplicationContext(), BeaconService.class);
        bindService(i, conn, 0);
        networkStatus = findViewById(R.id.NetworkStatus);
        switch (BeaconService.MQTT_STATUS) {
            case BeaconService.MQTT_CONNECTED:
                networkStatus.setText("connected");
                break;
            case BeaconService.MQTT_DISCONNECTED:
            case BeaconService.MQTT_CONNECTING:
                networkStatus.setText("disconnected");
                break;
        }
        location = findViewById(R.id.Location);
        mqttStatus = findViewById(R.id.NetworkStatus);
        eventCode = findViewById(R.id.EventCode);
        clientId = findViewById(R.id.ClientId);
        username = findViewById(R.id.Username);
        LocalBroadcastManager bus = LocalBroadcastManager.getInstance(getApplicationContext());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("location-update");
        intentFilter.addAction("mqtt-connected");
        intentFilter.addAction("mqtt-disconnected");
        bus.registerReceiver(receiver, intentFilter);
        pauseStart = findViewById(R.id.pauseStart);
        if (BeaconService.FUSED_LOCATION_STATUS == BeaconService.FUSED_LOCATION_STATUS_PAUSED) {
            pauseStart.setText("START");
        } else {
            pauseStart.setText("PAUSE");
        }

    }

    public void OnPause(View v) {
        if (BeaconService.FUSED_LOCATION_STATUS == BeaconService.FUSED_LOCATION_STATUS_PAUSED) {
            mService.startFusedLocation();
            Button b = (Button) v;
            b.setText("PAUSE");
        } else {
            mService.stopFusedLocation();
            Button b = (Button) v;
            b.setText("START");
        }
        Log.d(TAG, "OnPause: ");
    }

    public void OnStop(View v) {
        mService.stopBeaconService();
        finish();
        Log.d(TAG, "OnStop: ");
    }
}
