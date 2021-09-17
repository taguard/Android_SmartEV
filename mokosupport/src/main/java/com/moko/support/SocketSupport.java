package com.moko.support;

import android.content.Context;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.moko.support.entity.DeviceResponse;
import com.moko.support.event.SocketConnectionEvent;
import com.moko.support.event.SocketResponseEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


public class SocketSupport {

    private static final String TAG = SocketSupport.class.getSimpleName();

    private static volatile SocketSupport INSTANCE;

    private Context mContext;

    private SocketThread mSocketThread;

    private SocketSupport() {
        //no instance
    }

    public static SocketSupport getInstance() {
        if (INSTANCE == null) {
            synchronized (SocketSupport.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SocketSupport();
                }
            }
        }
        return INSTANCE;
    }

    public void init(Context context) {
        mContext = context;
    }


    public void startSocket() {
        mSocketThread = new SocketThread();
        mSocketThread.start();
    }

    public void closeSocket() {
        mSocketThread.close();
    }

    public void sendMessage(String message) {
        new Thread(() -> {
            mSocketThread.send(message);
        }).start();
    }


    private class SocketThread extends Thread {
        public Socket client = null;
        private String ip = "192.168.4.1";
        private int port = 8266;
        private int timeout = 10000;

        private DataOutputStream out;
        private DataInputStream in;
        public boolean isRun = true;

        @Override
        public void run() {
            XLog.i("线程socket开始运行");
            if (!conn()) {
                return;
            }
            XLog.i("1.run开始");
            String responseJson;
            while (isRun) {
                try {
                    if (client != null) {
                        XLog.i("2.等待设备发送数据");
                        byte[] b = new byte[1024];
                        in.read(b);
                        int len = 0;
                        for (int i = 0; i < b.length; i++) {
                            if (b[i] == 0) {
                                break;
                            }
                            len++;
                        }
                        if (len == 0) {
                            return;
                        }
                        responseJson = new String(b, 0, len);
                        XLog.i(String.format("3.返回数据:%s", responseJson));
                        DeviceResponse response = new Gson().fromJson(responseJson, DeviceResponse.class);
                        EventBus.getDefault().post(new SocketResponseEvent(response));
                        if (response.result.header == MokoConstants.HEADER_SET_WIFI_INFO) {
                            XLog.i("4.断开连接");
                            isRun = false;
                            close();
                            break;
                        }
                    } else {
                        XLog.i("没有可用连接");
                        socketState(MokoConstants.CONN_STATUS_FAILED);
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    XLog.i("获取数据超时");
                    e.printStackTrace();
                    isRun = false;
                    close();
                    socketState(MokoConstants.CONN_STATUS_TIMEOUT);
                    break;
                } catch (Exception e) {
                    XLog.i("数据接收错误" + e.getMessage());
                    e.printStackTrace();
                    isRun = false;
                    close();
                    socketState(MokoConstants.CONN_STATUS_CLOSED);
                    break;
                }
            }
        }

        /**
         * 连接socket服务器
         */
        public boolean conn() {
            try {
                XLog.i("获取到ip端口:" + ip + ":" + port);
                XLog.i("连接中……");
                client = new Socket(ip, port);
//            client.setSoTimeout(timeout);// 设置阻塞时间
                XLog.i("连接成功");
                in = new DataInputStream(client.getInputStream());
                out = new DataOutputStream(client.getOutputStream());
                XLog.i("输入输出流获取成功");
                socketState(MokoConstants.CONN_STATUS_SUCCESS);
                return true;
            } catch (UnknownHostException e) {
                XLog.i("连接错误UnknownHostException 重新获取");
                e.printStackTrace();
                socketState(MokoConstants.CONN_STATUS_FAILED);
            } catch (IOException e) {
                XLog.i("连接服务器io错误");
                e.printStackTrace();
                socketState(MokoConstants.CONN_STATUS_FAILED);
            } catch (Exception e) {
                XLog.i("连接服务器错误Exception" + e.getMessage());
                e.printStackTrace();
                socketState(MokoConstants.CONN_STATUS_FAILED);
            }
            return false;
        }

        private void socketState(int status) {
            EventBus.getDefault().post(new SocketConnectionEvent(status));
        }

        /**
         * 发送数据
         *
         * @param mess
         */
        public void send(String mess) {
            try {
                if (client != null && client.isConnected()) {
                    XLog.i(String.format("发送%s至%s:%d", mess,
                            client.getInetAddress().getHostAddress(),
                            client.getPort()));
                    out.write(mess.getBytes());
                } else {
                    XLog.i("连接不存在，重新连接");
                    conn();
                }
            } catch (Exception e) {
                XLog.i("send error");
                e.printStackTrace();
            } finally {
                XLog.i("发送完毕");
            }
        }

        /**
         * 关闭连接
         */
        public void close() {
            try {
                isRun = false;
                if (client != null && client.isConnected()) {
                    XLog.i("close in");
                    in.close();
                    XLog.i("close out");
                    out.close();
                    XLog.i("close client");
                    client.close();
                }
            } catch (Exception e) {
                XLog.i("close err");
                e.printStackTrace();
            }

        }
    }
}
