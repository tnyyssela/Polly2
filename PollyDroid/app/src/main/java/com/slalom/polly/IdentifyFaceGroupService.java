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

import java.util.UUID;

/**
 * Created by Ian on 8/21/2017.
 */

public class IdentifyFaceGroupService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID = "test-polly-group";
    public static final String FACE_IDS_EXTRA_KEY = "face_ids_extra";
    public static final String PERSON_ID_EXTRA_KEY = "person-id-extra";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private static final String TAG = IdentifyFaceGroupService.class.getSimpleName();

    public IdentifyFaceGroupService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {
        try {
            intent = handledIntent;

            receiver = intent.getParcelableExtra(ServiceResultReceiver.RECEIVER_KEY);

            String[] faceIds = intent.getStringArrayExtra(FACE_IDS_EXTRA_KEY);


            UUID[] faceIdsInput = new UUID[faceIds.length];

            for (int i = 0; i < faceIds.length; i ++) {
                faceIdsInput[i] = UUID.fromString(faceIds[i]);
            }

            IdentifyResult[] identifyResults = faceServiceClient.identity(PERSON_GROUP_ID, faceIdsInput, faceIds.length);


            if (identifyResults.length < 1) {
                return;
            }

            processGroup(identifyResults, faceIds);


        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFaceGroup);
            errorBundle.putString("e", e.getMessage());
            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }
    }

    private void processGroup(IdentifyResult[] identifyResults, String[] faceIds) {
        double confidenceThreshold = .50;
        UUID personId = null;
            for (IdentifyResult identifyResult : identifyResults) {
                try {

                for (Candidate candidate : identifyResult.candidates) {
                    if (identifyResult.candidates.size() < 1) {
                        // no matching person for face
                        continue;
                    }

                    if (candidate.confidence > confidenceThreshold) {
                        //group person identified
                        personId = candidate.personId;
                        break;
                    }

                }

                String personIdString = null;
                if (personId != null) {
                    personIdString = personId.toString();
                }

                Bundle resultBundle = new Bundle();
                resultBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFaceGroup);
                resultBundle.putStringArray(FACE_IDS_EXTRA_KEY, faceIds);
                resultBundle.putString(PERSON_ID_EXTRA_KEY, personIdString);

                receiver.send(SUCCESS_CODE, resultBundle);

            } catch (Exception e) {
                Bundle errorBundle = new Bundle();
                errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFaceGroup);
                errorBundle.putString("e", e.getMessage());
                if (receiver != null) {
                    receiver.send(ERROR_CODE, errorBundle);
                }
                Log.i("IdentifyFaceGroup", e.getMessage());

            }
        }
    }
}
