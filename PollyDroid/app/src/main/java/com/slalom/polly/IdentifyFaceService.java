package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Candidate;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.Person;

import java.util.UUID;

/**
 * Created by Ian on 8/21/2017.
 */

public class IdentifyFaceService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID_EXTRA_KEY = "test-polly-group";
    public static final String FACE_IDS_EXTRA_KEY = "face_ids_extra";
    public static final String NAME_EXTRA_KEY = "name_extra";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private static final String TAG = IdentifyFaceService.class.getSimpleName();

    public IdentifyFaceService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {
        try {
            intent = handledIntent;

            Bundle debugBundle = new Bundle();

            receiver = intent.getParcelableExtra(ServiceResultReceiver.RECEIVER_KEY);

            debugBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFace);

            Log.i("IdentifyFace", "0");


            String[] faceIds = intent.getStringArrayExtra(FACE_IDS_EXTRA_KEY);

            UUID[] faceIdsInput = new UUID[faceIds.length];

            for (int i = 0; i < faceIds.length; i ++) {
                faceIdsInput[i] = UUID.fromString(faceIds[i]);
            }

            Log.i("IdentifyFace", "1");

            IdentifyResult[] identifyResults = faceServiceClient.identity(IdentifyFaceService.PERSON_GROUP_ID_EXTRA_KEY, faceIdsInput, faceIds.length);

            Log.i("IdentifyFace", "2");

            double confidenceThreshold = .5;

            if (identifyResults.length < 1) {
                return;
            }

            Log.i("IdentifyFace", "3");

            if (identifyResults[0].candidates.size() < 1) {
                Log.i("IdentifyFace", identifyResults[0].faceId.toString() + " has no candidates");
                return;
            }

            Candidate candidate = identifyResults[0].candidates.get(0);

            String name = null;
            if (candidate.confidence > confidenceThreshold) {
                UUID personId = candidate.personId;

                Person person = faceServiceClient.getPerson(IdentifyFaceService.PERSON_GROUP_ID_EXTRA_KEY, personId);
                name = person.name;
            }

            Log.i("IdentifyFace", "4");

            getPersonNameCallback(name);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFace);
            errorBundle.putString("e", e.getMessage());
            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
            Log.i("IdentifyFace", e.getMessage());
        }
    }

    private void getPersonNameCallback(String name) {
        try {
            Bundle resultBundle = new Bundle();
            resultBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFace);
            resultBundle.putString(NAME_EXTRA_KEY, name);

            receiver.send(SUCCESS_CODE, resultBundle);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFace);
            errorBundle.putString("e", e.getMessage());
            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
            Log.i("IdentifyFace", e.getMessage());

        }

    }
}
