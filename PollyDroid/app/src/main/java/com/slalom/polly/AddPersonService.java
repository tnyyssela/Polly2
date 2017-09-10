package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;
import com.microsoft.projectoxford.face.contract.GroupResult;

import java.util.ArrayList;
import java.util.UUID;


/**
 * Created by Ian on 8/21/2017.
 */

public class AddPersonService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID = "test-polly-group";
    public static final String PERSON_ID_EXTRA_KEY = "person-id-extra";
    public static final String PERSON_NAME_EXTRA_KEY = "person-name-extra";
    public static final String PERSON_DATA_EXTRA_KEY = "person-data-extra";
    public static final String FACE_IDS_EXTRA_KEY = "face-ids-extra";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private static final String TAG = AddPersonService.class.getSimpleName();

    public AddPersonService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {

        try {
            intent = handledIntent;

            receiver = intent.getExtras().getParcelable(ServiceResultReceiver.RECEIVER_KEY);

            String personName = intent.getStringExtra(PERSON_NAME_EXTRA_KEY);
            String personData = intent.getStringExtra(PERSON_DATA_EXTRA_KEY);

            CreatePersonResult createPersonResult = faceServiceClient.createPerson(PERSON_GROUP_ID, personName, personData);


            addPersonCallback(createPersonResult.personId.toString());
        }
        catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddPerson);
            errorBundle.putString("e", e.getMessage());

            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }


    }

    private void addPersonCallback(String personId) {
        try {
            Bundle bundledExtras = new Bundle();
            bundledExtras.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddPerson);
            bundledExtras.putString(PERSON_ID_EXTRA_KEY, personId);
            bundledExtras.putStringArray(FACE_IDS_EXTRA_KEY, intent.getStringArrayExtra(FACE_IDS_EXTRA_KEY));

            receiver.send(SUCCESS_CODE, bundledExtras);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.AddPerson);
            errorBundle.putString("e", e.getMessage());
            receiver.send(ERROR_CODE, errorBundle);
        }
    }
}
