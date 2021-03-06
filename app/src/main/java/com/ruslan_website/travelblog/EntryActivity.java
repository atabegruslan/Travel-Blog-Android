package com.ruslan_website.travelblog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ActionBarOverlayLayout;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.FacebookException;
import com.facebook.share.Sharer;
import com.ruslan_website.travelblog.utils.TravelBlogApplication;
import com.ruslan_website.travelblog.utils.common.Auth;
import com.ruslan_website.travelblog.utils.common.Image;
import com.ruslan_website.travelblog.utils.common.Network;
import com.ruslan_website.travelblog.utils.common.PathCombiner;
import com.ruslan_website.travelblog.utils.common.UI;
import com.ruslan_website.travelblog.utils.database.EntryDAO;
import com.ruslan_website.travelblog.utils.http.api.APIFactory;
import com.ruslan_website.travelblog.utils.http.api.APIStrategy;
import com.ruslan_website.travelblog.utils.http.model.Entry;
import com.ruslan_website.travelblog.utils.http.service.EntryService;
import com.ruslan_website.travelblog.utils.http.service.ImageService;
import com.ruslan_website.travelblog.utils.http.service.TokenService;
import com.ruslan_website.travelblog.utils.social.facebook.Facebook;
import com.ruslan_website.travelblog.utils.social.facebook.FacebookShareCallbackInterface;
import com.ruslan_website.travelblog.utils.social.facebook.Share;
import com.ruslan_website.travelblog.utils.social.google.Google;
import com.ruslan_website.travelblog.utils.storage.SharedPreferencesManagement;
import com.ruslan_website.travelblog.utils.view.SwiperAdapter;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EntryActivity extends AppCompatActivity {

    private SharedPreferencesManagement mSPM;
    private static final int OBTAIN_IMG_BYTE_CODE = 4000;
    private EntryService entryService;
    private ImageService imageService;
    @BindView(R.id.bNewEntry) Button bNewEntry;
    @BindView(R.id.bRefresh) Button bRefresh;
    @BindView(R.id.bContact) Button bContact;
    @BindView(R.id.bLogout) Button bLogout;
    @BindView(R.id.fbShareLink) ImageButton fbShareLink;
    @BindView(R.id.greet) TextView greet;
    @BindView(R.id.entries) LinearLayout entries;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.swiper) ViewPager swiper;
    private Button[] changingButtons;

    APIFactory apiFactory;
    APIStrategy apiStrategy;

    private Google google;
    private Facebook facebook;

    private EntryDAO db;
    private int entryTableKey = 0;

    private SwiperAdapter swiperAdapter;

    private Share fbShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    private void init() {

        ButterKnife.bind(this);

        changingButtons = new Button[]{bRefresh, bLogout, bContact};

        if (mSPM == null) {
            mSPM = SharedPreferencesManagement.getInstance();
        }

        if(mSPM.getAccessToken() == null){
            Intent intent = new Intent(EntryActivity.this, LoginActivity.class);
            startActivity(intent);
        }

        if(facebook == null){
            facebook = Facebook.getInstance();
            facebook.init(EntryActivity.this);
        }
        if(google == null){
            google = Google.getInstance();
        }

        apiFactory = new APIFactory( mSPM.getBackendOption() );
        apiStrategy = apiFactory.getApiStrategy();

        greet.setText("Hi " + mSPM.getUsername());
        greet.setTextSize(30);
        greet.setGravity(Gravity.CENTER);
        greet.setTypeface(null, Typeface.BOLD);

        if(!mSPM.getLoginChannel().equals("facebook")){
            fbShareLink.setVisibility(View.GONE);
        }
        if (fbShare == null) {
            fbShare = Share.getInstance();
        }

        if(Network.isConnected()) {
            if (entries.getChildCount() > 0) entries.removeAllViews();
            obtainEntry();
        }
    }

    private void obtainEntry() {

        String toast = "Loading content";
        String log = "Loading entries";
        UI.setProgressStatus(EntryActivity.this, true, progressBar, changingButtons, toast, log);

        Call<List<Entry>> entryRequest = apiStrategy.obtainEntry( mSPM.getAccessToken() );

        entryRequest.enqueue(new Callback<List<Entry>>(){
            @Override
            public void onResponse(Call<List<Entry>> call, Response<List<Entry>> response) {
                obtainImage(response);
            }
            @Override
            public void onFailure(Call<List<Entry>> call, Throwable t) {
                String toast = "Update your app. App will close.";
                String log = "Entry - Error: " + t.getMessage();
                UI.setProgressStatus(EntryActivity.this, false, progressBar, changingButtons, toast, log);
            }
        });

    }

    private void obtainImage(Response<List<Entry>> response) {

        for (final Entry entry: response.body()) {

            String[] imgUrlParts = entry.getImg_url().split("/");
            final String imageName = imgUrlParts[imgUrlParts.length - 1];

            Call<ResponseBody> imageRequest = apiStrategy.obtainImage(imageName);

            imageRequest.enqueue(new Callback<ResponseBody>(){
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        makeEntryList(response.body(), imageName, entry);

                        swiperAdapter = new SwiperAdapter(EntryActivity.this, db);
                        swiper.setAdapter(swiperAdapter);
                    } catch (Exception e) {
                        String toast = "Update your app. App will close.";
                        String log = "makeEntryList - Error: " + e.getMessage();
                        UI.setProgressStatus(EntryActivity.this, false, progressBar, changingButtons, toast, log);
                        e.printStackTrace();
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    String toast = "Update your app. App will close.";
                    String log = "Download Image Error: " + t.getMessage();
                    t.printStackTrace();
                    UI.setProgressStatus(EntryActivity.this, false, progressBar, changingButtons, toast, log);
                }
            });

        }

        String toast = "All contents loaded.";
        String log = "All contents loaded";
        UI.setProgressStatus(EntryActivity.this, false, progressBar, changingButtons, toast, log);
    }

    private void makeEntryList(ResponseBody body, String imgName, Entry entry) {

        downloadImage(body, imgName);

        LinearLayout ll = new LinearLayout(EntryActivity.this);
        ll.setOrientation(LinearLayout.VERTICAL);

        TextView line1 = new TextView(EntryActivity.this);
        line1.setTextSize(20);
        line1.setGravity(Gravity.CENTER);
        line1.setTypeface(null, Typeface.BOLD);
        line1.setText(entry.getPlace());
        ll.addView(line1);

        TextView line2 = new TextView(EntryActivity.this);
        line2.setTextSize(15);
        line2.setGravity(Gravity.CENTER);
        line2.setTypeface(null, Typeface.ITALIC);
        line2.setText("By " + entry.getUser().getName() + ", " + entry.getTime());
        ll.addView(line2);

        TextView line3 = new TextView(EntryActivity.this);
        line3.setTextSize(14);
        line3.setGravity(Gravity.CENTER);
        line3.setText(entry.getComments());
        ll.addView(line3);

        ImageView imageView = new ImageView(EntryActivity.this);
        String path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath();
        Log.i("ReadImgPath", path);
        File imgFile = new File(path, imgName);
        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
        imageView.setImageBitmap(bitmap);
        imageView.setLayoutParams(new ActionBarOverlayLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        imageView.setPadding(0, 0, 0, 100);
        ll.addView(imageView);

        db = new EntryDAO(this);
        db.addEntry(new com.ruslan_website.travelblog.utils.database.Entry(
                entryTableKey,
                entry.getUser().getName(),
                entry.getTime(),
                entry.getPlace(),
                entry.getComments(),
                imgFile.getAbsolutePath()
        ));
        entryTableKey++;

        entries.addView(ll);
    }

    private void downloadImage(ResponseBody body, String imgName) {
        File path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        InputStream is = body.byteStream();
        Image.save(path, imgName, is);
    }

    @OnClick(R.id.bNewEntry)
    public void newEntry(View view){
        Intent intent = new Intent(EntryActivity.this, NewEntryActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.bRefresh)
    public void refresh(View view){
        init();
    }

    @OnClick(R.id.bLogout)
    public void logout(View view){
        Auth.logout(mSPM, facebook, google, EntryActivity.this);
    }

    @OnClick(R.id.bContact)
    public void toContactPage(View view){
        Intent intent = new Intent(EntryActivity.this, ContactActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.fbShareLink)
    public void fbShareLink(View view){
        fbShare.shareLink("http://ruslan-website.com/laravel/travel_blog/", EntryActivity.this, new FacebookShareCallbackInterface(){
            @Override
            public void onError(String msg, FacebookException error) {
                Toast.makeText(EntryActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onCancel(String msg) {
                Toast.makeText(EntryActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onSuccess(String msg, Sharer.Result result) {
                Toast.makeText(EntryActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
