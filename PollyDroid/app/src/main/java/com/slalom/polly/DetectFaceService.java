package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;

import java.io.ByteArrayInputStream;


/**
 * Created by Ian on 8/21/2017.
 */

public class DetectFaceService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String FACE_BUFFER_EXTRA_KEY = "faceBufferExtra";
    public static final String FACE_BUFFER_INDEX_EXTRA_KEY = "faceBufferIndexExtra";
    public static final String FACES_EXTRA_KEY = "facesExtra";


    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    int faceBufferIndex;

    private static final String TAG = DetectFaceService.class.getSimpleName();

    public DetectFaceService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {

        try {
            intent = handledIntent;

            receiver = intent.getExtras().getParcelable(ServiceResultReceiver.RECEIVER_KEY);

            byte[] faceBuffer = intent.getByteArrayExtra(FACE_BUFFER_EXTRA_KEY);

            faceBufferIndex = intent.getIntExtra(FACE_BUFFER_INDEX_EXTRA_KEY, -1);



            Face[] faces = faceServiceClient.detect(new ByteArrayInputStream(faceBuffer), true, true, new FaceServiceClient.FaceAttributeType[0]);

            detectFaceCallback(faces);
        }
        catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.DetectFace);
            errorBundle.putString("e", e.getMessage());
            Log.i("DetectFace", e.getMessage());

            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }


    }

    private void detectFaceCallback(Face[] detectFaceResult) {
        try {
            Bundle bundledExtras = new Bundle();
            bundledExtras.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.DetectFace);

            if (detectFaceResult == null) {
                bundledExtras.putString("e", "faces is null");
                receiver.send(ERROR_CODE, bundledExtras);
                return;
            } else if (detectFaceResult.length < 1) {
                bundledExtras.putString("e", "faces is empty");
                receiver.send(ERROR_CODE, bundledExtras);
                return;
            }

            String[] faceIds = new String[detectFaceResult.length];

            ParcelableFace[] parcelableFaces = new ParcelableFace[detectFaceResult.length];

            for (int i = 0; i < detectFaceResult.length; i++) {
                parcelableFaces[i] = new ParcelableFace(detectFaceResult[i]);
                faceIds[i] = detectFaceResult[i].faceId.toString();
            }

            bundledExtras.putStringArray(FACES_EXTRA_KEY, faceIds);
            bundledExtras.putInt(FACE_BUFFER_INDEX_EXTRA_KEY, faceBufferIndex);

            receiver.send(SUCCESS_CODE, bundledExtras);
        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.DetectFace);
            errorBundle.putString("e", e.getMessage());
            receiver.send(ERROR_CODE, errorBundle);
        }
    }

}
