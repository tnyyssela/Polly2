package com.slalom.polly;

import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.ResultReceiver;

/**
 * Created by ianb on 9/4/2017.
 */

class ServiceResultReceiver extends ResultReceiver implements Parcelable {
    public static final String SERVICE_CODE_KEY = "serviceCode";
    public static final String RECEIVER_KEY = "receiver";

    private Receiver mReceiver;

    public ServiceResultReceiver(Handler handler) {
        super(handler);
    }

    public void setReceiver(Receiver receiver) {
        mReceiver = receiver;
    }

    interface Receiver {
        void onReceiveServiceResult(int serviceCode, int resultCode, Bundle resultData);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {

        int serviceCode = resultData.getInt(SERVICE_CODE_KEY);

        if (mReceiver != null) {
            mReceiver.onReceiveServiceResult(serviceCode, resultCode, resultData);
        }
    }
}
