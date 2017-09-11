package com.slalom.polly;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.GroupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Created by Ian on 8/21/2017.
 */

public class GroupFacesService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";

    public static final String FACE_IDS_EXTRA_KEY = "face-ids-extra";

    public static final String ONLY_PROCESS_LARGEST_GROUP_EXTRA_KEY = "only-process-largest-group";



    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = 1;
    public static final int DEBUG_CODE = 2;

    public static FaceServiceClient faceServiceClient = new FaceServiceRestClient(SUBSCRIPTION_KEY);

    public Intent intent;

    private ResultReceiver receiver;

    private boolean onlyProcessLargestGroup = true;

    private static final String TAG = GroupFacesService.class.getSimpleName();

    public GroupFacesService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent handledIntent) {

        try {
            intent = handledIntent;

            receiver = intent.getExtras().getParcelable(ServiceResultReceiver.RECEIVER_KEY);

            onlyProcessLargestGroup = intent.getBooleanExtra(ONLY_PROCESS_LARGEST_GROUP_EXTRA_KEY, true);

            String[] faceIdStrings = intent.getStringArrayExtra(FACE_IDS_EXTRA_KEY);

            UUID[] faceIds = new UUID[faceIdStrings.length];
            for (int i = 0; i < faceIdStrings.length; i++) {
                faceIds[i] = UUID.fromString(faceIdStrings[i]);
            }

            GroupResult faceGroups = faceServiceClient.group(faceIds);

            if (onlyProcessLargestGroup) {
                if(faceGroups.groups.size() <= 0) {
                    Bundle errorBundle = new Bundle();
                    errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.GroupFaces);
                    errorBundle.putString("e", "no group identified");

                    receiver.send(ERROR_CODE, errorBundle);
                    return;
                }

                int maxLengthIndex = 0;
                int maxLength = 0;

                for (int i = 0; i < faceGroups.groups.size(); i++) {
                    UUID[] curr = faceGroups.groups.get(i);
                    if (curr.length > maxLength) {
                        maxLengthIndex = i;
                        maxLength = curr.length;
                    }
                }

                UUID[] largestGroup = faceGroups.groups.get(maxLengthIndex);
                faceGroups.groups = new ArrayList<>();
                faceGroups.groups.add(largestGroup);
            }

            faceGroupCallback(faceGroups);
        }
        catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.GroupFaces);
            errorBundle.putString("e", e.getMessage());

            if (receiver != null) {
                receiver.send(ERROR_CODE, errorBundle);
            }
        }


    }

    private void faceGroupCallback(GroupResult faceGroupResult) {
        try {
            Bundle bundledExtras = new Bundle();
            bundledExtras.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.GroupFaces);

            for (UUID[] group : faceGroupResult.groups) {

                String[] stringGroup = new String[group.length];
                for (int i = 0; i < group.length; i++) {
                    stringGroup[i] = group[i].toString();
                }

                bundledExtras.putStringArray(FACE_IDS_EXTRA_KEY, stringGroup);

                receiver.send(SUCCESS_CODE, bundledExtras);

            }

        } catch (Exception e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt(ServiceResultReceiver.SERVICE_CODE_KEY, ServiceCodes.GroupFaces);
            errorBundle.putString("e", e.getMessage());
            receiver.send(ERROR_CODE, errorBundle);
        }
    }
}
