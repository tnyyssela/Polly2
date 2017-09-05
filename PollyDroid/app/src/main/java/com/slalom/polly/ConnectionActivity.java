package com.slalom.polly;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.UUID;

import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;

public class ConnectionActivity extends Activity implements View.OnClickListener /*, ServiceResultReceiver.Receiver*/ {

    private static final String TAG = ConnectionActivity.class.getName();

    private TextView mTextConnectionStatus;
    private TextView mTextProduct;
    private Button mBtnOpen;

//    private ServiceResultReceiver serviceReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        serviceReceiver = new ServiceResultReceiver(new Handler());

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_connection);

        initUI();

//        testFaceServices();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(PollyApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
//        serviceReceiver.setReceiver(this);
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
//        serviceReceiver.setReceiver(null);
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void initUI() {

        mTextConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        mTextProduct = (TextView) findViewById(R.id.text_product_info);
        mBtnOpen = (Button) findViewById(R.id.btn_open);
        mBtnOpen.setOnClickListener(this);
        mBtnOpen.setEnabled(false);

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSDKRelativeUI();
        }
    };

    private void refreshSDKRelativeUI() {
        BaseProduct mProduct = PollyApplication.getProductInstance();

        if (null != mProduct && mProduct.isConnected()) {
            Log.v(TAG, "refreshSDK: True");
            mBtnOpen.setEnabled(true);

            String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
            mTextConnectionStatus.setText("Status: " + str + " connected");

            if (null != mProduct.getModel()) {
                mTextProduct.setText("" + mProduct.getModel().getDisplayName());
            } else {
                mTextProduct.setText(R.string.product_information);
            }

        } else {
            Log.v(TAG, "refreshSDK: False");
            mBtnOpen.setEnabled(false);

            mTextProduct.setText(R.string.product_information);
            mTextConnectionStatus.setText(R.string.connection_loose);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btn_open: {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
            }
            default:
                break;
        }
    }


    /*
    @Override
    public void onReceiveServiceResult(int serviceCode, int resultCode, Bundle resultData) {

        if (serviceCode == ServiceCodes.DetectFace) {
            switch (resultCode) {
                case DetectFaceService.ERROR_CODE:
//                    handleError(data);
                    showToast("detect face error");
                    break;
                case DetectFaceService.SUCCESS_CODE:
                    handleDetectFace(resultData);
                    break;
            }
        }
        if (serviceCode == ServiceCodes.IdentifyFace) {
            switch (resultCode) {
                case IdentifyFaceService.ERROR_CODE:
//                    handleError(data);
                    showToast("identify face error");
                    break;
                case IdentifyFaceService.SUCCESS_CODE:
                    handleIdentifyFace(resultData);
                    break;
            }
        }
    }

    private void handleDetectFace(Bundle data) {
        showToast("face(s) detected");

        ParcelableFace[] faces = (ParcelableFace[]) data.getParcelableArray(DetectFaceService.FACES_EXTRA_KEY);

        String[] faceIds = new String[faces.length];

        for (int i = 0; i < faces.length; i++) {
            faceIds[i] = faces[i].faceId.toString();
        }

        try {
            Intent identifyFaceIntent = new Intent(Intent.ACTION_SYNC, null, this, IdentifyFaceService.class);
            identifyFaceIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);
            identifyFaceIntent.putExtra(IdentifyFaceService.FACE_IDS_EXTRA_KEY, faceIds);

            startService(identifyFaceIntent);

            showToast("identifying faces...");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void handleIdentifyFace(Bundle data) {
        String name = data.getString(IdentifyFaceService.NAME_EXTRA_KEY);
        if(name != null) {
            showToast(name);
        } else {
            showToast("name not found");
        }

    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ConnectionActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void testFaceServices() {
        try {
            Resources res = getResources();
            InputStream ins = res.openRawResource(R.raw.face);

            byte[] buffer = new byte[188092];

            ins.read(buffer);
            ins.close();

            Intent detectFaceIntent = new Intent(Intent.ACTION_SYNC, null, this, DetectFaceService.class);
            detectFaceIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);

            detectFaceIntent.putExtra(DetectFaceService.FACE_BUFFER_EXTRA_KEY, buffer);
            startService(detectFaceIntent);

            showToast("detecting faces...");
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
    */
}
