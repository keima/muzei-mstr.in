package net.pside.android.muzei.mstr;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;

import java.util.Random;

import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

import static net.pside.android.muzei.mstr.MstrInService.Photo;
import static net.pside.android.muzei.mstr.MstrInService.PhotoResponse;

/**
 * Created by Kouta on 14/02/23.
 */
public class MstrInArtSource extends RemoteMuzeiArtSource {
    private static final String TAG = "MstrInArtSource";
    private static final String SOURCE_NAME = "MstrInArtSource";

    private static final int USER_COMMAND_SHARE = 1234;

    private static final int ROTATE_TIME_MILLIS = 3 * 60 * 60 * 1000; // rotate every 3 hours

    public MstrInArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        UserCommand shareUserCommand = new UserCommand(USER_COMMAND_SHARE, "Share Artwork");

        setUserCommands(
                new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK),
                shareUserCommand);
    }

    @Override
    protected void onCustomCommand(int id) {
        switch(id){
            case USER_COMMAND_SHARE:
                Photo photo = new Photo();
                photo.uid = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

                if (photo.uid == null) {
                    Toast.makeText(this,"uid error...", Toast.LENGTH_SHORT).show();
                    return;
                }

                String message = " " + photo.getStaticPageUrl();

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(intent, "Send Artwork to...").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                break;
        }
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("https://mstr.in/api")
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError retrofitError) {
                        int statusCode = retrofitError.getResponse().getStatus();
                        if (retrofitError.isNetworkError()
                                || (500 <= statusCode && statusCode < 600)) {
                            return new RetryException();
                        }
                        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                        return retrofitError;
                    }
                })
                .build();

        MstrInService service = restAdapter.create(MstrInService.class);
        Log.i(TAG, "Request latest Photo List...");
        PhotoResponse response = service.getLatestPhotos();
        Log.i(TAG, "Finish: Request latest Photo List...");

        if (response == null || response.photos == null) {
            Log.w(TAG, "Response is null.");
            throw new RetryException();
        }

        if (response.photos.size() == 0) {
            Log.w(TAG, "No photos returned from API.");
            scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
            return;
        }

        Random random = new Random();
        Photo photo;
        String token;
        while (true) {
            photo = response.photos.get(random.nextInt(response.photos.size()));
            token = photo.uid;
            Log.i(TAG,"currentToken: " + currentToken + " / token: " + token);
            if (response.photos.size() <= 1 || !TextUtils.equals(token, currentToken)) {
                break;
            }
        }

        Log.i(TAG, "Publish Artwork...");
        publishArtwork(new Artwork.Builder()
                .title("ImageID: " + photo.id)
                .imageUri(Uri.parse(photo.getPhotoUrl()))
                .token(token)
                .viewIntent(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://mstr.in/photos/" + photo.uid)))
                .build());

        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
    }
}
