package com.zgreenmatting;

import android.app.Application;
import android.content.Context;

import com.android.volley.toolbox.Volley;
import com.zgreenmatting.utils.AppData;

/**
 * Created by czf on 2017/5/13.
 */

public class BaseApplication extends Application {
    public static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        AppData.init(mContext);
        Volley.init(mContext,false);//初始化volley
    }

}
