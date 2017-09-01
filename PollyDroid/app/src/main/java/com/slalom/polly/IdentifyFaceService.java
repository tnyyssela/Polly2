package com.slalom.polly;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import com.google.gson.JsonSerializer;

import org.json.JSONArray;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Ian on 8/21/2017.
 */

public class IdentifyFaceService extends IntentService{

    private static final String SUBSCRIPTION_KEY = "3776b03f131645b8b7c3f1653dc35ac0";
    private static final String SERVICE_LOCATION = "westus";

    private static final String TAG = IdentifyFaceService.class.getSimpleName();

    public static final String PERSON_GROUP_ID_EXTRA = "test-polly-group";
    public static final String IDENTIFY_FACE_EXTRA = "identifyFace";
    public static final String FACE_IDS_EXTRA = "faceIds";
    public static final String PENDING_RESULT_EXTRA = "pending_result";

    public static final int RESULT_CODE = 0;
    public static final int INVALID_URL_CODE = 1;
    public static final int ERROR_CODE = 2;

    public IdentifyFaceService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PendingIntent reply = intent.getParcelableExtra(PENDING_RESULT_EXTRA);
        String[] faceIds = (String[]) intent.getParcelableArrayListExtra(FACE_IDS_EXTRA).toArray();

        try {
            try {

                Intent result = new Intent();

                String personGroupResult = identifyFace(PERSON_GROUP_ID_EXTRA, faceIds);

                result.putExtra(IDENTIFY_FACE_EXTRA, personGroupResult);

                reply.send(this, RESULT_CODE, result);
                reply.send(this, RESULT_CODE, result);
            } catch (MalformedURLException exc) {
                reply.send(INVALID_URL_CODE);
            } catch (Exception exc) {
                // could do better by treating the different sax/xml exceptions individually
                reply.send(ERROR_CODE);
            }
        } catch (PendingIntent.CanceledException exc) {
            Log.i(TAG, "reply cancelled", exc);
        }
    }

    private String identifyFace (String personGroupId, String[] faceIds) throws MalformedURLException {

        //TODO: ianb - validate personGroupId input
        // The valid characters for the ID below include numbers, English letters in lower case, '-', and '_'.
        // The maximum length of the personGroupId is 64.

        URL personGroupUrl = new URL("https", SERVICE_LOCATION + ".api.cognitive.microsoft.com/face/v1.0/identify", 80, "");

        try
        {
            HttpURLConnection conn = (HttpURLConnection) personGroupUrl.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            JSONArray jArray = new JSONArray(faceIds);

            String requestBody =  "{ \"faceIds\":\"" + jArray.toString() + "\",\"personGroupId\":\"" + personGroupId + "\" }";

            byte[] outputInBytes = requestBody.getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write( outputInBytes );
            os.close();

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY);

            int code = conn.getResponseCode();
            String message = conn.getResponseMessage();

            if (code >= 200 && code < 300) {
                return message;
            }

        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }

        return "request failed";

    }
}
