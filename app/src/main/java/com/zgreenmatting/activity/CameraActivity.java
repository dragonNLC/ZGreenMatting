package com.zgreenmatting.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.listener.Listener;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.igoda.dao.entity.MattingImage;
import com.igoda.dao.entity.TempImage;
import com.seu.magicfilter.MagicEngine;
import com.seu.magicfilter.filter.advanced.MagicAAFilter;
import com.seu.magicfilter.filter.base.gpuimage.GPUImageFilter;
import com.seu.magicfilter.filter.helper.MagicFilterType;
import com.seu.magicfilter.helper.SavePictureTask;
import com.seu.magicfilter.widget.MagicCameraView;
import com.seu.magicfilter.widget.base.MagicBaseView;
import com.zgreenmatting.BaseActivity;
import com.zgreenmatting.R;
import com.zgreenmatting.adapter.FilterAdapter;
import com.zgreenmatting.blservice.MattingImageService;
import com.zgreenmatting.download.DownloadManager;
import com.zgreenmatting.download.IDownloadStateListener;
import com.zgreenmatting.download.status.DownloadStatus;
import com.zgreenmatting.utils.AppData;
import com.zgreenmatting.utils.GetBigFileMD5;
import com.zgreenmatting.utils.JSONUtil;
import com.zgreenmatting.utils.NetworkUtils;
import com.zgreenmatting.utils.PhoneUtil;
import com.zgreenmatting.utils.RequestUtil;
import com.zgreenmatting.utils.ToastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.BindView;

public class CameraActivity extends BaseActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, FilterAdapter.onFilterChangeListener{

    //相机
    @BindView(R.id.cameraView)
    MagicCameraView cameraView;
    @BindView(R.id.btn_camera_mode)
    ImageView btn_camera_mode;//相机mode
    @BindView(R.id.btn_camera_switch)
    ImageView btn_camera_switch;//相机切换

    @BindView(R.id.btn_camera_beauty)
    SeekBar btn_camera_beauty;//透明度

    @BindView(R.id.btn_album)
    TextView btn_album;//相册
    @BindView(R.id.btn_camera_shutter)
    ImageView btn_camera_shutter;//拍照

    @BindView(R.id.btn_camera_filter)
    TextView btn_camera_filter;//背景

    @BindView(R.id.btn_camera_closefilter)
    ImageView btn_camera_closefilter;//关闭背景
    @BindView(R.id.new_layout_filter)
    LinearLayout mFilterLayout;//背景布局
    @BindView(R.id.filter_listView)
    RecyclerView mFilterListView;//背景列表
    private FilterAdapter mAdapter;
    private List<MattingImage> data;

    private MagicEngine magicEngine;
    private final int MODE_PIC = 1;
    private final int MODE_VIDEO = 2;
    private int mode = MODE_PIC;
    private ObjectAnimator animator;
    private SoundPool soundPool;
    private Map<Integer, Integer> soundMap;

    //下载
    IDownloadStateListener iDownloadStateListener;
    DownloadManager downloadManager;

    @Override
    protected int getContentLayout() {
        return R.layout.activity_camera;
    }

    /**
     * onCreate
     */
    @Override
    protected void preInitView() {
        //初始化相机
        MagicEngine.Builder builder = new MagicEngine.Builder();
        magicEngine = builder.build(cameraView);

        //初始化背景列表
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        mFilterListView.setLayoutManager(linearLayoutManager);
        data = new ArrayList<>();
        mAdapter = new FilterAdapter(this, data);
        mAdapter.setOnFilterChangeListener(this);
        mFilterListView.setAdapter(mAdapter);

        animator = ObjectAnimator.ofFloat(btn_camera_shutter,"rotation",0,360);
        animator.setDuration(500);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        Point screenSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) cameraView.getLayoutParams();
        params.width = screenSize.x;
        params.height = screenSize.x * 4 / 3;
        cameraView.setLayoutParams(params);

        btn_camera_filter.setOnClickListener(this);
        btn_camera_closefilter.setOnClickListener(this);
        btn_camera_shutter.setOnClickListener(this);
        btn_camera_switch.setOnClickListener(this);
        btn_camera_mode.setOnClickListener(this);
        btn_album.setOnClickListener(this);
        btn_camera_beauty.setOnSeekBarChangeListener(this);

