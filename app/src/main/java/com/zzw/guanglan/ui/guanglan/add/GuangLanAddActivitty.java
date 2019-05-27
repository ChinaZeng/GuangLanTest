package com.zzw.guanglan.ui.guanglan.add;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.zzw.guanglan.R;
import com.zzw.guanglan.base.BaseActivity;
import com.zzw.guanglan.bean.AreaBean;
import com.zzw.guanglan.bean.GradeBean;
import com.zzw.guanglan.bean.JuZhanBean;
import com.zzw.guanglan.bean.ListDataBean;
import com.zzw.guanglan.bean.ResBean;
import com.zzw.guanglan.dialogs.BottomListDialog;
import com.zzw.guanglan.dialogs.area.AreaDialog;
import com.zzw.guanglan.dialogs.multilevel.OnConfirmCallback;
import com.zzw.guanglan.http.Api;
import com.zzw.guanglan.http.retrofit.RetrofitHttpEngine;
import com.zzw.guanglan.location.LocationManager;
import com.zzw.guanglan.manager.UserManager;
import com.zzw.guanglan.rx.ErrorObserver;
import com.zzw.guanglan.rx.LifeObservableTransformer;
import com.zzw.guanglan.rx.ResultBooleanFunction;
import com.zzw.guanglan.ui.resource.SelResActivity;
import com.zzw.guanglan.utils.ToastUtils;

import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.functions.Consumer;

/**
 * Created by zzw on 2018/10/13.
 * 描述:
 */
public class GuangLanAddActivitty extends BaseActivity implements LocationManager.OnLocationListener {


    @BindView(R.id.cabel_name)
    EditText cabelName;
    @BindView(R.id.area_id)
    TextView areaId;
    @BindView(R.id.capaticy)
    EditText capaticy;
    @BindView(R.id.et_long)
    EditText etLong;
    @BindView(R.id.leave)
    TextView leave;
    @BindView(R.id.a_station)
    TextView aStation;
    @BindView(R.id.z_station)
    TextView zStation;
    @BindView(R.id.tv_location)
    TextView tvLocation;
    private LocationManager locationManager;

    public static void open(Context context) {
        context.startActivity(new Intent(context, GuangLanAddActivitty.class));
    }

    @Override
    protected void initData() {
        super.initData();
        startLocation();
    }

    @Override
    protected int initLayoutId() {
        return R.layout.activity_guang_lan_add;
    }

    private String areaIdStr, leaveIdStr, aStationIdStr, zStationIdStr;


