package com.slalom.polly;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ConfigureSearchSubjectActivity extends Activity implements ServiceResultReceiver.Receiver {
    //Image loading result to pass to startActivityForResult method
    public static final int LOAD_IMAGE_RESULTS = 1;


    private ServiceResultReceiver serviceReceiver;
    private Handler handler;

    private Button imagePicker;

    private EditText firstNameInput;
    private EditText lastNameInput;

    private ArrayList<byte[]> faceBuffers;
    private Map<String, Integer> faceMap;

    private int remainingImagesToProcess = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        serviceReceiver = new ServiceResultReceiver(handler);
        serviceReceiver.setReceiver(this);

        faceMap = new HashMap<>();

        setContentView(R.layout.activity_configure_search_subject);

        firstNameInput = (EditText)findViewById(R.id.input_first_name);
        lastNameInput = (EditText)findViewById(R.id.input_last_name);

        imagePicker = (Button)findViewById(R.id.btn_image_picker);

        imagePicker.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if(firstNameInput.getText().length() <= 0 || lastNameInput.getText().length() <= 0) {
                    showToast("please enter a name");
                    return;
                }

                Intent intent = new Intent();
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"Select Picture"), LOAD_IMAGE_RESULTS);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOAD_IMAGE_RESULTS && resultCode == Activity.RESULT_OK && data != null) {
            try {
                ClipData clipData = data.getClipData();

                remainingImagesToProcess = clipData.getItemCount();

                faceBuffers = new ArrayList<>(remainingImagesToProcess);

                for (int i = 0; i < remainingImagesToProcess; i++) {
                    Uri imageUri = clipData.getItemAt(i).getUri();
                    InputStream imageStream = getContentResolver().openInputStream(imageUri);

                    if (imageStream == null) {
                        return;
                    }

                    byte[] faceBuffer = new byte[imageStream.available()];

                    imageStream.read(faceBuffer);

                    Bitmap btm =  BitmapFactory.decodeByteArray(faceBuffer, 0, faceBuffer.length);

                    int imageSizeLimit = 1000000;

                    if (faceBuffer.length > imageSizeLimit) {
                        float scale = (imageSizeLimit / faceBuffer.length) * .90f;

                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        btm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        faceBuffer = stream.toByteArray();

                        if (faceBuffer.length > imageSizeLimit) {
                            btm = toGrayscale(btm);
                            btm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                            faceBuffer = stream.toByteArray();

                            if (faceBuffer.length > imageSizeLimit) {
                                btm = Bitmap.createScaledBitmap(btm, Math.round(btm.getWidth() * scale), Math.round(btm.getHeight() * scale), true);
                                btm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                faceBuffer = stream.toByteArray();

                                if (faceBuffer.length > imageSizeLimit) {
                                    Log.i("Upload Image", "File too large");
                                    return;
                                }
                            }

                        }

                    }


                    final Intent detectFaceIntent = new Intent(Intent.ACTION_SYNC, null, this, DetectFaceService.class);
                    detectFaceIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver)
                    .putExtra(DetectFaceService.FACE_BUFFER_EXTRA_KEY, faceBuffer)
                    .putExtra(DetectFaceService.FACE_BUFFER_INDEX_EXTRA_KEY, i);

                    startService(detectFaceIntent);

                    faceBuffers.add(i, faceBuffer);
                }
            } catch (Exception e) {
                Log.i("SearchActivity", e.getMessage());
            }
        }
    }

    private Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    @Override
    public void onReceiveServiceResult(int serviceCode, int resultCode, Bundle resultData) {
        try {
            if (serviceCode == ServiceCodes.DetectFace) {
                switch (resultCode) {
                    case DetectFaceService.ERROR_CODE:
                        break;
                    case DetectFaceService.DEBUG_CODE:
                        break;
                    case DetectFaceService.SUCCESS_CODE:
                        handleDetectFace(resultData);
                        break;
                }
            }
            if (serviceCode == ServiceCodes.GroupFaces) {
                switch (resultCode) {
                    case GroupFacesService.ERROR_CODE:
                        break;
                    case GroupFacesService.DEBUG_CODE:
                        break;
                    case GroupFacesService.SUCCESS_CODE:
                        handleGroupFace(resultData);
                        break;
                }
            }
            if (serviceCode == ServiceCodes.IdentifyFaceGroup) {
                switch (resultCode) {
                    case IdentifyFacePersonService.ERROR_CODE:
                        break;
                    case IdentifyFacePersonService.DEBUG_CODE:
                        break;
                    case IdentifyFacePersonService.SUCCESS_CODE:
                        handleIdentifyFaceGroup(resultData);
                        break;
                }
            }
            if (serviceCode == ServiceCodes.AddPerson) {
                switch (resultCode) {
                    case AddPersonService.ERROR_CODE:
                        break;
                    case AddPersonService.DEBUG_CODE:
                        break;
                    case AddPersonService.SUCCESS_CODE:
                        handleAddPerson(resultData);
                        break;
                }
            }
            if (serviceCode == ServiceCodes.AddFaceToPerson) {
                switch (resultCode) {
                    case AddFaceToPersonService.ERROR_CODE:
                        break;
                    case AddFaceToPersonService.DEBUG_CODE:
                        break;
                    case AddFaceToPersonService.SUCCESS_CODE:
                        handleAddFaceToPerson(resultData);
                        break;
                }
            }

        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }

    }

    private void handleDetectFace(Bundle data) {
        remainingImagesToProcess --;

        try {
            String[] faceIds = data.getStringArray(DetectFaceService.FACES_EXTRA_KEY);
            int faceBufferIndex = data.getInt(DetectFaceService.FACE_BUFFER_EXTRA_KEY);

            for (String faceId : faceIds) {
                faceMap.put(faceId, faceBufferIndex);
            }

            Log.i("SearchActivity", "faces retrieved");

            if (faceIds == null || faceIds.length < 1) {
                Log.i("SearchActivity", "no face ids");
                return;
            }

            Log.i("SearchActivity", "start identify service");

            if (remainingImagesToProcess <= 0) {
                showToast("all photos processed");

                Intent groupFacesIntent = new Intent(Intent.ACTION_SYNC, null, this, GroupFacesService.class);
                groupFacesIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver)
                .putExtra(GroupFacesService.FACE_IDS_EXTRA_KEY, faceMap.keySet().toArray(new String[faceMap.keySet().size()]))
                .putExtra(GroupFacesService.ONLY_PROCESS_LARGEST_GROUP_EXTRA_KEY, true);

                startService(groupFacesIntent);
            }

        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }

    }

    private void handleGroupFace(Bundle data) {
        try {
            String[] faceIds = data.getStringArray(GroupFacesService.FACE_IDS_EXTRA_KEY);

            if (faceIds == null || faceIds.length < 1) {
                return;
            }

            Intent identifyFaceGroupIntent = new Intent(Intent.ACTION_SYNC, null, this, IdentifyFaceGroupService.class);
            identifyFaceGroupIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);
            identifyFaceGroupIntent.putExtra(IdentifyFaceGroupService.FACE_IDS_EXTRA_KEY, faceIds);

            startService(identifyFaceGroupIntent);

        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }

    }


    private void handleIdentifyFaceGroup(Bundle data) {
        try {

            String[] faceIds = data.getStringArray(IdentifyFaceGroupService.FACE_IDS_EXTRA_KEY);
            String personId = data.getString(IdentifyFaceGroupService.PERSON_ID_EXTRA_KEY);

            if (personId == null) {
                //ADD PERSON
                showToast("new person identified");

                Intent addPersonIntent = new Intent(Intent.ACTION_SYNC, null, this, AddPersonService.class);
                addPersonIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);

                String personName = firstNameInput.getText().toString() + " " + lastNameInput.getText().toString();

                addPersonIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);
                addPersonIntent.putExtra(AddPersonService.PERSON_NAME_EXTRA_KEY, personName);
                addPersonIntent.putExtra(AddPersonService.PERSON_DATA_EXTRA_KEY, "");
                addPersonIntent.putExtra(AddPersonService.FACE_IDS_EXTRA_KEY, faceIds);

                startService(addPersonIntent);

            } else {
                //ADD FACES TO PERSON
                addFacesToPerson(faceIds, personId);
            }



        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }
    }


    private void handleAddPerson(Bundle data) {
        try {
            String personId = data.getString(AddPersonService.PERSON_ID_EXTRA_KEY);
            String[] faceIds = data.getStringArray(AddPersonService.FACE_IDS_EXTRA_KEY);


            addFacesToPerson(faceIds, personId);


        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }

    }


    private void handleAddFaceToPerson(Bundle data) {
        try {
            Log.i("SearchActivity", "Added face to person");
        } catch (Exception e) {
            Log.i("SearchActivity", e.getMessage());
        }
    }


    private void addFacesToPerson(String[] faceIds, String personId) {
        for (String faceId : faceIds) {
            byte[] face = faceBuffers.get(faceMap.get(faceId));

            Intent addFaceToPersonIntent = new Intent(Intent.ACTION_SYNC, null, this, AddFaceToPersonService.class);
            addFaceToPersonIntent.putExtra(ServiceResultReceiver.RECEIVER_KEY, serviceReceiver);
            addFaceToPersonIntent.putExtra(AddFaceToPersonService.FACE_BUFFER_EXTRA_KEY, face);
            addFaceToPersonIntent.putExtra(AddFaceToPersonService.PERSON_ID_EXTRA_KEY, personId);

            startService(addFaceToPersonIntent);
        }

        showToast("Faces registered to target person");
    }


    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ConfigureSearchSubjectActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
