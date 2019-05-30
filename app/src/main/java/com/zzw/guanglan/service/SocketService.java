package com.zzw.guanglan.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.zzw.guanglan.Contacts;
import com.zzw.guanglan.socket.CMD;
import com.zzw.guanglan.socket.EventBusTag;
import com.zzw.guanglan.socket.event.ConnBean;
import com.zzw.guanglan.socket.event.ReBean;
import com.zzw.guanglan.socket.event.SorFileBean;
import com.zzw.guanglan.socket.event.TestArgsAndStartBean;
import com.zzw.guanglan.socket.listener.STATUS;
import com.zzw.guanglan.socket.listener.StatusListener;
import com.zzw.guanglan.socket.manager.ServerManager;
import com.zzw.guanglan.socket.resolve.Packet;
import com.zzw.guanglan.socket.utils.ByteUtil;
import com.zzw.guanglan.socket.utils.FileHelper;
import com.zzw.guanglan.socket.utils.MyLog;
import com.zzw.guanglan.utils.MD5Utils;

import org.simple.eventbus.EventBus;
import org.simple.eventbus.Subscriber;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class SocketService extends Service implements StatusListener {

    private static final String TAG = "SocketService";

    private HotBroadcastReceiver receiver;
    private ServerManager serverManager;

    private final int PORT = 8825;
    private String key;
    private volatile int heartFlog = 0;
    private HeartThread heartThread;


    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        receiver = new HotBroadcastReceiver();
        IntentFilter mIntentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        registerReceiver(receiver, mIntentFilter);

        serverManager = new ServerManager(PORT);
        serverManager.setListener(this);
        serverManager.startServer();
    }


    public static boolean isConn() {
        return Contacts.isConn && !TextUtils.isEmpty(Contacts.connKey);
    }

    public static String getDeviceNum() {
        return TextUtils.isEmpty(Contacts.deviceNum) ? "" : Contacts.deviceNum.trim();
    }

    @Override
    public void onDestroy() {
        MyLog.e(TAG, "onDestroy");
        super.onDestroy();
        socketDisConn();
        EventBus.getDefault().unregister(this);
        unregisterReceiver(receiver);
        serverManager.close();
    }


    private void socketDisConn() {
        socketDisConnFlog();
        closeHeartThread();
    }

    private void socketDisConnFlog() {
        Contacts.isConn = false;
        Contacts.connKey = null;
        Contacts.deviceNum = null;
        EventBus.getDefault().post(false, EventBusTag.SOCKET_CONN_STATUS_CHANGE);
    }


    private void closeHeartThread() {
        if (heartThread != null && heartThread.isAlive()) {
            heartThread.interrupt();
        }
        heartThread = null;
        heartFlog = 0;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void statusChange(String key, STATUS status) {

        if (status == STATUS.END) {
            String content = key + "断开连接";
            MyLog.e(TAG,content);
            this.key = null;
            MyLog.e(TAG, "断开连接");
            socketDisConn();
        } else if (status == STATUS.INIT) {
            String content = key + "初始化接收线程";
            MyLog.e(TAG, content);
        } else if (status == STATUS.RUNNING) {
            String content = key + "建立连接,开始运行";
            MyLog.e(TAG, content);
            this.key = key;

            Contacts.isConn = true;
            Contacts.connKey = key;
            heartFlog = 0;
            EventBus.getDefault().post(true, EventBusTag.SOCKET_CONN_STATUS_CHANGE);

            closeHeartThread();
            heartThread = new HeartThread();
            heartThread.start();
        }

        ConnBean event = new ConnBean();
        event.status = status;
        event.key = key;
        EventBus.getDefault().post(event);
    }


    @Subscriber(tag = EventBusTag.GET_DEVICE_SERIAL_NUMBER)
    public void getDeviceSerialNumber(int cmd) {
        if (key != null) {
            serverManager.getDeviceSerialNumber(key);
        }
    }


    @Subscriber(tag = EventBusTag.SEND_TEST_ARGS_AND_STOP_TEST)
    public void sendTestArgsAndStopTest(int flog) {
        if (key != null) {
            serverManager.sendTestArgsAndStopTestPacket(key);
        }
    }


    @Subscriber(tag = EventBusTag.SEND_TEST_ARGS_AND_START_TEST)
    public void sendTestArgsAndStartTest(TestArgsAndStartBean bean) {
        if (key != null) {
            serverManager.sendTestArgsAndStartTestPacket(key, bean);
        }
    }

    @Subscriber(tag = EventBusTag.GET_SOR_FILE)
    public void getSorFile(SorFileBean bean) {
        if (key != null) {
            serverManager.getSorFile(key, bean);
        }
    }

    @Subscriber(tag = EventBusTag.SEND_RE)
    public void getSorFile(ReBean bean) {
        if (key != null) {
            serverManager.sendRe(key, bean);
        }
    }

    @Subscriber(tag = EventBusTag.SEND_HEART)
    public void sendHeart(int flog) {
        if (key != null) {
            serverManager.sendHeart(key);
        }
    }

    @Subscriber(tag = EventBusTag.RE_HEART)
    public void reHeart(int flog) {
        if (key != null) {
            serverManager.reHeart(key);
        }
    }

    @Subscriber(tag = EventBusTag.TAG_RECIVE_MSG)
    public void reciverMsg(Packet packet) {
        StringBuilder builder = new StringBuilder();
        if (packet.cmd == CMD.GET_DEVICE_SERIAL_NUMBER) {
            builder.append("接收:获取设备号\n");
        } else if (packet.cmd == CMD.RECIVE_DEVICE_SERIAL_NUMBER) {
            builder.append("接收:OTDR上报设备序列号给APP\n");
        } else if (packet.cmd == CMD.SEND_TEST_ARGS_AND_START_TEST) {
            builder.append("接收:APP给设备下发OTDR测试参数并启动测试\n");
        } else if (packet.cmd == CMD.SEND_TEST_ARGS_AND_STOP_TEST) {
            builder.append("接收:APP向设备发送停止OTDR测试命令\n");
        } else if (packet.cmd == CMD.RECIVE_SOR_INFO) {
            builder.append("接收:设备向APP反馈sor文件信息\n");
        } else if (packet.cmd == CMD.GET_SOR_FILE) {
            builder.append("接收:APP向设备请求传输sor文件\n");
        } else if (packet.cmd == CMD.RECIVE_SOR_FILE) {
            builder.append("接收:设备向APP发送OTDR测试结果文件\n");
        } else if (packet.cmd == CMD.HEART_SEND) {
            builder.append("接收:心跳\n");
        } else if (packet.cmd == CMD.HEART_RE) {
            builder.append("接收:心跳回复\n");
        } else if (packet.cmd == CMD._RE) {
            builder.append("接收:回复指令\n");
        }

        builder.append("起始值:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(Packet.START_FRAME)) + "\n");
        builder.append("总帧长度:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.pkAllLen)) + "\n");
        builder.append("版本号:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.rev)) + "\n");
        builder.append("源地址:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.src)) + "\n");
        builder.append("目标地址:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.dst)) + "\n");
        builder.append("帧类型:" + ByteUtil.bytesToHexSpaceString(ByteUtil.shortToBytes(packet.pkType)) + "\n");
        builder.append("流水号:" + ByteUtil.bytesToHexSpaceString(ByteUtil.shortToBytes((short) packet.pktId)) + "\n");
        builder.append("保留字节:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.keep)) + "\n");
        builder.append("cmd:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.cmd)) + "\n");
        builder.append("数据长度:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.cmdDataLength)) + "\n");
        builder.append("数据:" + ByteUtil.bytesToHexSpaceString(packet.data) + "\n");
        builder.append("结尾值:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(Packet.END_FRAME)) + "\n");
        MyLog.e(TAG,builder.toString());

        if (packet.cmd == CMD.RECIVE_SOR_FILE) {
            if (packet.data.length < 32 + 4 + 32) return;

            String fileName = ByteUtil.bytes2Str(ByteUtil.subBytes(packet.data, 0, 32));
            int fileSize = ByteUtil.bytesToInt(ByteUtil.subBytes(packet.data, 32, 4));
            String MD5 = ByteUtil.bytes2Str(ByteUtil.subBytes(packet.data, 32 + 4, 32));
            byte[] data = ByteUtil.subBytes(packet.data, 32 + 4 + 32, packet.data.length - (32 + 4 + 32));

            File file = FileHelper.saveFileToLocal(data, false, fileName);
            if (file.length() >= fileSize) {
                try {
                    String fileMD5 = MD5Utils.getFileMD5(file);
                    SorFileBean bean = new SorFileBean();
                    bean.fileName = fileName;
                    bean.fileSize = fileSize;
                    bean.filePath = file.getAbsolutePath();
                    bean.MD5 = MD5;
                    MyLog.e(TAG,"sermd5 = " + MD5 + " serfilesize = " + fileSize
                            + " file:" + file.getAbsolutePath() + " filesize = " + file.length() + "  fileMd5=" + fileMD5);
                    if (TextUtils.equals(MD5, fileMD5)) {
                        MyLog.e("zzz", "接收文件成功");
                        EventBus.getDefault().post(bean, EventBusTag.SOR_RECIVE_SUCCESS);
                    } else {
                        MyLog.e("zzz", "接收文件失败");
                        EventBus.getDefault().post(bean, EventBusTag.SOR_RECIVE_FAIL);
                    }
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (packet.cmd == CMD.HEART_SEND) {
            heartFlog++;
            MyLog.e(TAG,"heartFlog = " + heartFlog);
            reHeart(1);
        } else if (packet.cmd == CMD.RECIVE_DEVICE_SERIAL_NUMBER) {
            //正常是这样的 设备厂家(16) + 序列号(16) + 设备版本(8),但是这里只要序列号  所以没判断设备版本
//            if (packet.data.length < 16 + 16 + 8) return;

            if (packet.data.length < 16 + 16) return;

            byte[] data = ByteUtil.subBytes(packet.data, 16, 16);
            String deviceNum = ByteUtil.bytes2Str(data);
            Contacts.deviceNum = deviceNum;
            EventBus.getDefault().post(deviceNum, EventBusTag.RECIVE_DEVICE_SERIAL_NUMBER);
        }
    }

    @Subscriber(tag = EventBusTag.TAG_SEND_MSG)
    public void sendMsg(Packet packet) {

        StringBuilder builder = new StringBuilder();
        if (packet.cmd == CMD.GET_DEVICE_SERIAL_NUMBER) {
            builder.append("发送:获取设备号\n");
        } else if (packet.cmd == CMD.RECIVE_DEVICE_SERIAL_NUMBER) {
            builder.append("发送:OTDR上报设备序列号给APP\n");
        } else if (packet.cmd == CMD.SEND_TEST_ARGS_AND_START_TEST) {
            builder.append("发送:APP给设备下发OTDR测试参数并启动测试\n");
        } else if (packet.cmd == CMD.SEND_TEST_ARGS_AND_STOP_TEST) {
            builder.append("发送:APP向设备发送停止OTDR测试命令\n");
        } else if (packet.cmd == CMD.RECIVE_SOR_INFO) {
            builder.append("发送:设备向APP反馈sor文件信息\n");
        } else if (packet.cmd == CMD.GET_SOR_FILE) {
            builder.append("发送:APP向设备请求传输sor文件\n");
        } else if (packet.cmd == CMD.RECIVE_SOR_FILE) {
            builder.append("发送:设备向APP发送OTDR测试结果文件\n");
        } else if (packet.cmd == CMD.HEART_SEND) {
            builder.append("发送:心跳\n");
        } else if (packet.cmd == CMD.HEART_RE) {
            builder.append("发送:心跳回复\n");
        } else if (packet.cmd == CMD._RE) {
            builder.append("发送:回复指令\n");
        }
        builder.append("起始值:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(Packet.START_FRAME)) + "\n");
        builder.append("总帧长度:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.pkAllLen)) + "\n");
        builder.append("版本号:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.rev)) + "\n");
        builder.append("源地址:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.src)) + "\n");
        builder.append("目标地址:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.dst)) + "\n");
        builder.append("帧类型:" + ByteUtil.bytesToHexSpaceString(ByteUtil.shortToBytes(packet.pkType)) + "\n");
        builder.append("流水号:" + ByteUtil.bytesToHexSpaceString(ByteUtil.shortToBytes((short) packet.pktId)) + "\n");
        builder.append("保留字节:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.keep)) + "\n");
        builder.append("cmd:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.cmd)) + "\n");
        builder.append("数据长度:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(packet.cmdDataLength)) + "\n");
        builder.append("数据:" + ByteUtil.bytesToHexSpaceString(packet.data) + "\n");
        builder.append("结尾值:" + ByteUtil.bytesToHexSpaceString(ByteUtil.intToBytes(Packet.END_FRAME)) + "\n");
        MyLog.e(TAG,builder.toString());
    }


    class HeartThread extends java.lang.Thread {
        @Override
        public void run() {
            super.run();
            while (Contacts.isConn && !isInterrupted()) {
                try {
                    Thread.sleep(10 * 1000);
                    MyLog.e(TAG,"检测 heartFlog = " + heartFlog);
                    if (heartFlog < 1) {
//                        MyLog.e("心跳没收到，关闭service");
                        socketDisConn();
//                        stopSelf();
                    } else {
                        heartFlog = 0;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    socketDisConn();
//                    stopSelf();
                }
            }
        }
    }

    private class HotBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                //state状态为：10---正在关闭；11---已关闭；12---正在开启；13---已开启
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                MyLog.e(TAG,"state =" + state);

                switch (state) {
                    case 10:
                        MyLog.e(TAG,"热点正在关闭");
                        break;
                    case 11:
                        MyLog.e(TAG,"热点已关闭");
                        socketDisConn();
                        stopSelf();
                        break;

                    case 12:
                        MyLog.e(TAG,"热点正在开启");
                        break;
                    case 13:
                        //开启成功
                        MyLog.e(TAG,"热点正在开启");
                        break;
                }
            }
        }
    }


}