    public void submit() {
        final String cabelOpNameS = cabelName.getText().toString().trim();
        final String capaticyS = capaticy.getText().toString().trim();
        final String longS = etLong.getText().toString().trim();


        if (TextUtils.isEmpty(cabelOpNameS)) {
            ToastUtils.showToast("请填取光缆名称");
            return;
        }
        if (TextUtils.isEmpty(areaIdStr)) {
            ToastUtils.showToast("请选择地区");
            return;
        }

        if (TextUtils.isEmpty(cabelOpNameS)) {
            ToastUtils.showToast("请填取光缆名称");
            return;

        }
        if (TextUtils.isEmpty(leaveIdStr)) {
            ToastUtils.showToast("请选择光缆级别");
            return;
        }

        if (TextUtils.isEmpty(aStationIdStr)) {
            ToastUtils.showToast("请选择A端局站");
            return;
        }


        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .appAddGldInfo(new HashMap<String, String>() {
                    {
//                        put("userId", UserManager.getInstance().getUserId());
                        put("cableOpName", cabelOpNameS);
                        put("capaticy", capaticyS);
                        put("opLong", longS);
                        put("areaId", areaIdStr);
                        put("aRoomId", aStationIdStr);
                        put("zRoomId", zStationIdStr == null ? "" : zStationIdStr);
                        put("serialNo", leaveIdStr);
                    }
                })
                .map(ResultBooleanFunction.create())
                .compose(LifeObservableTransformer.<Boolean>create(this))
                .subscribe(new ErrorObserver<Boolean>(this) {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            ToastUtils.showToast("新增成功");
                            finish();
                        }
                    }
                });
    }

    private void area() {
        AreaDialog.createCityDialog(this, "选择地区", new OnConfirmCallback<AreaBean>() {
            @Override
            public void onConfirm(List<AreaBean> selectedEntities) {
                if (selectedEntities.size() > 0) {
                    AreaBean bean = selectedEntities.get(selectedEntities.size() - 1);
                    areaId.setText(bean.getText());
                    areaIdStr = bean.getId();
                }
            }
        }, true).show(getSupportFragmentManager(), "area");
    }

    private void leave() {
        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .quertListInfo()
                .compose(LifeObservableTransformer.<List<GradeBean>>create(this))
                .subscribe(new ErrorObserver<List<GradeBean>>(this) {
                    @Override
                    public void onNext(final List<GradeBean> data) {

                        if (data != null && data.size() > 0) {
                            BottomListDialog.newInstance(data, new BottomListDialog.Convert<GradeBean>() {
                                @Override
                                public String convert(GradeBean data) {
                                    return data.getDescChina();
                                }
                            }).setCallback(new BottomListDialog.Callback<GradeBean>() {
                                @Override
                                public boolean onSelected(GradeBean data, int position) {
                                    leave.setText(data.getDescChina());
                                    leaveIdStr = data.getSerialNo();
                                    return true;
                                }
                            }).show(getSupportFragmentManager(), "leave");
                        } else {
                            ToastUtils.showToast("数据为空");
                        }
                    }
                });
    }

    private void station(final int aOrz) {
        int code = aOrz == 0 ? 5 : 6;
        startActivityForResult(new Intent(this, SelResActivity.class), code);
//        RetrofitHttpEngine.obtainRetrofitService(Api.class)
//                .getAppJfAZInfo(areaIdStr, null, null)
//                .compose(LifeObservableTransformer.<ListDataBean<JuZhanBean>>create(this))
//                .subscribe(new ErrorObserver<ListDataBean<JuZhanBean>>(this) {
//                    @Override
//                    public void onNext(ListDataBean<JuZhanBean> list) {
//                        List<JuZhanBean> data = list.getList();
//
//                        if (data != null && data.size() > 0) {
//                            BottomListDialog.newInstance(data, new BottomListDialog.Convert<JuZhanBean>() {
//                                @Override
//                                public String convert(JuZhanBean data) {
//                                    return data.getROOM_NAME();
//                                }
//                            }).setCallback(new BottomListDialog.Callback<JuZhanBean>() {
//                                @Override
//                                public boolean onSelected(JuZhanBean data, int position) {
//                                    if (aOrz == 0) {
//                                        aStation.setText(data.getROOM_NAME());
//                                        aStationIdStr = data.getROOM_ID();
//                                    } else {
//                                        zStation.setText(data.getROOM_NAME());
//                                        zStationIdStr = data.getROOM_ID();
//                                    }
//                                    return true;
//                                }
//                            }).show(getSupportFragmentManager(), "juzhan");
//                        } else {
//                            ToastUtils.showToast("当前地区无局站");
//                        }
//                    }
//                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        ResBean resBean = (ResBean) data.getSerializableExtra("bean");
        if (requestCode == 5) {//a段
            aStation.setText(resBean.getRoomName());
            aStationIdStr = resBean.getRoomId();
        } else {//z端
            zStation.setText(resBean.getRoomName());
            zStationIdStr = resBean.getRoomId();
        }
    }

    @OnClick({R.id.tv_location,R.id.area_id, R.id.leave, R.id.a_station, R.id.z_station, R.id.add})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.tv_location:
                startLocation();
                break;
            case R.id.area_id:
                area();
                break;
            case R.id.leave:
                leave();
                break;
            case R.id.a_station:
                station(0);
                break;

            case R.id.z_station:
                station(1);
                break;
            case R.id.add:
                submit();
                break;
        }
    }

    @SuppressLint("CheckResult")
    private void startLocation() {
        tvLocation.setText("定位中...");
        new RxPermissions(this)
                .request(Manifest.permission.ACCESS_COARSE_LOCATION)
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            stopLocation();
                            locationManager = new LocationManager(GuangLanAddActivitty.this,
                                    GuangLanAddActivitty.this);
                            locationManager.start();
                        } else {
                            ToastUtils.showToast("请开启定位权限");
                            tvLocation.setText("定位失败，点击重新定位");
                        }
                    }
                });
    }

    private void stopLocation() {
        if (locationManager != null) {
            locationManager.stop();
            locationManager = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocation();
    }

    private LocationManager.LocationBean locationBean;

    @Override
    public void onSuccess(LocationManager.LocationBean bean) {
        tvLocation.setText(bean.addrss);
        this.locationBean = bean;
    }

    @Override
    public void onError(int code, String msg) {
        tvLocation.setText("定位失败，点击重新定位");
    }
}
