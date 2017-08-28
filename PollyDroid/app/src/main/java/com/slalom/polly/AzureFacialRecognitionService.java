package com.slalom.polly;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Ian on 8/21/2017.
 */

public class AzureFacialRecognitionService implements FacialRecognitionService {

    private static final String SUBSCRIPTION_KEY = "13hc77781f7e4b19b5fcdd72a8df7156";
    private static final String SERVICE_LOCATION = "westcentralus";

    public String createPersonGroup (String personGroupId) {
        try
        {
            //TODO: ianb - validate personGroupId input
            // The valid characters for the ID below include numbers, English letters in lower case, '-', and '_'.
            // The maximum length of the personGroupId is 64.

            URL personGroupUrl = new URL("https", SERVICE_LOCATION + ".api.cognitive.microsoft.com/face/v1.0/persongroups/" + personGroupId, 80, "");
            HttpURLConnection conn = (HttpURLConnection) personGroupUrl.openConnection();

            conn.setRequestMethod("PUT");

            String requestBody =  "{ \"name\":\"TestPollyGroup\",\"userData\":\"Test Group Data.\" }";

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
