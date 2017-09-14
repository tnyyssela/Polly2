package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;
import com.microsoft.projectoxford.face.contract.TrainingStatus;


/**
 * Created by Ian on 8/21/2017.
 */

public class TrainPersonGroupService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String PERSON_GROUP_ID = "test-polly-group";

    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private static final String TAG = TrainPersonGroupService.class.getSimpleName();

    public TrainPersonGroupService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {

        try {
            intent = handledIntent;

            receiver = intent.getExtras().getParcelable(ServiceResultReceiver.RECEIVER_KEY);

            faceServiceClient.trainPersonGroup(PERSON_GROUP_ID);

            boolean isComplete = false;

            while(isComplete) {

                TrainingStatus tStatus = faceServiceClient.getPersonGroupTrainingStatus(PERSON_GROUP_ID);

                if (tStatus.status == TrainingStatus.Status.Succeeded) {
                    isComplete = true;
                    receiver.send(SUCCESS_CODE, new Bundle());
                    break;
                }

                if (tStatus.status == TrainingStatus.Status.Failed) {
                    isComplete = true;
                    receiver.send(ERROR_CODE, new Bundle());
                    break;
                }


                wait(1000);
            }


        }
        catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.TrainPersonGroup);
            errorBundle.putString("e", e.getMessage());

            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }


    }

}
