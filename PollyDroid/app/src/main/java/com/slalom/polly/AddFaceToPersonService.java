package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.AddPersistedFaceResult;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;


/**
 * Created by Ian on 8/21/2017.
 */

public class AddFaceToPersonService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID = "test-polly-group";
    public static final String PERSON_ID_EXTRA_KEY = "person-id-extra";
    public static final String FACE_BUFFER_EXTRA_KEY = "face-buffer-extra";
    public static final String FACE_ID_EXTRA_KEY = "face-id-extra";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private static final String TAG = AddFaceToPersonService.class.getSimpleName();

    public AddFaceToPersonService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {

        try {
            intent = handledIntent;

            receiver = intent.getExtras().getParcelable(ServiceResultReceiver.RECEIVER_KEY);

            String personId = intent.getStringExtra(PERSON_ID_EXTRA_KEY);
            byte[] faceBuffer = intent.getByteArrayExtra(FACE_BUFFER_EXTRA_KEY);

            InputStream inputStream = new ByteArrayInputStream(faceBuffer);

            AddPersistedFaceResult addFaceResult = faceServiceClient.addPersonFace(PERSON_GROUP_ID, UUID.fromString(personId), inputStream, null, null);

            addFaceCallback(addFaceResult.persistedFaceId.toString());
        }
        catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddFaceToPerson);
            errorBundle.putString("e", e.getMessage());

            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }


    }

    private void addFaceCallback(String faceId) {
        try {
            Bundle bundledExtras = new Bundle();
            bundledExtras.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddFaceToPerson);

            bundledExtras.putString(FACE_ID_EXTRA_KEY, faceId);

            receiver.send(SUCCESS_CODE, bundledExtras);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddFaceToPerson);
            errorBundle.putString("e", e.getMessage());
            receiver.send(ERROR_CODE, errorBundle);
        }
    }
}
