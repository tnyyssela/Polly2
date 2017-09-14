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

public class IdentifyFacePersonService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID = "test-polly-group";
    public static final String FACE_IDS_EXTRA_KEY = "face_ids_extra";
    public static final String NAME_EXTRA_KEY = "name_extra";
    public static final String PERSON_ID_EXTRA_KEY = "person-id-extra";
    public static final String FACE_DETAILS_EXTRA_KEY = "face-details-extra";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;
    public static final int COMPLETE_CODE = 3;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    public String targetPersonId;


    private ResultReceiver receiver;

    private static final String TAG = IdentifyFacePersonService.class.getSimpleName();

    public IdentifyFacePersonService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {
        try {
            intent = handledIntent;

            targetPersonId = intent.getStringExtra(PERSON_ID_EXTRA_KEY);

            ParcelableFace[] faceDetails = (ParcelableFace[])intent.getParcelableArrayExtra(FACE_DETAILS_EXTRA_KEY);

            receiver = intent.getParcelableExtra(ServiceResultReceiver.RECEIVER_KEY);

            String[] faceIds = intent.getStringArrayExtra(FACE_IDS_EXTRA_KEY);

            UUID[] faceIdsInput = new UUID[1];

            Bundle resultBundle = new Bundle();
            resultBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFacePerson);

            for (int i = 0; i < faceDetails.length; i ++) {
                faceIdsInput[0] = UUID.fromString(faceIds[i]);
                IdentifyResult[] identifyResults = faceServiceClient.identity(IdentifyFacePersonService.PERSON_GROUP_ID, faceIdsInput, faceIds.length);


                double confidenceThreshold = .5;

                if (identifyResults.length < 1) {
                    Bundle errorBundle = new Bundle();
                    errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFacePerson);
                    errorBundle.putString("e", "No people identified.");
                    if (receiver != null) {
                        receiver.send(ERROR_CODE, errorBundle);
                    }

                    return;
                }

                if (identifyResults[0].candidates.size() < 1) {
                    Log.i("IdentifyFacePerson", identifyResults[0].faceId.toString() + " has no candidates");
                    return;
                }

                Candidate candidate = identifyResults[0].candidates.get(0);

                String name = null;
                UUID personId = null;
                if (candidate.confidence > confidenceThreshold) {
                    personId = candidate.personId;

                    Person person = faceServiceClient.getPerson(IdentifyFacePersonService.PERSON_GROUP_ID, personId);
                    name = person.name;
                }

                String personIdString = null;
                if (personId != null) {
                    personIdString = personId.toString();
                }

                if (personIdString == targetPersonId) {
                    getPersonNameCallback(name, personIdString, faceDetails[i]);
                    return;
                } else if (name != null) {
                    resultBundle.putString(NAME_EXTRA_KEY, name);
                    resultBundle.putString(PERSON_ID_EXTRA_KEY, personIdString);
                }
            }

            receiver.send(COMPLETE_CODE, resultBundle);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFacePerson);
            errorBundle.putString("e", e.getMessage());
            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
            Log.i("IdentifyFacePerson", e.getMessage());
        }
    }

    private void getPersonNameCallback(String name, String personId, ParcelableFace faceDetail) {
        try {
            Bundle resultBundle = new Bundle();
            resultBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFacePerson);
            resultBundle.putString(NAME_EXTRA_KEY, name);
            resultBundle.putString(PERSON_ID_EXTRA_KEY, personId);
            resultBundle.putParcelable(FACE_DETAILS_EXTRA_KEY, faceDetail);

            receiver.send(SUCCESS_CODE, resultBundle);

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.IdentifyFacePerson);
            errorBundle.putString("e", e.getMessage());
            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
            Log.i("IdentifyFacePerson", e.getMessage());

        }

    }
}
