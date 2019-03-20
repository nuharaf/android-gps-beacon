package com.example.nuha.sensorbeacon3;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;


import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

public class BeaconService extends Service {
    public static final int MQTT_DISCONNECTED = 0;
    public static final int MQTT_CONNECTED = 1;
    public static final int MQTT_CONNECTING = 2;
    public static int MQTT_STATUS = MQTT_DISCONNECTED;
    public static int SERVICE_RUNNING = 1;
    public static int SERVICE_NOT_RUNNING = 0;
    public static int SERVICE_STATUS = SERVICE_NOT_RUNNING;
    public static int FUSED_LOCATION_STATUS_STARTED = 1;
    public static int FUSED_LOCATION_STATUS_PAUSED = 0;
    public static int FUSED_LOCATION_STATUS  = FUSED_LOCATION_STATUS_PAUSED;

    private static String TAG = "BeaconService";
    private static long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    NotificationManager notificationManager;
    SharedPreferences pref;

    MqttAsyncClient mqttClient;
    MqttConnectOptions mqttConnectOptions;
    FusedLocationProviderClient mFusedLocationClient;
    Location mCurrentLocation;


    LocalBroadcastManager bus;
    Timer t;


    private final Binder mBinder = new LocalBinder();

    NotificationCompat.Builder mBuilder;

    public BeaconService() {
    }

    public class LocalBinder extends Binder {
        BeaconService getService() {
            return BeaconService.this;
        }
    }

    private int initMqtt(String url,String clientId,String username,String password) {
        Log.v(TAG, "initMqtt called");
        try {
            mqttClient = new MqttAsyncClient(url, clientId,null);
        } catch (MqttException e) {
            e.printStackTrace();
            return -1;
        }
        mqttClient.setCallback(cb);
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setKeepAliveInterval(60);
        mqttConnectOptions.setPassword(password.toCharArray());
        mqttConnectOptions.setUserName(username);
        return 0;
    }

    MqttCallbackExtended cb = new MqttCallbackExtended() {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            Bundle bundle = new Bundle();
            String uri = mqttClient.getServerURI();
            MQTT_STATUS = MQTT_CONNECTED;
            mBuilder.setContentTitle("Connected");
            notificationManager.notify(1,mBuilder.build());
            if (reconnect) {
                Log.v(TAG, "Reconnect complete, uri : " + uri);
                bus.sendBroadcast(new Intent("mqtt-reconnected"));
            } else {
                Log.v(TAG, "Connect complete, uri : " + uri);
                bus.sendBroadcast(new Intent("mqtt-connected"));

            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            Log.d(TAG, "Connection Lost: ",cause);
            MQTT_STATUS = MQTT_DISCONNECTED;
            bus.sendBroadcast(new Intent("mqtt-disconnected"));
            mBuilder.setContentTitle("Connection lost");
            notificationManager.notify(1,mBuilder.build());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.v(TAG, "Message arrived : " + new String(message.getPayload()));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            Log.v(TAG, "Delivery complete : topic :  " + token.getTopics());
        }
    };


//    public void purgePendingMessage() {
//        IMqttDeliveryToken toks[] = mqttAndroidClient.getPendingDeliveryTokens();
//        for (IMqttDeliveryToken tok : toks){
//            try {
//                mqttAndroidClient.removeMessage(tok);
//            } catch (MqttException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            String topic = "gps";
            super.onLocationResult(locationResult);
            mCurrentLocation = locationResult.getLastLocation();
            final JSONObject loc = new JSONObject();
            try {
                loc.put("lat", mCurrentLocation.getLatitude());
                loc.put("lon", mCurrentLocation.getLongitude());
                loc.put("ele", mCurrentLocation.getAltitude());
                loc.put("time", mCurrentLocation.getTime());
                loc.put("accuracy", mCurrentLocation.getAccuracy());
                loc.put("speed", mCurrentLocation.getSpeed());
                loc.put("src", mCurrentLocation.getProvider());
                Intent i = new Intent("location-update");
                i.putExtra("latitude",mCurrentLocation.getLatitude());
                i.putExtra("longitude",mCurrentLocation.getLongitude());
                i.putExtra("altitude",mCurrentLocation.getAltitude());
                i.putExtra("time",mCurrentLocation.getTime());
                i.putExtra("speed",mCurrentLocation.getSpeed());
                i.putExtra("accuracy",mCurrentLocation.getAccuracy());
                i.putExtra("provider",mCurrentLocation.getProvider());
                bus.sendBroadcast(i);
                if (mqttClient.isConnected()) {
                    try {
                        mqttClient.publish(topic, loc.toString().getBytes(), 0, false);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };


    public int startFusedLocation() {
        LocationRequest mLocationRequest;

        int rate = 1000;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(rate * 10);
        mLocationRequest.setFastestInterval(rate);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "ACCESS_FINE_LOCATION permission not granted");
            return 1;
        }
        else {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
            mBuilder.setContentText("Location update running");
            notificationManager.notify(1,mBuilder.build());
            FUSED_LOCATION_STATUS = FUSED_LOCATION_STATUS_STARTED;
            return 0;
        }
    }

    public void stopFusedLocation() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        mBuilder.setContentText("Location update paused");
        notificationManager.notify(1,mBuilder.build());
        FUSED_LOCATION_STATUS = FUSED_LOCATION_STATUS_PAUSED;
    }

    public void stopBeaconService(){
        stopForeground(true);
        stopSelf();
    }

    public Location getCurrentLocation(){
        return mCurrentLocation;
    }

    public String getClientId(){
        return mqttClient.getClientId();
    }
    public String getUserName(){return mqttConnectOptions.getUserName();}



    Handler mainhandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            return false;
        }
    });


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy:  ");
        try {
            mqttClient.disconnect(1000).waitForCompletion();
            mqttClient.close();
        } catch (MqttException e) {
            e.printStackTrace();
            Log.d(TAG, "onDestroy:  Exception");
            return;
        }
        SERVICE_STATUS = SERVICE_NOT_RUNNING;
        notificationManager.cancelAll();
        t.cancel();
        Log.d(TAG, "onDestroy:  Success");
    }




    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: startId :  " + startId);
        Log.d(TAG,"SERVICE_STATUS flag : " + SERVICE_STATUS);
        pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("app_default", "default", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mBuilder = new NotificationCompat.Builder(this, "app_default")
                .setSmallIcon(R.drawable.sensorbeacon)
                .setContentTitle("SensorBeacon")
                .setContentText("Sencor beacon running")
                .setContentIntent(pendingIntent).setAutoCancel(true);
        startForeground(1, mBuilder.build());

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

//        mainhandler.postDelayed(()->{
//            mBuilder.setContentText("ulala");
//            notificationManager.notify(1,mBuilder.build());
//        },1000);
        mainhandler.postDelayed(()->{
            String mqtt_server = pref.getString("mqtt_server","http://10.0.2.2:8888");
            initMqtt(mqtt_server,intent.getStringExtra("clientId"),intent.getStringExtra("username"),"demo");
            try {
                mqttClient.connect(mqttConnectOptions);
                MQTT_STATUS = MQTT_CONNECTING;
            } catch (MqttException e) {
                e.printStackTrace();
            }
            t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "run: ");
                    if(mqttClient.isConnected()){
                        try {
                            mqttClient.publish("greeting","{\"hello\" :\"world\"}".getBytes(),0,false);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }
                }
            },0,10000);
            startFusedLocation();
        },1000);
        SERVICE_STATUS = SERVICE_RUNNING;
        bus = LocalBroadcastManager.getInstance(getApplicationContext());
        return START_NOT_STICKY;
    }
}
