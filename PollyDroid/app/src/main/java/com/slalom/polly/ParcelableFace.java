package com.slalom.polly;

import android.os.Parcel;
import android.os.Parcelable;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;
import java.util.UUID;

/**
 * Created by ianb on 9/4/2017.
 */

public class ParcelableFace extends Face implements Parcelable {
    protected ParcelableFace(Parcel in) {

        String[] data = new String[5];

        in.readStringArray(data);

        this.faceId = UUID.fromString(data[0]);
        this.faceRectangle = new FaceRectangle();
        this.faceRectangle.width = Integer.parseInt(data[1]);
        this.faceRectangle.height = Integer.parseInt(data[2]);
        this.faceRectangle.left = Integer.parseInt(data[3]);
        this.faceRectangle.top = Integer.parseInt(data[4]);

        this.faceAttributes = null;
        this.faceLandmarks = null;

    }

    ParcelableFace(Face face) {
        this.faceId = face.faceId;
        this.faceRectangle = face.faceRectangle;
        this.faceAttributes = face.faceAttributes;
        this.faceLandmarks = face.faceLandmarks;
    }

    public static final Creator<ParcelableFace> CREATOR = new Creator<ParcelableFace>() {
        @Override
        public ParcelableFace createFromParcel(Parcel in) {
            return new ParcelableFace(in);
        }

        @Override
        public ParcelableFace[] newArray(int size) {
            return new ParcelableFace[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    //TODO: ianb - parcel FaceLandmarks, FaceAttributes if these are going to be used.
    public void writeToParcel(Parcel dest, int flags) {
        String[] out = new String[5];

        out[0] = faceId.toString();
        out[1] = String.valueOf(faceRectangle.width);
        out[2] = String.valueOf(faceRectangle.height);
        out[3] = String.valueOf(faceRectangle.left);
        out[4] = String.valueOf(faceRectangle.top);

        dest.writeStringArray(out);

    }
}
