package com.ruslan_website.travelblog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ruslan_website.travelblog.utils.common.Image;
import com.ruslan_website.travelblog.utils.common.UI;
import com.ruslan_website.travelblog.utils.http.api.APIFactory;
import com.ruslan_website.travelblog.utils.http.api.APIStrategy;
import com.ruslan_website.travelblog.utils.storage.SharedPreferencesManagement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewEntryActivity extends AppCompatActivity {

    private SharedPreferencesManagement mSPM;
    private static final int REQUEST_CAMERA = 5000;
    private static final int SELECT_FILE = 5001;
    private static final int VOICE_CODE = 123;
    private static final int QR_CODE = 1234;
    String filePath;
    @BindView(R.id.name) TextView name;
    @BindView(R.id.place) EditText place;
    @BindView(R.id.comments) EditText comments;
    @BindView(R.id.bImage) ImageButton bImage;
    @BindView(R.id.bSubmit) Button bSubmit;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.bVoice) ImageButton bVoice;
    @BindView(R.id.bMap) ImageButton bMap;
    @BindView(R.id.bQr) ImageButton bQr;
    private Button[] changingButtons;
    private APIFactory apiFactory;
    private APIStrategy apiStrategy;
    private String currentMapLocality;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_entry);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                currentMapLocality = null;
            } else {
                currentMapLocality = extras.getString("currentLocality");
            }
        } else {
            currentMapLocality = (String) savedInstanceState.getSerializable("currentLocality");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    private void init() {

        ButterKnife.bind(this);
        changingButtons = new Button[]{bSubmit};

        if (mSPM == null) {
            mSPM = SharedPreferencesManagement.getInstance();
        }

        if(mSPM.getAccessToken() == null){
            Intent intent = new Intent(NewEntryActivity.this, LoginActivity.class);
            startActivity(intent);
        }

        apiFactory = new APIFactory( mSPM.getBackendOption() );
        apiStrategy = apiFactory.getApiStrategy();

        name.setText(mSPM.getUsername());
        name.setTextSize(20);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(null, Typeface.BOLD);

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            bQr.setVisibility(View.GONE);
        }else{
            bQr.setVisibility(View.VISIBLE);
        }

        if(currentMapLocality != null){
            place.setText(currentMapLocality);
        }
    }

    @OnClick(R.id.bImage)
    public void selectImage(View view){
        selectImage();
    }

    // Pick image or take photo tutorial:
    // http://www.theappguruz.com/blog/android-take-photo-camera-gallery-code-sample
    private void selectImage() {

        final CharSequence[] items = { "Take Photo", "Choose from Library", "Cancel" };
        AlertDialog.Builder builder = new AlertDialog.Builder(NewEntryActivity.this);
        builder.setTitle("Add Photo!");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals("Take Photo")) {
                    cameraIntent();
                } else if (items[item].equals("Choose from Library")) {
                    galleryIntent();
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    private void cameraIntent(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    private void galleryIntent(){
        Intent intent = new Intent();

        // select only images from the media storage.
        // Tip:  if you want to get images as well as videos,
        // you can use following code : intent.setType("image/* video/*");
        intent.setType("image/*");

        intent.setAction(Intent.ACTION_GET_CONTENT);

        // calling an implicit intent to open the gallery
        // and then calling startActivityForResult() and passing the intent.
        startActivityForResult(Intent.createChooser(intent, "Select File"), SELECT_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The requestCode is what you have passed to startActivityForResult().
        // Either REQUEST_CAMERA or SELECT_FILE

        // resultCode :
        // RESULT_OK if the operation was successful or
        // RESULT_CANCEL if the operation was somehow cancelled or unsuccessful.

        // The intent data carries the result data
        // Either the image we have captured from the camera,
        // or the image we selected from gallery.
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_FILE) {
                onSelectFromGalleryResult(data);
            }else if (requestCode == REQUEST_CAMERA) {
                onCaptureImageResult(data);
            }else if (requestCode == VOICE_CODE){
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if(!results.isEmpty()) {
                    place.setText(results.get(0));
                }
            }else if (requestCode == QR_CODE){
                if (resultCode == RESULT_OK) {
                    String qrResults = data.getStringExtra("SCAN_RESULT");
                    place.setText(qrResults);
                }
            }
        }
    }

    // On selecting image from gallery, onSelectFromGalleryResult(data) will be called
    @SuppressWarnings("deprecation")
    private void onSelectFromGalleryResult(Intent data) {
        Bitmap bm=null;
        if (data != null) {
            try {

                // Android saves the images in its own database,
                // we can fetch it using different ways. Here we use MediaStore.Images.Media.getBitmap().
                // Fetching image from specific path( data.getData() ) as Bitmap by calling getBitmap() method.
                android.net.Uri selectedImage = data.getData();
                bm = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), selectedImage);

                saveImage(bm);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void onCaptureImageResult(Intent data) {

        // We get our data in our Intent, so we are first getting that data through data.getsExtras().get("data")
        // and then we are casting that data into Bitmap since we want it as an image.
        Bitmap thumbnail = (Bitmap) data.getExtras().get("data");

        saveImage(thumbnail);
    }

    private void saveImage(Bitmap bitmap){
        bitmap = Image.cropToSquare(bitmap);
        bitmap = Image.scaleDown(bitmap);
        bitmap = Image.cropToCircle(bitmap);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        // We want to make thumbnail of an image,
        // so we need to first take the ByteArrayOutputStream and than pass it into thumbnail.compress() method.
        bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/,  bytes);

        // Convert Bitmap to InputStream
        byte[] bitmapdata = bytes.toByteArray();
        ByteArrayInputStream is = new ByteArrayInputStream(bitmapdata);

        File photoPath = new File(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp");
        String photoName = System.currentTimeMillis() + ".png";
        File destination = new File(photoPath, photoName);

        Image.save(photoPath, photoName, is);
        bImage.setImageBitmap(bitmap);
        filePath = destination.getPath();
    }

    @OnClick(R.id.bMap)
    public void toMap(View view){
        Intent intent = new Intent(NewEntryActivity.this, MapActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.bVoice)
    public void voiceInput(View view){
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak Now");
        startActivityForResult(i, VOICE_CODE);
    }

    @OnClick(R.id.bQr)
    public void qrInput(View view){
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            startActivityForResult(intent, QR_CODE);
        } catch (Exception e) {
            Uri marketUri = Uri.parse("market://details?id=com.google.zxing.client.android");
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            startActivity(marketIntent);
            e.printStackTrace();
        }
    }

    @OnClick(R.id.bSubmit)
    public void submit(View view){

        if( place.getText().toString().length() == 0 ){
            place.setError( "Place is required!" );
            return;
        }
        if( comments.getText().toString().length() == 0 ){
            comments.setError( "Comments is required!" );
            return;
        }
        if( bImage.getDrawable() == null ){
            Toast.makeText(NewEntryActivity.this, "Must Pick an Image", Toast.LENGTH_SHORT).show();
            return;
        }

        String toast = "Submitting ...";
        String log = "Submitting";
        UI.setProgressStatus(NewEntryActivity.this, true, progressBar, changingButtons, toast, log);

        File file = new File(filePath);
        RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part image = MultipartBody.Part.createFormData("image", file.getName(), reqFile);
        RequestBody userId = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(mSPM.getUserId()) );
        RequestBody place1 = RequestBody.create(MediaType.parse("text/plain"), place.getText().toString() );
        RequestBody comments1 = RequestBody.create(MediaType.parse("text/plain"), comments.getText().toString() );

        Call<ResponseBody> newEntryRequest = apiStrategy.uploadEntry(mSPM.getAccessToken(), image, userId, place1, comments1);

        newEntryRequest.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                String toast = "Submission Success.";
                String log = "Create New Success: " + response.message();
                UI.setProgressStatus(NewEntryActivity.this, false, progressBar, changingButtons, toast, log);
                back();
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                String toast = "Submission Failure. Update your app.";
                String log = "Create New Fail: " + t.getMessage();
                UI.setProgressStatus(NewEntryActivity.this, false, progressBar, changingButtons, toast, log);
                t.printStackTrace();
                back();
            }
        });
    }

    private void back(){
        Intent intent = new Intent(NewEntryActivity.this, EntryActivity.class);
        startActivity(intent);
    }
}
