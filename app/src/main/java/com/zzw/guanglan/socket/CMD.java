package com.zzw.guanglan.socket;

public interface CMD {
    int EMPTY = 0X00;

    //APP询问设备序列号
    int GET_DEVICE_SERIAL_NUMBER = 0x83000001;

    //OTDR上报设备序列号给APP
    int RECIVE_DEVICE_SERIAL_NUMBER = 0x83000002;

    //APP给设备下发OTDR测试参数并启动测试
    int SEND_TEST_ARGS_AND_START_TEST = 0x83000003;
    //APP向设备发送停止OTDR测试命令
    int SEND_TEST_ARGS_AND_STOP_TEST = 0x83000007;

    //设备向APP反馈sor文件信息
    int RECIVE_SOR_INFO = 0x83000004;

    // APP向设备请求传输sor文件
    int GET_SOR_FILE = 0x83000005;

    //设备向APP发送OTDR测试结果文件
    int RECIVE_SOR_FILE = 0x83000006;

    //心跳
    int HEART_SEND = 0x10000000;
    //心跳回复
    int HEART_RE = 0x10000001;
    //回复
    int _RE = 0x8e000000;


    int _FILE = 0x83000006;


    interface _CODE {
        //成功
        int SUCCESS = 0;
        //序列号验证失败
        int DEVICE_NUM_VER_ERROR = 101;
        //设备读取不到序列号
        int DEVICE_NUM_NOT_READ_ERROR = 102;
        //OTDR测试参数有误
        int OTDR_ARGS_ERROR = 110;
        //OTDR测试出错
        int OTDR_TEST_ERROR = 111;
        //OTDR测试结果文件不存在
        int OTDR_TEST_FILE_NOT_EXIT_ERROR = 121;
    }


}
