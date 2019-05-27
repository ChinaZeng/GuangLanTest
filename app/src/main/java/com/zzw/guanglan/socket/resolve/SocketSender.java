package com.zzw.guanglan.socket.resolve;

import com.zzw.guanglan.socket.thread.SocketThread;
import com.zzw.guanglan.socket.utils.ByteUtil;
import com.zzw.guanglan.utils.MD5Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

public class SocketSender {

    private SocketThread socketThread;
    private Dispatcher mDispatcher;

    public SocketSender(SocketThread socketThread) {
        this.socketThread = socketThread;
        mDispatcher = new Dispatcher();
    }



    /**
     * APP询问设备序列号
     */
    public void getDeviceSerialNumber() {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getDeviceSerialNumberPacket(socketThread.socket);
                socketThread.sendQueue(packet);
            }
        });
    }


    /**
     * APP给设备下发OTDR测试参数并启动测试
     */
    public void sendTestArgsAndStartTest(final int... args) {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getTestArgsAndStartTestPacket(socketThread.socket, args);
                socketThread.sendQueue(packet);
            }
        });
    }

    /**
     * APP向设备发送停止OTDR测试命令
     */
    public void sendTestArgsAndStopTest() {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getTestArgsAndStopTestPacket(socketThread.socket);
                socketThread.sendQueue(packet);
            }
        });
    }


    /**
     * @param fileName 文件名称  16
     * @param fileDir  文件存放位置 48
     */
    public void getSorFile(final String fileName, final String fileDir) {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getSorFilePacket(socketThread.socket, fileName, fileDir);
                socketThread.sendQueue(packet);
            }
        });
    }

    /**
     * 发送心跳
     */
    public void sendHeart() {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getHeartPacket(socketThread.socket, true);
                socketThread.sendQueue(packet);
            }
        });
    }

    /**
     * 回复心跳
     */
    public void reHeart() {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getHeartPacket(socketThread.socket, false);
                socketThread.sendQueue(packet);
            }
        });
    }

    /**
     * 回复
     *
     * @param errorCode 错误代码
     * @param cmdCode   命令码  Uint32
     */
    public void sendRe(final int errorCode, final int cmdCode) {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getRePacket(socketThread.socket, errorCode, cmdCode);
                socketThread.sendQueue(packet);
            }
        });
    }


    private final static int FILE_BUFFER = 4096;

    public void sendFileMsg(final String path) {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                try {
                    File file = new File(path);
                    is = new FileInputStream(file);
                    byte[] buffer = new byte[FILE_BUFFER];
                    int len;
                    String md5 = MD5Utils.getFileMD5(file);
                    String name = file.getName();
                    int fileSize = (int) file.length();
                    while ((len = is.read(buffer, 0, buffer.length)) > 0) {
                        byte[] data = buffer;
                        if (len < buffer.length) {
                            data = ByteUtil.subBytes(buffer, 0, len);
                        }
                        Packet packetData = PacketHelper.getSendSorFilePacket(socketThread.socket, name, md5, fileSize, data);
                        socketThread.sendQueue(packetData);
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    /**
     * 设备向app推送sor文件信息
     * @param name
     * @param fileLoc
     * @param size
     */
    public void sendSorInfo(final String name, final String fileLoc, final int size) {
       mDispatcher.submit(new Runnable() {
            @Override
            public void run() {
                Packet packet = PacketHelper.getSendSorInfo(socketThread.socket, name, fileLoc,size);
                socketThread.sendQueue(packet);
            }
        });
    }

    public void shutdownNow(){
        mDispatcher.shutdownNow();
        mDispatcher = null;
    }

}
