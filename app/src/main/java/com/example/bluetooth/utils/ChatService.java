package com.example.bluetooth.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.example.bluetooth.BluetoothChatActivity;
import com.example.bluetooth.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ChatService {
    //本应用的主Activity组件名称
    private static final String NAME = "BluetoothChat";
    // UUID：通用唯一识别码,是一个128位长的数字，一般用十六进制表示
    //算法的核心思想是结合机器的网卡、当地时间、一个随机数来生成
    //在创建蓝牙连接
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;
    private  Context mContext;
    //构造方法，接收UI主线程传递的对象
    public ChatService(Context context, Handler handler) {
        mContext = context;
        //构造方法完成蓝牙对象的创建
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    private synchronized void setState(int state, String address) {
        mState = state;
        if (!address.isEmpty()){
            if (/*mState == STATE_CONNECTING || */mState == STATE_CONNECTED){
                mCacheAddress = address;
            } else {
                mCacheAddress = "";
            }
        } else {
            mCacheAddress = "";
        }
        mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized String getCacheAddress() {
        return mCacheAddress;
    }

    //取消 CONNECTING 和 CONNECTED 状态下的相关线程，然后运行新的 mConnectThread 线程
    public synchronized void connect(BluetoothDevice device) {
        if (!mCacheAddress.isEmpty()){
            if (device.getAddress().equals(mCacheAddress)){
                //还是连接同一个设备，啥操作也不用做
                return;
            } else {
                //连接一个新设备//???
                //setState(STATE_LISTEN, "");
                //stop();
                //start();
            }
        }
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING, device.getAddress());
    }

    private String mCacheAddress = "";

    /*
        开启一个 ConnectedThread 来管理对应的当前连接。之前先取消任意现存的 mConnectThread 、
        mConnectedThread 、 mAcceptThread 线程，然后开启新 mConnectedThread ，传入当前刚刚接受的
        socket 连接。最后通过 Handler来通知UI连接
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (!mCacheAddress.isEmpty() && device.getAddress().equals(mCacheAddress)){
            return;
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        Message msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChatActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED, device.getAddress());

    }

    public synchronized void start() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN, "");
    }

    // 停止所有相关线程，设当前状态为 NONE
    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_NONE, "");
        mCacheAddress = "";
    }

    // 在 STATE_CONNECTED 状态下，调用 mConnectedThread 里的 write 方法，写入 byte
    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    // 连接失败的时候处理，通知 ui ，并设为 STATE_LISTEN 状态
    private void connectionFailed() {
        setState(STATE_LISTEN, "");
        Message msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChatActivity.TOAST, mContext.getString(R.string.text_not_found));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        stop();
        start();
    }

    //当连接失去(中断)的时候，设为 STATE_LISTEN 状态并通知 ui
    private void connectionLost() {
        setState(STATE_LISTEN, "");
        Message msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChatActivity.TOAST, mContext.getString(R.string.text_break));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        stop();
        start();
    }

    //-----------------------↓三个用于内部类，开子线程处理业务逻辑----------------------------

    // 创建监听线程，准备接受新连接。使用阻塞方式，调用 BluetoothServerSocket.accept()
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                //使用射频端口（RF comm）监听
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        @Override
        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket = null;
            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                if (socket != null) {
                    synchronized (ChatService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            setName("ConnectThread");
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                //ChatService.this.stop();
                ChatService.this.start();
                return;
            }
            synchronized (ChatService.this) {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}