package com.zzw.guanglan.ui.qianxin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.dl7.tag.TagLayout;
import com.dl7.tag.TagView;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.zzw.guanglan.BuildConfig;
import com.zzw.guanglan.Contacts;
import com.zzw.guanglan.R;
import com.zzw.guanglan.base.BaseActivity;
import com.zzw.guanglan.base.WebActivity;
import com.zzw.guanglan.bean.GuangLanDItemBean;
import com.zzw.guanglan.bean.ListDataBean;
import com.zzw.guanglan.bean.QianXinItemBean;
import com.zzw.guanglan.bean.RemoveBean;
import com.zzw.guanglan.bean.SingleChooseBean;
import com.zzw.guanglan.bean.StatusInfoBean;
import com.zzw.guanglan.dialogs.BottomActionListDialog;
import com.zzw.guanglan.dialogs.BottomListDialog;
import com.zzw.guanglan.http.Api;
import com.zzw.guanglan.http.retrofit.RetrofitHttpEngine;
import com.zzw.guanglan.location.GPSUtils;
import com.zzw.guanglan.location.LocationManager;
import com.zzw.guanglan.manager.UserManager;
import com.zzw.guanglan.rx.ErrorObserver;
import com.zzw.guanglan.rx.LifeObservableTransformer;
import com.zzw.guanglan.rx.ResultBooleanFunction;
import com.zzw.guanglan.service.SocketService;
import com.zzw.guanglan.socket.CMD;
import com.zzw.guanglan.socket.EventBusTag;
import com.zzw.guanglan.socket.event.SorFileBean;
import com.zzw.guanglan.socket.event.TestArgsAndStartBean;
import com.zzw.guanglan.socket.resolve.Packet;
import com.zzw.guanglan.socket.utils.ByteUtil;
import com.zzw.guanglan.socket.utils.FileHelper;
import com.zzw.guanglan.socket.utils.MyLog;
import com.zzw.guanglan.ui.HotConnActivity;
import com.zzw.guanglan.ui.room.add.RoomAddActivity;
import com.zzw.guanglan.utils.DataUtils;
import com.zzw.guanglan.utils.RealPathFromUriUtils;
import com.zzw.guanglan.utils.RequestBodyUtils;
import com.zzw.guanglan.utils.SPUtil;
import com.zzw.guanglan.utils.ToastUtils;

