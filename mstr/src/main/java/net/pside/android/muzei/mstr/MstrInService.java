package net.pside.android.muzei.mstr;

import java.util.List;

import retrofit.http.GET;

/**
 * Created by Kouta on 14/02/24.
 */
interface MstrInService {
    @GET("/photos.json")
    PhotoResponse getLatestPhotos();

    static class PhotoResponse {
        List<Photo> photos;
    }

    static class Photo {
        long id;
        String uid;

        public String getPhotoUrl(){
            return "https://pic.mstr.in/images/" + uid + ".jpg";
        }
    }
}