        try {
            /**
             * 下载状态回调
             */
            iDownloadStateListener = new IDownloadStateListener() {
                @Override
                public void onPrepare(final Object entity, final long size) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            updateView(entity, DownloadStatus.WAIT);
                            //准备下载
                        }
                    });
                }

                @Override
                public void onProcess(final Object entity, final long size) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //updateView(entity, DownloadStatus.DLING);
                        }
                    });
                }

                @Override
                public void onFinish(final Object entity, final String savePath) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MattingImage data = (MattingImage) entity;
                            data.setSdPath(savePath);
                            updateView(entity, DownloadStatus.DONE);
                        }
                    });
                }

                @Override
                public void onFailed(final Object entity, final String msg) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            updateView(entity, DownloadStatus.ERROR);
                        }
                    });
                }

                @Override
                public void onPause(final Object entity, final long size) {
                    Log.i("tag", "onProcess " + entity.toString() + ",size=" + size);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateView(entity, DownloadStatus.PAUSE);
                        }
                    });
                }

                @Override
                public void onCancel(final Object entity) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateView(entity, DownloadStatus.NONE);
                        }
                    });
                }
            };
            downloadManager = DownloadManager.INSTANCE.init(CameraActivity.this);
            downloadManager.registerStateListener(iDownloadStateListener);
            downloadManager.onStart();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 更新item
     * @param entity
     * @param status
     */
    protected void updateView(Object entity, DownloadStatus status) {
        MattingImage dataItem = (MattingImage)entity;
        dataItem.setDownloadState(status.getValue());
        MattingImageService.getInstance().update(dataItem);
        int itemIdx = data.indexOf(entity);
        mAdapter.notifyItemChanged(itemIdx);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadManager != null) {
            downloadManager.unRegisterStateListener(iDownloadStateListener);
            downloadManager.onDestroy();
        }
    }

    /**
     * onResume
     */
    @Override
    protected void preInitData() {
        getBackdrops();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_camera_mode:
                switchMode();
                break;
            case R.id.btn_camera_shutter:
                //这个地方检查一下，本地如果超过15个未上传图片，提示用户打开网络进行上传操作
                int unuploadCount = MattingImageService.getInstance().getLocalUnuploadCount();
                if(unuploadCount>15){
                    ToastUtils.showCustomerToast(mContext,"请打开网络，上传本地已拍照照片");
                    return;
                }
                //如果本地有离线数据就立马上传一次
                if(NetworkUtils.isNetworkAvailable(mContext)&&unuploadCount>0){
                    uploadPicInfo();
                }
                if (PermissionChecker.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(CameraActivity.this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
                } else {
                    takePhoto();
                }
                break;
            case R.id.btn_camera_filter:
                showFilters();
                break;
            case R.id.btn_camera_switch:
                magicEngine.switchCamera();
                break;
            case R.id.btn_camera_closefilter:
                hideFilters();
                break;
            case R.id.btn_album:
                startActivity(new Intent(CameraActivity.this,GalleryActivity.class));
                break;
        }
    }

    private void initSP() throws Exception{
        //创建一个SoundPool对象，该对象可以容纳5个音频流
        AudioManager systemService = (AudioManager) CameraActivity.this.getSystemService(Context.AUDIO_SERVICE);
        //初始化soundPool 对象,第一个参数是允许有多少个声音流同时播放,第2个参数是声音类型,第三个参数是声音的品质
        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        soundMap=new HashMap<>();
        soundMap.put(1, soundPool.load(CameraActivity.this, R.raw.tackphoto, 1));
        int streamVolume = systemService.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        soundPool.play(soundMap.get(1), streamVolume, streamVolume, 1, 0, 1f);
    }

    private void switchMode(){
        if(mode == MODE_PIC){
            mode = MODE_VIDEO;
            btn_camera_mode.setImageResource(R.mipmap.icon_camera);
        }else{
            mode = MODE_PIC;
            btn_camera_mode.setImageResource(R.mipmap.icon_video);
        }
    }

    private void takePhoto(){
        try {
            initSP();
        } catch (Exception e) {
            e.printStackTrace();
        }
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "ZGreenMatting");
        Log.e("test","file : "+getOutputMediaFile());
        Log.e("test","file : "+mediaStorageDir.getPath() + File.separator);
        magicEngine.savePicture(getOutputMediaFile(), new SavePictureTask.OnPictureSaveListener() {
            @Override
            public void onSaved(String result) {
                //
                if(!TextUtils.isEmpty(result)){
                    saveTmpPicInfo(result);
                }
            }
        });
    }



    public File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "ZGreenMatting");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINESE).format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");

        return mediaFile;
    }
    private void showFilters(){
        ObjectAnimator animator = ObjectAnimator.ofFloat(mFilterLayout, "translationY", mFilterLayout.getHeight(), 0);
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                btn_camera_shutter.setClickable(false);
                mFilterLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {

            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }
        });
        animator.start();
    }

    private void hideFilters(){
        ObjectAnimator animator = ObjectAnimator.ofFloat(mFilterLayout, "translationY", 0 ,  mFilterLayout.getHeight());
        animator.setDuration(200);
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mFilterLayout.setVisibility(View.INVISIBLE);
                btn_camera_shutter.setClickable(true);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mFilterLayout.setVisibility(View.INVISIBLE);
                btn_camera_shutter.setClickable(true);
            }
        });
        animator.start();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == btn_camera_beauty){
            int params = btn_camera_beauty.getProgress();
            Log.e("params",params+"");
            MagicAAFilter filter = (MagicAAFilter)cameraView.getFilter();
            if(filter != null){
                float value = params/10f;
                Log.e("value",value+"");
                filter.setParams(value);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onFilterChanged() {
        magicEngine.setFilter();
    }

    @Override
    public void onChangePostion(final int position) {
        MagicAAFilter filter = (MagicAAFilter)cameraView.getFilter();
        if(filter != null) {
            if(position!=0){
                magicEngine.setFilterListener(new MagicBaseView.OnFilterChangedListener() {
                    @Override
                    public void filterChange(GPUImageFilter filter) {
                        if(filter instanceof MagicAAFilter){
                            MagicAAFilter aafilter =(MagicAAFilter) filter;
                            aafilter.setAsset(position+".jpg");
                        }
                    }
                });
                Log.e("photo:",position+".jpg");
            }
        }
        hideFilters();
    }

    //////////////////
    //获取背景数据
    private void getBackdrops(){
        StringRequest request = new StringRequest(Request.Method.POST, RequestUtil.backdrops, new Listener<String>() {
            @Override
            public void onSuccess(final String response) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //[{"value":"cc9a1852b09bd828ae2fefe3889dc44a","ext":"jpg","url":"http://tv.xxpost.com/camera/backdrop/cc9a1852b09bd828ae2fefe3889dc44a.jpg","createTime":"2017-05-19 16:27"}]
                            JSONArray data = new JSONArray(response);
                            List<MattingImage>  mattingImages = JSONUtil.toBeans(data,MattingImage.class);
                            for(MattingImage item : mattingImages){
                                //这里修改数据
                                MattingImageService.getInstance().save(item);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        getBackdropsFromLocal();
                    }
                }).start();
            }
            @Override
            public void onError(VolleyError error) {
                getBackdropsFromLocal();
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<String, String>();
                map.put("account", AppData.getString(mContext,AppData.ACCOUNT));
                map.put("device_id", PhoneUtil.getDevicesID(mContext));
                return map;
            }
        };
        Volley.getRequestQueue().add(request);
    }

    private void getBackdropsFromLocal() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                data.clear();
                data.addAll(MattingImageService.getInstance().getList());
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    private void uploadPicInfo() {
        final TempImage tempImage = MattingImageService.getInstance().getNextTmpImage();
        if(NetworkUtils.isNetworkAvailable(mContext)&&tempImage!=null){
            StringRequest request = new StringRequest(Request.Method.POST, RequestUtil.sendImageInfo, new Listener<String>() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject obj = new JSONObject(response);
                        if (obj.getInt("errCode") ==1) {
                            MattingImageService.getInstance().deleteTmpImage(tempImage);
                        }else {
                            ToastUtils.showSystemToast(mContext,obj.getString("desc"));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onError(VolleyError error) {
                }
            }){
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("account", AppData.getString(mContext,AppData.ACCOUNT));
                    map.put("value", tempImage.getValue());
                    map.put("device_id", PhoneUtil.getDevicesID(mContext));
                    return map;
                }
            };
            Volley.getRequestQueue().add(request);
        }
    }
    //先把数据保存到本地,然后再上传
    private void saveTmpPicInfo(String picPath) {
        File file = new File(picPath);
        if(file.exists()){
            String hash = GetBigFileMD5.getMD5(file);
            MattingImageService.getInstance().saveTmpImage(picPath,hash);
            uploadPicInfo();
        }
    }
}