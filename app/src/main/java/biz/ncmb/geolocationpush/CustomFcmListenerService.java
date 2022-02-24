package biz.ncmb.geolocationpush;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.messaging.RemoteMessage;
import com.nifcloud.mbaas.core.NCMB;
import com.nifcloud.mbaas.core.NCMBException;
import com.nifcloud.mbaas.core.NCMBFirebaseMessagingService;
import com.nifcloud.mbaas.core.NCMBObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class CustomFcmListenerService extends NCMBFirebaseMessagingService implements OnSuccessListener<Void>,  OnFailureListener  {

    protected static final String TAG = "CustomListenerService";

    protected static final int GEOFENCE_RADIUS_IN_METERS = 1000;

    protected static final int GEOFENCE_EXPIRATION_IN_HOURS = 1;

    protected static final int GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000;

    private PendingIntent mGeofencePendingIntent;

    private GeofencingClient geofencingClient;
    private ArrayList<Geofence> mGeofenceList;
    private GeofencingRequest mGeofenceRequest;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Bundle data = getBundleFromRemoteMessage(remoteMessage);

        //ペイロードデータの取得
        if (data.containsKey("com.nifcloud.mbaas.Data")) {
            try {
                JSONObject json = new JSONObject(data.getString("com.nifcloud.mbaas.Data"));

                //Locationデータの取得
                NCMBObject point = new NCMBObject("Location");

                //SDKの再初期化が必要
                NCMB.initialize(
                        this.getApplicationContext(),
                        "0730e01abce99ac3d5400690cb658a25f79e8f0bac8895dd67283e9b98077d1e",
                        "d4175a28a524d55c47057f6f77b47c0c654842521b94488442867c82deb83dac"
                );
                try {
                    point.setObjectId(json.getString("location_id"));
                    point.fetch();
                    Log.d(TAG, "location name:" + point.getString("name"));
                } catch (NCMBException e) {
                    e.printStackTrace();
                }

                // Initially set the PendingIntent used in addGeofences() and removeGeofences() to null.
                mGeofencePendingIntent = null;

                // create an instance of the Geofencing client
                geofencingClient = LocationServices.getGeofencingClient(this);

                createGeofenceRequest(point);

                //geofenceの作成
                addGeofences();

            } catch (JSONException e) {
                //エラー処理
                Log.e(TAG, "error:" + e.getMessage());
            }
        }

        // デフォルトの通知を実行する場合はsuper.onMessageReceivedを実行する
        // super.onMessageReceived(remoteMessage);
    }

    private void createGeofenceRequest(NCMBObject point) {
        // Empty list for storing geofences.
        mGeofenceList = new ArrayList<>();

        //Geofenceオブジェクトの作成
        mGeofenceList.add(new Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId(point.getString("name"))
                .setCircularRegion(
                        point.getGeolocation("geo").getLatitude(),
                        point.getGeolocation("geo").getLongitude(),
                        GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)
                .build());

        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        mGeofenceRequest = builder.build();
    }

    @SuppressLint("MissingPermission")
    private void addGeofences() {
        geofencingClient.addGeofences(mGeofenceRequest, getGeofencePendingIntent())
                .addOnSuccessListener(this)
                .addOnFailureListener(this);
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mGeofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE);
        } else {
            mGeofencePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return mGeofencePendingIntent;
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        Log.d(TAG, "Failed to add geofences.");
    }

    @Override
    public void onSuccess(@NonNull Void unused) {
        Log.d(TAG, "Geofences added.");
    }
}