import org.simple.eventbus.EventBus;
import org.simple.eventbus.Subscriber;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import butterknife.BindView;
import io.reactivex.functions.Consumer;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class QianXinListActivity extends BaseActivity implements
        BaseQuickAdapter.RequestLoadMoreListener,
        SwipeRefreshLayout.OnRefreshListener,
        QianXinListAdapter.OnTestListener,
        QianXinListAdapter.OnUploadListener,
        QianXinListAdapter.OnStatusListener,
        LocationManager.OnLocationListener {
    @BindView(R.id.recy)
    RecyclerView recy;

    @BindView(R.id.ll)
    LinearLayout ll;

    TextView tvLocation;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout refreshLayout;

    //必须先上传图片才能进行测试
    private boolean isUploadImg = false;
    private int pageNo = 1;

    private final static String ITEM = "item";
    private final static int GALLERY_REQUEST_CODE = 155;
    private final static int CAMERA_REQUEST_CODE = 156;


    private QianXinListAdapter adapter;

    private GuangLanDItemBean guangLanDBean;
    private LocationManager.LocationBean locationBean;
    private LocationManager locationManager;


    private List<SingleChooseBean> juliS;
    private List<SingleChooseBean> bochangS;
    private List<SingleChooseBean> timeS;
    private List<SingleChooseBean> zheshelvS;
    private List<SingleChooseBean> modeS;
    private List<SingleChooseBean> maiKuanS;

    private TestArgsAndStartBean testArgsCustomModeBean;
    private TestArgsAndStartBean testArgsLastModeBean;
    private TestArgsAndStartBean testArgsAutoModeBean;


    public static void open(Context context, GuangLanDItemBean bean) {
        context.startActivity(new Intent(context, QianXinListActivity.class)
                .putExtra(ITEM, bean)
        );
    }

    @Override
    protected int initLayoutId() {
        return R.layout.activity_qian_xin_list;
    }

    @Override
    protected void initData() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.WRITE_SETTINGS}, 5);

        super.initData();
        guangLanDBean = (GuangLanDItemBean) getIntent().getSerializableExtra(ITEM);

        headerView();

        recy.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QianXinListAdapter(new ArrayList<QianXinItemBean>());
        adapter.setEnableLoadMore(true);
        adapter.setOnLoadMoreListener(this, recy);
        adapter.setOnTestListener(this);
        adapter.setOnUploadListener(this);
        adapter.setOnStatusListener(this);
        recy.setAdapter(adapter);
        refreshLayout.setOnRefreshListener(this);

        onRefresh();
    }

    private boolean compareDistance(QianXinItemBean bean, int val) {
        boolean aOk = true;
        boolean zOk = true;
        if (!TextUtils.isEmpty(bean.getAGEOX()) && !TextUtils.isEmpty(bean.getAGEOY())) {
            if (GPSUtils.getDistance(locationBean.latitude, locationBean.longitude
                    , Double.parseDouble(bean.getAGEOY()), Double.parseDouble(bean.getAGEOX())) > val) {
                aOk = false;
            }
        }
        if (!TextUtils.isEmpty(bean.getZGEOX()) && !TextUtils.isEmpty(bean.getZGEOY())) {
            if (GPSUtils.getDistance(locationBean.latitude, locationBean.longitude
                    , Double.parseDouble(bean.getZGEOY()), Double.parseDouble(bean.getZGEOX())) > val) {

                zOk = false;
            }
        }
        return aOk || zOk;
    }

    void getData() {
        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .getAppFiberListByPage(new HashMap<String, String>() {
                    {
                        put("cblOpName", guangLanDBean.getCABL_OP_NAME());
                        put("cblOpCode", "");
                        put("pageNum", String.valueOf(pageNo));
                    }
                })
                .compose(LifeObservableTransformer.<ListDataBean<QianXinItemBean>>create(this))
                .subscribe(new ErrorObserver<ListDataBean<QianXinItemBean>>(this) {
                    @Override
                    public void onNext(ListDataBean<QianXinItemBean> qianXinItemBeans) {
                        if (qianXinItemBeans.getList() != null && qianXinItemBeans.getList().size() > 0) {
                            setData(qianXinItemBeans.getList());
                            if (adapter.getData().size() >= qianXinItemBeans.getTotal()) {
                                adapter.loadMoreEnd();
                            } else {
                                adapter.loadMoreComplete();
                            }
                        }
                        refreshLayout.setRefreshing(false);
                    }
                });
    }

    //检测重复信息
    void look() {
        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .remove(guangLanDBean.getID())
                .compose(LifeObservableTransformer.<RemoveBean>create(this))
                .subscribe(new ErrorObserver<RemoveBean>(this) {
                    @Override
                    public void onNext(RemoveBean removeBean) {
                        List<RemoveBean.RemoveObjBean> remove = removeBean.getRemove();
                        List<RemoveBean.RemoveObjBean> error = removeBean.getAbnormal();

                        if ((remove == null || remove.size() == 0) && (error == null || error.size() == 0)) {
                            ToastUtils.showToast("无纤芯重复和异常信息");
                            return;
                        }

                        if (adapter != null) {
                            if (remove != null) {
                                HashSet<String> arginTestIds = new HashSet<>();
                                for (RemoveBean.RemoveObjBean removeObjBean : remove) {
                                    arginTestIds.add(removeObjBean.getFiberId());
                                    adapter.setArginData(arginTestIds);
                                }
                            }
                            if (error != null) {
                                HashSet<String> errorDataIds = new HashSet<>();
                                for (RemoveBean.RemoveObjBean bean : error) {
                                    errorDataIds.add(bean.getFiberId());
                                    adapter.setExceptionData(errorDataIds);
                                }
                            }
                            adapter.notifyDataSetChanged();
                        }

                        List<RemoveBean.RemoveObjBean> da = new ArrayList<>();
                        if (remove != null) {
                            da.addAll(remove);
                        }
                        if (error != null) {
                            da.addAll(error);
                        }

                        final BottomActionListDialog<RemoveBean.RemoveObjBean> dialog = BottomActionListDialog.newInstance(da, new BottomActionListDialog.Convert<RemoveBean.RemoveObjBean>() {
                            @Override
                            public String convert(RemoveBean.RemoveObjBean data) {
                                return data.getText();
                            }
                        });
                        dialog.setTitle("温馨提示");
                        dialog.setRightTitle("关闭");
                        dialog.setItemActionName("重新测试");
                        dialog.setCallback(new BottomActionListDialog.Callback<RemoveBean.RemoveObjBean>() {
                            @Override
                            public boolean onSelected(RemoveBean.RemoveObjBean data, int position) {
                                return true;
                            }

                            @Override
                            public boolean onClickAction(RemoveBean.RemoveObjBean data, int position) {
                                if (adapter != null) {
                                    List<QianXinItemBean> qianXinItemBeans = adapter.getData();
                                    QianXinItemBean testQianXinItemBean = null;
                                    for (QianXinItemBean qianXinItemBean : qianXinItemBeans) {
                                        if (TextUtils.equals(data.getFiberId(), qianXinItemBean.getFIBER_ID())) {
                                            testQianXinItemBean = qianXinItemBean;
                                            break;
                                        }
                                    }
                                    if (testQianXinItemBean != null) {
                                        onTest(testQianXinItemBean);
                                    }
                                }
                                return true;
                            }

                            @Override
                            public void onRightClick() {
                                dialog.dismiss();
                            }
                        });
                        dialog.show(getSupportFragmentManager(), "remove");
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        refreshLayout.setRefreshing(false);
                    }
                });
    }


    private File tempFile;

    private void takePhoto() {
        Intent openCameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        String f = System.currentTimeMillis() + ".jpg";
        tempFile = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + f);
        Uri uri = FileProvider.getUriForFile(this, "com.zzw.guanglan.fileProvider", tempFile);
        openCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri); //指定图片存放位置，指定后，在onActivityResult里得到的Data将为null
        startActivityForResult(openCameraIntent, CAMERA_REQUEST_CODE);
    }

    private void choosePhoto() {

        // 激活系统图库，选择一张图片
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivityForResult(intent, GALLERY_REQUEST_CODE);


//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), GALLERY_REQUEST_CODE);
//        } else {
//            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
//            intent.addCategory(Intent.CATEGORY_OPENABLE);
//            intent.setType("image/*");
//            startActivityForResult(intent, GALLERY_REQUEST_CODE);
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;
        if (requestCode == GALLERY_REQUEST_CODE) {
            if (data != null) {
                String realPathFromUri = RealPathFromUriUtils.getRealPathFromUri(this, data.getData());
                uploadImg(new File(realPathFromUri));
            } else {
                ToastUtils.showToast("图片损坏，请重新选择");
            }

        } else if (requestCode == CAMERA_REQUEST_CODE) {
            if (tempFile != null) {
                uploadImg(tempFile);
            } else {
                ToastUtils.showToast("未知异常");
            }

        }
    }

    //上传实地照片
    void uploadImg(File file) {

        if (progressDialog == null) {
            initProgress();
        }
        progressDialog.setTitle("正在上传图片");
        progressDialog.show();


        final File aFile = file;
        RequestBody aRequestFile =
                RequestBody.create(MediaType.parse("multipart/form-data"), aFile);
        String aName = aFile.getName();
        MultipartBody.Part aFileBody =
                MultipartBody.Part.createFormData("aFile", aName, aRequestFile);

//        final File zFile = new File(aFilePath);
//        RequestBody zRequestFile =
//                RequestBody.create(MediaType.parse("multipart/form-data"), aFile);
//        String zName = zFile.getName();
//        MultipartBody.Part zFileBody =
//                MultipartBody.Part.createFormData("zFile", zName, zRequestFile);


        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .saveImgFile(RequestBodyUtils.generateRequestBody(new HashMap<String, String>() {
                    {
                        put("objectName", "光缆段");//拍照上传类型如”光缆段”，“机房”等
                        put("notes", "");//备注
                        put("userId", UserManager.getInstance().getUserId());
                        put("objectId", guangLanDBean.getID());
                    }
                }), aFileBody, null)
                .map(ResultBooleanFunction.create())
                .compose(LifeObservableTransformer.<Boolean>create(this))
                .subscribe(new ErrorObserver<Boolean>(this) {
                    @Override
                    public void onNext(Boolean bo) {
                        if (bo) {
                            ToastUtils.showToast("上传成功");
                            isUploadImg = true;
                        } else {
                            ToastUtils.showToast("上传失败");
                        }
                        progressDialog.dismiss();
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        progressDialog.dismiss();
                    }
                });
    }

    void setData(List<QianXinItemBean> datas) {
        if (pageNo == 1) {
            adapter.replaceData(datas);
        } else {
            adapter.addData(datas);
        }
    }

    @Override
    public void onLoadMoreRequested() {
        pageNo++;
        getData();
    }

    @Override
    public void onRefresh() {
        pageNo = 1;
        getData();
    }

    private QianXinItemBean testBean;

    @Override
    public void onTest(final QianXinItemBean bean) {

        if (!isUploadImg) {
            ToastUtils.showToast("请先上传实地照片");
            return;
        }

        if (locationBean == null) {
            ToastUtils.showToast("请先定位");
            return;
        }
        if (!compareDistance(bean, 1)) {
            ToastUtils.showToast("当前位置和该光缆的位置大于1公里，请确认定位位置!");
            return;
        }

        if (TextUtils.equals(bean.getSTATENAME(), "损坏")) {
            ToastUtils.showToast("当前纤芯已损坏，不能测试！");
            return;
        }

        final TestArgsAndStartBean testArgsAndStartBean;
        if (argsMode == 0) {
            testArgsAndStartBean = testArgsCustomModeBean;
        } else if (argsMode == 1) {
            testArgsAndStartBean = testArgsLastModeBean;
            if (testArgsAndStartBean == null) {
                ToastUtils.showToast("没有上一次测试的数据!");
                return;
            }
        } else {
            testArgsAndStartBean = testArgsAutoModeBean;
        }


        if (!SocketService.isConn()) {
            ToastUtils.showToast("请和设备建立链接");
            HotConnActivity.open(this);
            return;
        }

        if (TextUtils.isEmpty(SocketService.getDeviceNum())) {
            ToastUtils.showToast("设备号没有获取，请先获取设备号");
            HotConnActivity.open(this);
            return;
        }


        if (progressDialog == null) {
            initProgress();
        }
        progressDialog.setTitle("正在校验参数");
        progressDialog.show();
        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .checkSerial(SocketService.getDeviceNum(), UserManager.getInstance().getUserName())
                .map(ResultBooleanFunction.create())
                .compose(LifeObservableTransformer.<Boolean>create(this))
                .subscribe(new ErrorObserver<Boolean>(this) {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            SPUtil.getInstance("testArgs").put("args", testArgsAndStartBean);
                            QianXinListActivity.this.testBean = bean;
                            realTest(testArgsAndStartBean);
                        } else {
                            ToastUtils.showToast("该设备没有授权无法测试，请联系管理员。");
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
//                        super.onError(e);
                        ToastUtils.showToast("该设备没有授权无法测试，请联系管理员。");
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                        }
                    }
                });
    }


    private void realTest(TestArgsAndStartBean testArgsAndStartBean) {
        if (progressDialog == null) {
            initProgress();
        }
        progressDialog.setTitle("正在获取相关参数");
        progressDialog.show();

        EventBus.getDefault().post(testArgsAndStartBean, EventBusTag.SEND_TEST_ARGS_AND_START_TEST);

//        chooseArgs();
    }


    private void initProgress() {
        progressDialog = new ProgressDialog(QianXinListActivity.this);
        progressDialog.setCanceledOnTouchOutside(false);
    }

    @Subscriber(tag = EventBusTag.TAG_RECIVE_MSG)
    public void reciverMsg(Packet packet) {

        if (packet.cmd == CMD.RECIVE_SOR_INFO && packet.data.length >= (32 + 16 + 4)) {
            byte[] fileNameB = ByteUtil.subBytes(packet.data, 0, 32);
            byte[] fileLocB = ByteUtil.subBytes(packet.data, 32, 16);
            byte[] fileSizeB = ByteUtil.subBytes(packet.data, 32 + 16, 4);
            fileName = ByteUtil.bytes2Str(fileNameB);
            fileDir = ByteUtil.bytes2Str(fileLocB);
            fileSize = ByteUtil.bytesToInt(fileSizeB);
            MyLog.e("fileName = " + fileName + "  fileLoc = " + fileDir + " fileSize = " + fileSize);
            getSorFilecount = 0;

            getSorFile();
        }
    }

    @Subscriber(tag = EventBusTag.SOR_RECIVE_SUCCESS)
    public void reciveSorSuccess(SorFileBean bean) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        testBean.setTestLocalFilePath(bean.filePath);
        adapter.notifyDataSetChanged();

        testSuccessHint();
    }

    private void testSuccessHint() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("测试完成,是否上传测试文件?");
        builder.setNegativeButton("上传", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onUpload(testBean);
                dialog.dismiss();
            }
        });
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.setCancelable(false);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
    }

    @Subscriber(tag = EventBusTag.SOR_RECIVE_FAIL)
    public void reciveSorFail(SorFileBean bean) {
        getSorFile();
    }

    private AlertDialog chooseArgsDialog;
    private ProgressDialog progressDialog;

    private void chooseArgs() {
        progressDialog = new ProgressDialog(QianXinListActivity.this);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在获取sor文件相关参数信息");
        progressDialog.show();


        if (chooseArgsDialog == null) {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_edit_args, null);
            final EditText range = dialogView.findViewById(R.id.et_range);
            final EditText wl = dialogView.findViewById(R.id.et_wl);
            final EditText pw = dialogView.findViewById(R.id.et_pw);
            final EditText time = dialogView.findViewById(R.id.et_time);
            final EditText mode = dialogView.findViewById(R.id.et_mode);
            final EditText gi = dialogView.findViewById(R.id.et_gi);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogView);
            chooseArgsDialog = builder.create();
            chooseArgsDialog.setCanceledOnTouchOutside(false);
            dialogView.findViewById(R.id.start_test).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String rangeStr = range.getText().toString().trim();
                        String wlStr = wl.getText().toString().trim();
                        String pwStr = pw.getText().toString().trim();
                        String timeStr = time.getText().toString().trim();
                        String modeStr = mode.getText().toString().trim();
                        String giStr = gi.getText().toString().trim();
                        if (rangeStr.length() == 0 || wlStr.length() == 0
                                || pwStr.length() == 0 || timeStr.length() == 0
                                || modeStr.length() == 0 || giStr.length() == 0) {
                            ToastUtils.showToast("请先填取参数");
                            return;
                        }
                        int r = Integer.parseInt(rangeStr);
                        int w = Integer.parseInt(wlStr);
                        int p = Integer.parseInt(pwStr);
                        int t = Integer.parseInt(timeStr);
                        int m = Integer.parseInt(modeStr);
                        int g = Integer.parseInt(giStr);

                        TestArgsAndStartBean bean = new TestArgsAndStartBean();
                        bean.rang = r;
                        bean.wl = w;
                        bean.pw = p;
                        bean.time = t;
                        bean.mode = m;
                        bean.gi = g;

                        EventBus.getDefault().post(bean, EventBusTag.SEND_TEST_ARGS_AND_START_TEST);

                        progressDialog = new ProgressDialog(QianXinListActivity.this);
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setTitle("正在获取sor文件相关参数信息");
                        progressDialog.show();

                    } catch (Exception e) {
                        e.printStackTrace();
                        ToastUtils.showToast("出现异常了，请填取数值");
                    }
                    chooseArgsDialog.dismiss();
                }
            });
        }
        chooseArgsDialog.show();
    }

    private String fileName;
    private String fileDir;
    private int fileSize;
    private int getSorFilecount = 0;

    private void getSorFile() {
        if (fileName != null && fileDir != null && fileSize != 0) {
            getSorFilecount++;
            if (getSorFilecount > 3) {
                getSorFilecount = 0;
                ToastUtils.showToast("测试失败!");
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
                return;
            }

            //这一步很重要，因为协议原因。请求之前必须先删除之前的文件
            String localFileName = FileHelper.SAVE_FILE_DIR + File.separator + fileName;
            File file = new File(localFileName);
            if (file.exists()) {
                file.delete();
            }


            progressDialog.setTitle("正在获取测试文件");
            progressDialog.show();

            SorFileBean bean = new SorFileBean();
            bean.fileDir = fileDir;
            bean.fileName = fileName;
            bean.fileSize = fileSize;
            EventBus.getDefault().post(bean, EventBusTag.GET_SOR_FILE);

        } else {
            ToastUtils.showToast("请先设备向APP反馈sor文件信息");
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        }
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }


    void initArgsData() {
        testArgsCustomModeBean = new TestArgsAndStartBean();
        juliS = new ArrayList<>();

        List<SingleChooseBean> maikuanS1 = new ArrayList<>();
        maikuanS1.add(new SingleChooseBean(0, "5ns", 5));
        maikuanS1.add(new SingleChooseBean(1, "10ns", 10));
        maikuanS1.add(new SingleChooseBean(2, "20ns", 20));
        maikuanS1.add(new SingleChooseBean(3, "40ns", 40));
        juliS.add(new SingleChooseBean(0, "300m", 300, maikuanS1));
        juliS.add(new SingleChooseBean(1, "1km", 1000, maikuanS1));

        List<SingleChooseBean> maikuanS2 = new ArrayList<>(maikuanS1);
        maikuanS2.add(new SingleChooseBean(4, "80ns", 80));
        maikuanS2.add(new SingleChooseBean(5, "160ns", 160));

        juliS.add(new SingleChooseBean(2, "3km", 3000, maikuanS2));
        juliS.add(new SingleChooseBean(3, "5km", 5000, maikuanS2));


        List<SingleChooseBean> maikuanS3 = new ArrayList<>(maikuanS2);
        maikuanS3.add(new SingleChooseBean(6, "320ns", 320));
        juliS.add(new SingleChooseBean(4, "10km", 10000, maikuanS3));


        List<SingleChooseBean> maikuanS4 = new ArrayList<>(maikuanS3);
        maikuanS4.add(new SingleChooseBean(7, "640ns", 640));

        juliS.add(new SingleChooseBean(5, "20km", 20000, maikuanS4));
        juliS.add(new SingleChooseBean(6, "30km", 30000, maikuanS4));


        List<SingleChooseBean> maikuanS5 = new ArrayList<>(maikuanS4.subList(4, maikuanS4.size()));
        maikuanS5.add(new SingleChooseBean(8, "1.28us", 1280));
        juliS.add(new SingleChooseBean(7, "60km", 60000, maikuanS5));

        List<SingleChooseBean> maikuanS6 = new ArrayList<>(maikuanS5.subList(1, maikuanS5.size()));
        maikuanS6.add(new SingleChooseBean(9, "2.56us", 2560));
        maikuanS6.add(new SingleChooseBean(10, "5.12us", 5120));
        maikuanS6.add(new SingleChooseBean(11, "10.24us", 10240));
        maikuanS6.add(new SingleChooseBean(12, "20.48us", 20480));
        juliS.add(new SingleChooseBean(8, "100km", 100000, maikuanS6));
        juliS.add(new SingleChooseBean(9, "180km", 180000, maikuanS6));
        testArgsCustomModeBean.rang = juliS.get(0).getValue();

        maiKuanS = juliS.get(0).getNextChooses();
        testArgsCustomModeBean.pw = maiKuanS.get(0).getValue();

        bochangS = new ArrayList<>();
        bochangS.add(new SingleChooseBean(0, "1550nm", 1550));
        testArgsCustomModeBean.wl = bochangS.get(0).getValue();


        timeS = new ArrayList<>();
        timeS.add(new SingleChooseBean(0, "10s", 10));
        timeS.add(new SingleChooseBean(1, "15s", 15));
        timeS.add(new SingleChooseBean(2, "30s", 30));
        timeS.add(new SingleChooseBean(3, "1min", 60));
        testArgsCustomModeBean.time = timeS.get(0).getValue();

        modeS = new ArrayList<>();
        modeS.add(new SingleChooseBean(0, "平均", 1));
//        modeS.add(new SingleChooseBean(1, "实时", 2));
        testArgsCustomModeBean.mode = modeS.get(0).getValue();

        zheshelvS = new ArrayList<>();
        zheshelvS.add(new SingleChooseBean(0, "146850", 146850));
        testArgsCustomModeBean.gi = zheshelvS.get(0).getValue();
    }

    private TagLayout juli, bochang, maikuan, time, zheshelv, mode;
    //0 自定义  1上一次  2自动
    int argsMode = 0;

    View headerView() {
        final View view = LayoutInflater.from(this).inflate(R.layout.layout_qianxin_header, ll, false);
        ll.addView(view, 0);

        tvLocation = view.findViewById(R.id.tv_location);
        startLocation();
        tvLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocation();
            }
        });


        view.findViewById(R.id.bt_look_img).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = Contacts.BASE_URL + "/glcs/cblFiber/appShowImgInfo?objectId="
                        + guangLanDBean.getID() + "&objectName=光缆段";
                WebActivity.open(QianXinListActivity.this, "查看图片", path);
            }
        });
        view.findViewById(R.id.bt_look).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                look();
            }
        });
        view.findViewById(R.id.bt_upload_img).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // uploadImg();
                new RxPermissions(QianXinListActivity.this)
                        .request(
                                Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .subscribe(new Consumer<Boolean>() {
                            @Override
                            public void accept(Boolean aBoolean) throws Exception {
                                if (aBoolean) {
//                                    choosePhoto();
                                    takePhoto();
                                } else {
                                    ToastUtils.showToast("请开启权限");
                                }
                            }
                        });
            }
        });

        TextView tv_guanglan_name = view.findViewById(R.id.tv_guanglan_name);
        TextView tv_guangland_name = view.findViewById(R.id.tv_guangland_name);

        tv_guanglan_name.setText("所属光缆:" + guangLanDBean.getCABLE_NAME());
        tv_guangland_name.setText("光缆段名称:" + guangLanDBean.getCABL_OP_NAME());

        final View cutomView = view.findViewById(R.id.content);
        final View lastView = view.findViewById(R.id.content2);
        final View autoMode = view.findViewById(R.id.content3);

        final TextView head_click = view.findViewById(R.id.head_click);
        view.findViewById(R.id.head_click).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View content;
                if (argsMode == 0) {
                    content = cutomView;
                } else if (argsMode == 1) {
                    content = lastView;
                    initLastMode(view);
                } else {
                    content = autoMode;
                }
                content.setVisibility(content.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (content.getVisibility() == View.VISIBLE) {
                    head_click.setText("确定");
                    if (argsMode == 1) {
                        initLastMode(view);
                    }
                } else {
                    head_click.setText("展开");
                }
            }
        });

        initCustomMode(view);
        initLastMode(view);
        initAutoMode(view);

        final TagLayout tagLayout = view.findViewById(R.id.sel_mode);
        tagLayout.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    head_click.setText("确定");
                    argsMode = i;
                    //自定义
                    if (i == 0) {
                        cutomView.setVisibility(View.VISIBLE);
                        lastView.setVisibility(View.GONE);
                        autoMode.setVisibility(View.GONE);
                        //上一次
                    } else if (i == 1) {
                        initLastMode(view);
                        cutomView.setVisibility(View.GONE);
                        lastView.setVisibility(View.VISIBLE);
                        autoMode.setVisibility(View.GONE);
                        //自动配置
                    } else {
                        cutomView.setVisibility(View.GONE);
                        lastView.setVisibility(View.GONE);
                        autoMode.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        tagLayout.addTags("自定义", "上一次配置", "自动配置");
//        tagLayout.setCheckTag(0);

        return view;
    }


    private void initAutoMode(View view) {
        TagLayout juli = view.findViewById(R.id.content3_juli);
        juli.addTags("300m", "1km", "3km", "5km", "10km", "20km", "30km", "60km", "100km", "180km");
        juli.setCheckTag(0);
        juli.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (!b) {
                    return;
                }
                TestArgsAndStartBean bean = new TestArgsAndStartBean();
                bean.mode = 1;
                bean.gi = 146850;
                bean.wl = 1550;
                if (i == 0) {
                    bean.rang = 300;
                    bean.pw = 10;
                    bean.time = 10;
                } else if (i == 1) {
                    bean.rang = 1000;
                    bean.pw = 20;
                    bean.time = 10;
                } else if (i == 2) {
                    bean.rang = 3000;
                    bean.pw = 20;
                    bean.time = 10;
                } else if (i == 3) {
                    bean.rang = 5000;
                    bean.pw = 40;
                    bean.time = 15;
                } else if (i == 4) {
                    bean.rang = 10000;
                    bean.pw = 80;
                    bean.time = 15;
                } else if (i == 5) {
                    bean.rang = 20000;
                    bean.pw = 80;
                    bean.time = 15;
                } else if (i == 6) {
                    bean.rang = 30000;
                    bean.pw = 160;
                    bean.time = 15;
                } else if (i == 7) {
                    bean.rang = 60000;
                    bean.pw = 320;
                    bean.time = 30;
                } else if (i == 8) {
                    bean.rang = 100000;
                    bean.pw = 640;
                    bean.time = 30;
                } else if (i == 9) {
                    bean.rang = 180000;
                    bean.pw = 2560;
                    bean.time = 60;
                }
                testArgsAutoModeBean = bean;
            }
        });
        //初始化
        TestArgsAndStartBean bean = new TestArgsAndStartBean();
        bean.mode = 1;
        bean.gi = 146850;
        bean.wl = 1550;
        bean.rang = 300;
        bean.pw = 10;
        bean.time = 10;
        testArgsAutoModeBean = bean;
    }

    private void initLastMode(View view) {
        TestArgsAndStartBean bean = SPUtil.getInstance("testArgs").getSerializable("args", null);
        TextView juli = view.findViewById(R.id.content2_juli);
        TextView bochang = view.findViewById(R.id.content2_bochang);
        TextView maikuan = view.findViewById(R.id.content2_maikuan);
        TextView time = view.findViewById(R.id.content2_time);
        TextView mode = view.findViewById(R.id.content2_mode);
        TextView zheshelv = view.findViewById(R.id.content2_zheshelv);

        if (bean != null) {
            juli.setText("测试距离:" + bean.rang);
            bochang.setText("测试波长:" + bean.wl);
            maikuan.setText("测试脉宽:" + bean.pw);
            time.setText("测试时间:" + bean.time);
            mode.setText("测试模式:" + (bean.mode == 1 ? "平均" : "实时"));
            zheshelv.setText("折射率:" + bean.gi);
        } else {
            juli.setText("测试距离: 无");
            bochang.setText("测试波长: 无");
            maikuan.setText("测试脉宽: 无");
            time.setText("测试时间: 无");
            mode.setText("测试模式: 无");
            zheshelv.setText("折射率: 无");
        }

        testArgsLastModeBean = bean;
    }

    private void initCustomMode(View view) {
        initArgsData();

        juli = view.findViewById(R.id.juli);
        for (SingleChooseBean singleChooseBean : juliS) {
            juli.addTag(singleChooseBean.getName());
        }
        juli.setCheckTag(0);
        juli.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.rang = juliS.get(i).getValue();
                    maiKuanS = juliS.get(i).getNextChooses();
                    changeTag(maikuan, maiKuanS);
                    testArgsCustomModeBean.pw = maiKuanS.get(0).getValue();
                }
            }
        });

        bochang = view.findViewById(R.id.bochang);
        for (SingleChooseBean singleChooseBean : bochangS) {
            bochang.addTag(singleChooseBean.getName());
        }
        bochang.setCheckTag(0);
        bochang.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.wl = bochangS.get(i).getValue();
                }
            }
        });

        maikuan = view.findViewById(R.id.maikuan);
        for (SingleChooseBean singleChooseBean : maiKuanS) {
            maikuan.addTag(singleChooseBean.getName());
        }
        maikuan.setCheckTag(0);
        maikuan.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.pw = maiKuanS.get(i).getValue();
                }
            }
        });

        time = view.findViewById(R.id.time);
        for (SingleChooseBean singleChooseBean : timeS) {
            time.addTag(singleChooseBean.getName());
        }
        time.setCheckTag(0);
        time.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.time = timeS.get(i).getValue();
                }
            }
        });

        mode = view.findViewById(R.id.mode);
        for (SingleChooseBean singleChooseBean : modeS) {
            mode.addTag(singleChooseBean.getName());
        }
        mode.setCheckTag(0);
        mode.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.mode = modeS.get(i).getValue();
                }
            }
        });

        zheshelv = view.findViewById(R.id.zheshelv);
        for (SingleChooseBean singleChooseBean : zheshelvS) {
            zheshelv.addTag(singleChooseBean.getName());
        }
        zheshelv.setCheckTag(0);
        zheshelv.setTagCheckListener(new TagView.OnTagCheckListener() {
            @Override
            public void onTagCheck(int i, String s, boolean b) {
                if (b) {
                    testArgsCustomModeBean.gi = zheshelvS.get(i).getValue();
                }
            }
        });
    }

    void changeTag(TagLayout tagLayout, List<SingleChooseBean> newBeans) {
        tagLayout.cleanTags();
        for (SingleChooseBean newBean : newBeans) {
            tagLayout.addTag(newBean.getName());
        }
        tagLayout.setCheckTag(0);
    }

    void checkInit() {
        for (int i = 0; i < juliS.size(); i++) {
            if (testArgsCustomModeBean.rang == juliS.get(i).getValue()) {
                juli.setCheckTag(i);
                break;
            }
        }

        for (int i = 0; i < bochangS.size(); i++) {
            if (testArgsCustomModeBean.wl == bochangS.get(i).getValue()) {
                bochang.setCheckTag(i);
                break;
            }
        }

        for (int i = 0; i < maiKuanS.size(); i++) {
            if (testArgsCustomModeBean.pw == maiKuanS.get(i).getValue()) {
                maikuan.setCheckTag(i);
                break;
            }
        }

        for (int i = 0; i < timeS.size(); i++) {
            if (testArgsCustomModeBean.time == timeS.get(i).getValue()) {
                time.setCheckTag(i);
                break;
            }
        }

        for (int i = 0; i < modeS.size(); i++) {
            if (testArgsCustomModeBean.mode == modeS.get(i).getValue()) {
                mode.setCheckTag(i);
                break;
            }
        }

        for (int i = 0; i < zheshelvS.size(); i++) {
            if (testArgsCustomModeBean.gi == zheshelvS.get(i).getValue()) {
                zheshelv.setCheckTag(i);
                break;
            }
        }
    }


    @Override
    public void onUpload(final QianXinItemBean bean) {

        if (locationBean == null) {
            ToastUtils.showToast("请先获取定位!");
            return;
        }

        if (TextUtils.isEmpty(bean.getTestLocalFilePath())) {
            ToastUtils.showToast("请先进行测试!");
            return;
        }
        if (!new File(bean.getTestLocalFilePath()).exists()) {
            ToastUtils.showToast("测试文件已失效，请重新测试!");
            bean.setTestLocalFilePath(null);
            adapter.notifyDataSetChanged();
            return;
        }

        if (progressDialog == null) {
            initProgress();
        }
        progressDialog.setTitle("正在上传sor文件");
        progressDialog.show();

        final File file = new File(bean.getTestLocalFilePath());
        RequestBody requestFile =
                RequestBody.create(MediaType.parse("multipart/form-data"), file);
        String name = file.getName();
        MultipartBody.Part fileBody =
                MultipartBody.Part.createFormData("file", name, requestFile);

        String struffix = null;
        if (name.contains(".")) {
            String[] split = name.split("\\.");
            struffix = split[split.length - 1];
        }

        final String finalStruffix = struffix;
        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .saveFiberFile(RequestBodyUtils.generateRequestBody(new HashMap<String, String>() {
                    {
                        put("struffix", finalStruffix);
                        put("fiberId", bean.getFIBER_ID());
                        put("userId", UserManager.getInstance().getUserId());
                        put("geoy", String.valueOf(locationBean.latitude));
                        put("geox", String.valueOf(locationBean.longitude));
                    }
                }), fileBody)
                .map(ResultBooleanFunction.create())
                .compose(LifeObservableTransformer.<Boolean>create(this))
                .subscribe(new ErrorObserver<Boolean>(this) {
                    @Override
                    public void onNext(Boolean bo) {
                        if (bo) {
                            bean.setUpload(true);
                            bean.setMODIFY_DATE_STR(DataUtils.getNowTime());
                            adapter.notifyDataSetChanged();
                            ToastUtils.showToast("上传成功");

                            ArrayList<GuangLanDItemBean> data = SPUtil.getInstance("guanglan")
                                    .getSerializable("data", new ArrayList<GuangLanDItemBean>());
                            data.remove(guangLanDBean);
                            data.add(0, guangLanDBean);
                            if (data.size() > 10) {
                                data.remove(10);
                            }
                            SPUtil.getInstance("guanglan").put("data", data);

                        } else {
                            ToastUtils.showToast("上传失败");
                        }
                        progressDialog.dismiss();
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        progressDialog.dismiss();
                    }
                });
    }


    @Override
    public void onStatus(final QianXinItemBean bean) {
        if (locationBean == null) {
            ToastUtils.showToast("请先定位");
            return;
        }

        if (!compareDistance(bean, 5)) {
            ToastUtils.showToast("超出更改范围!");
            return;
        }


        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .quertstatuslistinfo()
                .compose(LifeObservableTransformer.<List<StatusInfoBean>>create(this))
                .subscribe(new ErrorObserver<List<StatusInfoBean>>(this) {
                    @Override
                    public void onNext(List<StatusInfoBean> data) {
                        if (data != null && data.size() > 0) {
                            BottomListDialog.newInstance(data, new BottomListDialog.Convert<StatusInfoBean>() {
                                @Override
                                public String convert(StatusInfoBean data) {
                                    return data.getName();
                                }
                            }).setCallback(new BottomListDialog.Callback<StatusInfoBean>() {
                                @Override
                                public boolean onSelected(StatusInfoBean data, int position) {
                                    changeStatus(bean, data);
                                    return true;
                                }
                            }).show(getSupportFragmentManager(), "state");
                        } else {
                            ToastUtils.showToast("没有查到状态信息");
                        }
                    }
                });
    }


    void changeStatus(final QianXinItemBean qianXinItemBean,
                      final StatusInfoBean statusInfoBean) {
//        if (TextUtils.equals(qianXinItemBean.getStateId(), statusInfoBean.getStateId())){
//            return ;
//        }

        RetrofitHttpEngine.obtainRetrofitService(Api.class)
                .updateFiberState(qianXinItemBean.getFIBER_ID(), statusInfoBean.getStateId())
                .map(new ResultBooleanFunction<>())
                .compose(LifeObservableTransformer.<Boolean>create(this))
                .subscribe(new ErrorObserver<Boolean>(this) {
                    @Override
                    public void onNext(Boolean b) {
                        if (b) {
//                            qianXinItemBean.setStateId(statusInfoBean.getStateId());
                            qianXinItemBean.setSTATENAME(statusInfoBean.getName());
                            int pos = adapter.getData().indexOf(qianXinItemBean);
                            if (pos != -1) {
                                //1 是header
                                adapter.notifyItemChanged(pos);
                            } else {
                                adapter.notifyDataSetChanged();
                            }

                            ToastUtils.showToast("修改状态成功");
                        } else {
                            ToastUtils.showToast("修改状态失败");
                        }
                    }
                });
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
                            locationManager = new LocationManager(QianXinListActivity.this,
                                    QianXinListActivity.this);
                            locationManager.start();
                        } else {
                            ToastUtils.showToast("请开启定位权限");
                            tvLocation.setText("定位失败，点击重新定位");
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        stopLocation();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void stopLocation() {
        if (locationManager != null) {
            locationManager.stop();
            locationManager = null;
        }
    }

    @Override
    public void onSuccess(LocationManager.LocationBean bean) {
        this.locationBean = bean;
        tvLocation.setText("定位位置:" + bean.addrss);
    }

    @Override
    public void onError(int code, String msg) {
        tvLocation.setText("定位失败，点击重新定位");
    }

}
