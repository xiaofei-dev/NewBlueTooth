package com.example.bluetooth;

import static com.example.bluetooth.utils.Constants.IS_DEVICE_B;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bluetooth.utils.ChatService;
import com.example.bluetooth.utils.ToastUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class BluetoothChatActivity extends AppCompatActivity {
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    private static final int REQUEST_CONNECT_DEVICE = 1;  //请求连接设备
    private static final int REQUEST_ENABLE_BT = 2;
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private ChatService mChatService = null;

    //RxJava 轮询任务
    Disposable mDisposable;
    //开始轮询进行指定设备蓝牙连接
    private void startLoopCheck(){
        if (mDisposable != null){
            mDisposable.dispose();
        }
        mDisposable = Observable
                .interval(1000, 1000, TimeUnit.MILLISECONDS)
                .subscribe(aLong -> {
                    if (mChatService != null && !mAddress.isEmpty()){
                        //开始连接指定设备
//                        if (mChatService.getState() != ChatService.STATE_CONNECTING
//                                && mChatService.getState() != ChatService.STATE_CONNECTED){
//                            BluetoothDevice specifyDevice = mBluetoothAdapter.getRemoteDevice(mAddress);
//                            mChatService.connect(specifyDevice);
//                        }
                    } else {
                        if (mAddress.isEmpty()){
                            initBluetooth();
                        }
                    }
                });
    }

    //直连设备的 mac 地址
    private String mAddress = "";
    //硬编码的两个蓝牙设备 mac 地址
    //private String[] mAddressArray;

    private void initBluetooth(){
        /*BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getAddress().toUpperCase(Locale.ROOT).equals(mAddressArray[0])
                        || device.getAddress().toUpperCase(Locale.ROOT).equals(mAddressArray[1])){
                    mAddress = device.getAddress();

                    //连接指定的蓝牙设备
                    //BluetoothDevice specifyDevice = mBluetoothAdapter.getRemoteDevice(mAddress);
                    assert mChatService != null;
                    //mChatService.connect(device);
                    break;
                }
            }
            if (mAddress.isEmpty()){
                //没找到待连接的设备
                mHandler.post(() -> {
                    ToastUtils.showToast(BluetoothChatActivity.this, R.string.text_missing);
                });
            }
        } else {
            //没找到待连接的设备
            mHandler.post(() -> {
                ToastUtils.showToast(BluetoothChatActivity.this, R.string.text_missing);
            });
        }*/
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAddress = getString(R.string.mac_a);
//        mAddressArray = new String[2];
//        mAddressArray[0] = getString(R.string.mac_1);
//        mAddressArray[1] = getString(R.string.mac_2);

        setContentView(R.layout.activity_bluetooth_chat);
        init();
        Objects.requireNonNull(getSupportActionBar()).hide();  //隐藏标题栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        //创建选项菜单
        toolbar.inflateMenu(R.menu.option_menu);
        //选项菜单监听
        toolbar.setOnMenuItemClickListener(new MyMenuItemClickListener());
        mTitle = findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = findViewById(R.id.title_right_text);
        // 得到本地蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            ToastUtils.showToast(BluetoothChatActivity.this, "蓝牙不可用");
            finish();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) { //若当前设备蓝牙功能未开启
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT); //引导开启蓝牙
        } else {
            if (mChatService == null) {
                setupChat();  //创建会话
            }
        }
        //开始轮询连接指定蓝牙设备
        startLoopCheck();
    }

    private void init(){
        if (IS_DEVICE_B){//设备 B
            findViewById(R.id.in).setVisibility(View.GONE);
            findViewById(R.id.layout_button).setVisibility(View.VISIBLE);

            findViewById(R.id.btn_connect).setOnClickListener(v -> {
                //开始连接指定设备
                if (mChatService.getState() != ChatService.STATE_CONNECTING
                        && mChatService.getState() != ChatService.STATE_CONNECTED){
                    BluetoothDevice specifyDevice = mBluetoothAdapter.getRemoteDevice(mAddress);
                    mChatService.connect(specifyDevice);
                }
            });

            findViewById(R.id.btn_disconnect).setOnClickListener(v -> {
                //断开连接指定设备
                if (mChatService.getState() == ChatService.STATE_CONNECTED){
                    mChatService.stop();
                    mChatService.start();
                }
            });
        } else {//设备 A
            findViewById(R.id.in).setVisibility(View.VISIBLE);
            findViewById(R.id.layout_button).setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length>0){
            if(grantResults[0]!= PackageManager.PERMISSION_GRANTED){
                ToastUtils.showToast(BluetoothChatActivity.this, "未授权，蓝牙搜索功能将不可用！");
            }
        }
    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        if (mChatService != null) {
            if (mChatService.getState() == ChatService.STATE_NONE) {
                mChatService.start();
            }
        }
    }

    private void setupChat() {
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.item_chat);
        mConversationView = findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);
        mOutEditText = findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);
        mSendButton = findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView view = findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });
        //创建服务对象
        mChatService = new ChatService(this, mHandler);
        mOutStringBuffer = new StringBuffer("");
        ensureDiscoverable();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
            mChatService = null;
        }
        mAddress = "";
        //closeDiscoverable();
        mHandler.removeCallbacksAndMessages(null);
        mDisposable.dispose();
        System.exit(0);
    }

    private void ensureDiscoverable() { //修改本机蓝牙设备的可见性
        //打开手机蓝牙后，能被其它蓝牙设备扫描到的时间不是永久的
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            //设置在3600秒内可见（能被扫描）
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
            startActivity(discoverableIntent);
            Toast.makeText(this,
                    "已经设置本机蓝牙设备的可见性，对方可搜索了。",
                    Toast.LENGTH_SHORT).show();
            //初始化待连接蓝牙设备 mac 地址
            initBluetooth();
        }
    }

    private void closeDiscoverable(){
        //BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        try {
            Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
            setDiscoverableTimeout.setAccessible(true);
            Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class);
            setScanMode.setAccessible(true);
            setDiscoverableTimeout.invoke(mBluetoothAdapter, 1);
            setScanMode.invoke(mBluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String message) {
        if (mChatService.getState() != ChatService.STATE_CONNECTED) {
            ToastUtils.showToast(BluetoothChatActivity.this, R.string.not_connected);
            return;
        }
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            mChatService.write(send);
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }
    private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                //软键盘里的回车也能发送消息
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };
    //使用Handler对象在UI主线程与子线程之间传递消息
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {   //消息处理
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatService.STATE_CONNECTED:
                            mTitle.setText(R.string.title_connected_to);
                            mTitle.append(mConnectedDeviceName);
                            mConversationArrayAdapter.clear();
                            if (IS_DEVICE_B){
                                findViewById(R.id.layout_type).setVisibility(View.VISIBLE);
                            }
                            break;
                        case ChatService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;
                        case ChatService.STATE_LISTEN:
                        case ChatService.STATE_NONE:
                            mTitle.setText(R.string.title_not_connected);
                            if (IS_DEVICE_B){
                                findViewById(R.id.layout_type).setVisibility(View.GONE);
                            }
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("我:  " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  "
                            + readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    ToastUtils.showToast(BluetoothChatActivity.this, "链接到 " + mConnectedDeviceName);
                    break;
                case MESSAGE_TOAST:
                    String str = msg.getData().getString(TOAST);
                    ToastUtils.showToast(BluetoothChatActivity.this, str);
                    if (str.equals(getString(R.string.text_not_found))
                            || str.equals(getString(R.string.text_break))){
                        //改成连接中文案
                        if (mTitle != null){
                            mTitle.setText(R.string.title_not_connected);
                        }
                    }
                    break;
            }
        }
    };

    //返回进入好友列表操作后的数回调方法
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE://开始连接到另一台蓝牙设备
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActiity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    if (mChatService != null
                            && !mChatService.getCacheAddress().isEmpty()
                            && !mChatService.getCacheAddress().equals(address)){
                        //连接到一台新设备，需要做些准备工作
                        mChatService.stop();
                        mChatService.start();
                        mHandler.postDelayed(() ->{
                            //延迟操作，等准备工作做完
                            assert mChatService != null;
                            mChatService.connect(device);
                        }, 1000);
                        return;
                    }
                    assert mChatService != null;
                    mChatService.connect(device);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    ToastUtils.showToast(BluetoothChatActivity.this, "未选择任何好友！");
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupChat();
                } else {
                    ToastUtils.showToast(BluetoothChatActivity.this, R.string.bt_not_enabled_leaving);
                    finish();
                }
        }
    }

    private class MyMenuItemClickListener implements Toolbar.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                /*case R.id.scan:
                    //启动DeviceList这个Activity
                    Intent serverIntent = new Intent(BluetoothChatActivity.this, DeviceListActiity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                    return true;*/
                case R.id.discoverable:
                    ensureDiscoverable();
                    return true;
                /*case R.id.back:
                    finish();
                    System.exit(0);
                    return true;*/
            }
            return false;
        }
    }
}