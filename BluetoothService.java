package com.example.main;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.example.main.Request.BluetoothRequest;
import com.example.main.Request.FeedbackRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class BluetoothService extends Service {

    private final IBinder mBinder = new LocalBinder();
    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    private MyQueue tempQueue = new MyQueue(11);
    private MyQueue heartQueue = new MyQueue(11);
    private MyQueue muscleQueue = new MyQueue(11);


    private static int accel_x, accel_y, accel_z = 0;
    //private static int temp = 0;


    private static String msg;
    private int i = 0;
    private SimpleDateFormat dformat = new SimpleDateFormat("yyyy/MM/dd");
    private SimpleDateFormat tformat = new SimpleDateFormat("HH:mm");
    private SimpleDateFormat tsformat = new SimpleDateFormat("HH:mm:ss");
    private Date dt;
    private String id = ""; //???????????? ???????????? ?????? ????????? ?????????
    boolean notnom = false; // ????????? ?????? ????????? ???????????? ?????? ??????
    private int a = 0;
    private int b = 0;
    String beforet = ""; // ?????? ????????? ??? ???????????? ??????
    String beforeh = "";
    String beforem = "";
    private String limit_d = "";
    private String modecheck = "";
    private String name = "?????????";
    private String docID;


    private static StringBuffer str = new StringBuffer();
    static String str1;

    double t_avg = 0.0; //??? ?????? ????????? ?????? ?????? ????????????
    int h_avg = 0;
    int Rm_avg = 0;
    int Lm_avg = 0;

    double t_sum = 0; //?????? ?????? ???
    int h_sum = 0;
    int Rm_sum = 0;
    int Lm_sum = 0;

    int end = 10; // ????????? ???????????? ????????? 1????????? ?????? ????????? ????????????.

    private AES256Chiper aes256Chiper;

    private boolean check_t = false;
    private boolean check_h = false;
    private boolean check_m = false; // ????????? ?????? ???????????? ??????

    private static int before_x = 0;
    private static int before_y = 0;
    private static int before_z = 0;
    private static int before_svm = 0;

    public static int mode = 1;
    public static int sensor_count = 1;
    private static int ABS = 0, SVM = 0;

    public static Intent serviceIntent = null;
    private static String Device_name = "";

    String key = "qwertyuiopasdfghjklzxcvbnmqwerty";
    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    BluetoothRequest bluetoothRequest2;
    RequestQueue queue;

    private Handler mBluetoothHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == BT_MESSAGE_READ) {
                String readMessage = null;
                try {
                    readMessage = new String((byte[]) msg.obj, "UTF-8");

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                getMsg(readMessage);

            }
        }
    };

    Response.Listener<String> responseListener = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            if (response != null) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    boolean success_t = jsonObject.getBoolean("success1");
                    boolean success_h = jsonObject.getBoolean("success2");
                    boolean success_m = jsonObject.getBoolean("success3");

                    if (success_t) {
                        System.out.println("????????? ????????????");
                        if (success_h) {
                            System.out.println("?????? ??? ????????????");
                            if (success_m) {
                                System.out.println("????????? ??? ????????????");
                            } else {
                                System.out.println("????????? ??? ????????????");
                            }
                        } else {
                            System.out.println("?????? ??? ????????????");
                        }
                    } else {
                        System.out.println("????????? ????????????");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
            }
        }

        public void onErrorResponse(VolleyError error) {
            Log.e("TAG", "Error at sign in : " + error.getMessage());
        }
    };

    Response.Listener<String> fbrListener = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            try {
                JSONObject jsonObject = new JSONObject(response);
                boolean success = jsonObject.getBoolean("success");
                if (success) {
                    System.out.println("????????? ?????? ??????");
                } else {
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };


    public void getMsg(String msg) {
        this.msg = msg;
    }

    public String returnMsg() {
        if (!msg.equals(null))
            return this.msg;
        else {
            return "null";
        }
    }

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //Requestalarm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        id = intent.getStringExtra("userID");
        name = intent.getStringExtra("userName");
        docID = intent.getStringExtra("docID");
        /*
        Device_name = intent.getStringExtra("Device");
        if (!Device_name.equals("null_device")) {
            connectSelectedDevice(Device_name);
        }
         */
        serviceIntent = intent;
        return START_NOT_STICKY;
    }



    @Override
    public void onDestroy() {
        serviceIntent = null;
        //setAlarmTimer();
        super.onDestroy();

    }

    protected void setAlarmTimer() {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.add(Calendar.SECOND, 1);
        Intent intent = new Intent(this, ServiceBluetoothReceiver.class);
        intent.putExtra("userID", id);
        intent.putExtra("Device", Device_name);
        intent.putExtra("userName", name);
        intent.putExtra("docID", docID);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);

        AlarmManager mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);
    }

    public void bluetoothOn() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ?????? ???????????????.", Toast.LENGTH_LONG).show();
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "??????????????? ?????? ????????? ?????? ????????????.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "??????????????? ????????? ?????? ?????? ????????????.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void test() {
        System.out.println("???????????? ????????? ?????????");
    }

    public void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "??????????????? ?????? ???????????? ?????? ????????????.", Toast.LENGTH_SHORT).show();
        }
    }

    public void setmBluetoothAdapter(BluetoothAdapter mBluetoothAdapter) {
        this.mBluetoothAdapter = mBluetoothAdapter;
    }


    public CharSequence[] getItems() {
        if (mBluetoothAdapter.isEnabled()) {
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                return items;
            } else {
                Toast.makeText(getApplicationContext(), "???????????? ????????? ????????????.", Toast.LENGTH_LONG).show();
                return null;
            }
        } else {
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ?????? ????????????.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    public void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);


            } else {
                Toast.makeText(getApplicationContext(), "???????????? ????????? ????????????.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "??????????????? ???????????? ?????? ????????????.", Toast.LENGTH_SHORT).show();
        }
    }

    void connectSelectedDevice(String selectedDeviceName) {
        for (BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                Device_name = selectedDeviceName;
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS).sendToTarget();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "???????????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
        }
    }

    public class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "?????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public void run() {
            String[] value;
            byte[] buffer = new byte[1024];
            byte[] empty = new byte[buffer.length];
            int bytes, i = 0, j = 0;
            boolean first = true; // ?????? ?????? ?????????????????? ?????????
            queue = Volley.newRequestQueue(getApplicationContext());

            before_x = 1200;
            before_y = 15700;
            before_z = 4750;

            while (true) {
                SystemClock.sleep(2000); //1?????????

                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        System.arraycopy(empty, 0, buffer, 0, buffer.length);
                        bytes = mmInStream.read(buffer, 0, bytes);

                        if (i == 2) {
                            i = 1;
                        }
                        str1 = new String((byte[]) buffer).split("@")[i++];
                        value = str1.split("/");

                        //value = new String((byte[]) buffer).split("/");



                        if (sensor_count != 5) {


                            accel_x = Integer.parseInt(value[4]);
                            accel_y = Integer.parseInt(value[5]);
                            accel_z = Integer.parseInt(value[6]);


                            ABS = (int) (Math.abs(Math.sqrt(Math.pow(accel_x, 2) + Math.pow(accel_y, 2) + Math.pow(accel_z, 2))
                                    - Math.sqrt(Math.pow(before_x, 2) + Math.pow(before_y, 2) + Math.pow(before_z, 2))));
                            SVM = (int) Math.sqrt(Math.pow(accel_x - before_x, 2) + Math.pow(accel_y - before_y, 2) + Math.pow(accel_z - before_z, 2));
                            if (ABS > 15000) {
                                if (SVM - before_svm > 1000) {
                                    j++;
                                    if(j>2)
                                        call119("true");
                                }
                            } else {
                                call119("false");
                            }
                            before_x = accel_x;
                            before_y = accel_y;
                            before_z = accel_z;
                            before_svm = SVM;


                            sensor_count++;

                        } else if (sensor_count == 5)
                        {
                            accel_x = Integer.parseInt(value[4]);
                            accel_y = Integer.parseInt(value[5]);
                            accel_z = Integer.parseInt(value[6]);

                            ABS = (int) (Math.abs(Math.sqrt(Math.pow(accel_x, 2) + Math.pow(accel_y, 2) + Math.pow(accel_z, 2))
                                    - Math.sqrt(Math.pow(before_x, 2) + Math.pow(before_y, 2) + Math.pow(before_z, 2))));
                            SVM = (int) Math.sqrt(Math.pow(accel_x - before_x, 2) + Math.pow(accel_y - before_y, 2) + Math.pow(accel_z - before_z, 2));
                            if (ABS > 15000) {
                                if (SVM - before_svm > 1000) {
                                    j++;
                                    if(j>2)
                                        call119("true");
                                }
                            } else {
                                call119("false");
                            }
                            before_x = accel_x;
                            before_y = accel_y;
                            before_z = accel_z;
                            before_svm = SVM;


                            sensor_count = 0;


                            Intent intent = new Intent("com.example.action.bluetooth.receive");
                            intent.putExtra("Temp", value[0]);
                            intent.putExtra("Heart", value[1]);
                            intent.putExtra("Muscle_L", value[2]);
                            intent.putExtra("Muscle_R", value[3]);

                            intent.putExtra("ABS", String.valueOf(ABS));
                            intent.putExtra("SVM", String.valueOf(SVM));
                            intent.putExtra("Test", "x"+value[4]+"y"+value[5]+"z"+value[6]);
                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);


                            //?????????????????? ????????????

                            Double temp = Double.parseDouble(value[0]); //????????? ?????????
                            int heart = Integer.parseInt(value[1]);
                            int Rmuscle = Integer.parseInt(value[2]);
                            int Lmuscle = Integer.parseInt(value[3]);

                            Calendar day = Calendar.getInstance();
                            day.setTime(new Date());
                            day.add(Calendar.DATE, 1);
                            String comparedate = dformat.format(day.getTime()); //?????? ????????? ??????


                            dt = new Date();
                            String date = dformat.format(dt); //??????
                            String stime = tsformat.format(dt); //??????(???)
                            String time = tformat.format(dt);//??????(???)

                            String en_t = temp + "," + date + "," + stime; // ???????????? ?????? ?????? '??? ?????? ???????????????'
                            String en_h = heart + "," + date + "," + stime;
                            String en_m = Rmuscle + "," + Lmuscle + "," + date + "," + stime;


                            if (tempQueue.isFull() && heartQueue.isFull()) { //?????? ??? ??? ???????????? ????????? ???????????? ????????????.

                                if (first) { // ????????????????????? ?????? ????????? ??? ??????.
                                    for (int k = 0; k < 11; k++) {
                                        if (k != tempQueue.getFront()) {
                                            t_sum += Double.parseDouble(tempQueue.peek(k).split(",")[0]);
                                        }
                                        if (k != heartQueue.getFront()) {
                                            h_sum += Integer.parseInt(heartQueue.peek(k).split(",")[0]);
                                        }
                                        if (k != muscleQueue.getFront()) {
                                            Rm_sum += Integer.parseInt(muscleQueue.peek(k).split(",")[0]);
                                            Lm_sum += Integer.parseInt(muscleQueue.peek(k).split(",")[1]);
                                        }
                                    }
                                    first = false;
                                } else { //?????? ???????????? ????????? ????????? ????????? ????????? ????????? ?????? ????????? ?????? ?????????.
                                    t_sum = t_sum - Double.parseDouble(beforet.split(",")[0]) + temp;
                                    h_sum = h_sum - Integer.parseInt(beforeh.split(",")[0]) + heart;
                                    Rm_sum = Rm_sum - Integer.parseInt(beforem.split(",")[0]) + Rmuscle;
                                    Lm_sum = Lm_sum - Integer.parseInt(beforem.split(",")[0]) + Lmuscle;
                                }

                                t_avg = t_sum / 10; //3????????????
                                h_avg = h_sum / 10;
                                Rm_avg = Rm_sum / 10;
                                Lm_avg = Lm_sum / 10;


                                if (Rm_avg - Lm_avg >= 300 || Rm_avg - Lm_avg <= -300) {
                                    check_m = true;
                                }

                                switch (mode) { //mode??? ?????????
                                    case 0:
                                        //
                                        //????????? ??????
                                        break;
                                    case 1: //????????????
                                        if (t_avg >= 37.5 || t_avg <= 33.0) {
                                            check_t = true;
                                        }
                                        if (h_avg >= 100 || h_avg <= 60) {
                                            check_h = true;
                                        }
                                        break;
                                    case 2: //????????????
                                        if (t_avg >= 37.5 || t_avg <= 33.0) {
                                            check_t = true;
                                        }
                                        if (h_avg >= 90 || h_avg <= 50) {
                                            check_h = true;
                                        }
                                        break;
                                    case 3: //????????????
                                        if (t_avg >= 39 || t_avg <= 33.0) {
                                            check_t = true;
                                        }
                                        break;
                                    default:
                                        break;
                                }


                                if (a == 10) {
                                    t_avg = Double.parseDouble(String.format("%.1f", t_avg)); //???????????????.
                                    try {
                                        String e_id = aes256Chiper.encoding(id, key);
                                        String e_tavg = aes256Chiper.encoding(Double.toString(t_avg), key);
                                        String e_havg = aes256Chiper.encoding(Integer.toString(h_avg), key);
                                        String e_Rmavg = aes256Chiper.encoding(Integer.toString(Rm_avg), key);
                                        String e_Lmavg = aes256Chiper.encoding(Integer.toString(Lm_avg), key);


                                        bluetoothRequest2 = new BluetoothRequest(e_id, e_tavg, e_havg, e_Rmavg, e_Lmavg, date, time, responseListener);

                                        queue.add(bluetoothRequest2); //???????????????
                                        a = 0; // 180?????? ?????????. 180?????? ?????? ??????????????? ?????? ??????


                                        System.out.println("?????????????????????????????? ????????? : " + t_avg + " : ?????? : " + a);
                                        System.out.println("----------- ????????? -----------\n");
                                        System.out.println("\n");

                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchPaddingException e) {
                                        e.printStackTrace();
                                    } catch (InvalidKeyException e) {
                                        e.printStackTrace();
                                    } catch (InvalidAlgorithmParameterException e) {
                                        e.printStackTrace();
                                    } catch (IllegalBlockSizeException e) {
                                        e.printStackTrace();
                                    } catch (BadPaddingException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (check_t || check_h || check_m) { // ??? ???????????? ?????????????????????, ?????????????????????, ?????????????????????
                                    int n = 1;
                                    String t[] = new String[3];
                                    String h[] = new String[3];

                                    if (check_m == true) { // ??????????????????
                                        System.out.println("????????? ??????????????????. ?????? ????????? ????????? ?????????."); //?????? ????????????????????? ????????????????????? ?????? ( ????????????)
                                    }
                                    if (notnom == false) { // ???????????? ???????????? ?????? ??????
                                        while (n < 11) {
                                            if (n != tempQueue.getFront()) { // n??? ?????? ???????????? ????????? ???(?????? ???????????? ???) ??? ????????????(????????? ?????? ????????????)
                                                t = tempQueue.peek(n).split(",");
                                            }
                                            if (n != heartQueue.getFront()) {
                                                h = heartQueue.peek(n).split(",");
                                            }
                                            if (mode == 3) { // ??????????????? ?????? ?????? ?????? 0?????? ?????????.
                                                h[0] = "0";
                                            }
                                            System.out.println("a??? : " + a);

                                            try {
                                                String e_id = aes256Chiper.encoding(id, key);
                                                String e_t_value = aes256Chiper.encoding(t[0], key);
                                                String e_h_value = aes256Chiper.encoding(h[0], key);

                                                BluetoothRequest bluetoothRequest = new BluetoothRequest(e_id, e_t_value,
                                                        date, time, e_h_value, responseListener);

                                                queue.add(bluetoothRequest);

                                            } catch (NoSuchAlgorithmException e) {
                                                e.printStackTrace();
                                            } catch (NoSuchPaddingException e) {
                                                e.printStackTrace();
                                            } catch (InvalidKeyException e) {
                                                e.printStackTrace();
                                            } catch (InvalidAlgorithmParameterException e) {
                                                e.printStackTrace();
                                            } catch (IllegalBlockSizeException e) {
                                                e.printStackTrace();
                                            } catch (BadPaddingException e) {
                                                e.printStackTrace();
                                            }

                                            n++;
                                        }
                                        String symptom = "";
                                        if (check_t) {
                                            symptom = "????????????";
                                        }
                                        if (check_h) {
                                            if (!symptom.equals("")) {
                                                symptom = symptom + " ??? ????????????";
                                            } else {
                                                symptom = "????????????";
                                            }
                                        }
                                        //??????????????? ????????? ??????

                                        String m = "";
                                        switch (mode) {
                                            case 1:
                                                m = "????????????";
                                                break;
                                            case 2:
                                                m = "????????????";
                                                break;
                                            case 3:
                                                m = "????????????";
                                                break;
                                            default:
                                                break;
                                        }


                                        try {
                                            String e_id = aes256Chiper.encoding(id, MainActivity.key);
                                            String e_date = date;
                                            String e_time = time;
                                            String e_symptom = aes256Chiper.encoding(symptom, MainActivity.key);
                                            String e_mode = aes256Chiper.encoding(m, MainActivity.key);
                                            String e_name = aes256Chiper.encoding(name, MainActivity.key);
                                            String e_new = aes256Chiper.encoding("0", MainActivity.key);
                                            String e_doc = aes256Chiper.encoding(docID, MainActivity.key);

                                            if ("".equals(limit_d)) { //?????????????????? ?????????????????? ?????? ???
                                                FeedbackRequest fbAlarmRequest
                                                        = new FeedbackRequest(e_id, e_date + " " + e_time, e_symptom, e_mode, e_doc, e_name, e_new, fbrListener);
                                                queue.add(fbAlarmRequest);
                                                limit_d = setFbDate();
                                            } else if (limit_d.equals(comparedate) && !(modecheck.contains(mode + ""))) { //??????????????? ?????? ????????? ?????? ???????????? ????????????
                                                FeedbackRequest fbAlarmRequest = new FeedbackRequest(e_id, e_date + " " + e_time, e_symptom, e_mode, e_doc, e_name, e_new, fbrListener);
                                                queue.add(fbAlarmRequest);
                                                modecheck += mode + "";
                                            } else if (limit_d.equals(date) || (!limit_d.equals(comparedate) && !limit_d.equals(date))) { //??????????????????????????? ????????????????????? ?????? ?????? ??????????????? ???
                                                FeedbackRequest fbAlarmRequest = new FeedbackRequest(e_id, e_date + " " + e_time, e_symptom, e_mode, e_doc, e_name, e_new, fbrListener);
                                                queue.add(fbAlarmRequest);
                                                limit_d = setFbDate();
                                            }
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchPaddingException e) {
                                            e.printStackTrace();
                                        } catch (InvalidKeyException e) {
                                            e.printStackTrace();
                                        } catch (InvalidAlgorithmParameterException e) {
                                            e.printStackTrace();
                                        } catch (IllegalBlockSizeException e) {
                                            e.printStackTrace();
                                        } catch (BadPaddingException e) {
                                            e.printStackTrace();
                                        }
                                            notnom = true;
                                        } else{
                                            //?????? ????????? ?????? ?????????????????? ?????? ?????? ?????? ??????????????? ???????????? ????????????????????? ????????? ??????,
                                            //?????? 180?????? ??????????????? ????????? ??????????????? ??? ????????? ????????? ????????? 180??? ?????? ?????????.
                                            end++;
                                        }
                                    }
                                    if (notnom == true) { // ?????? ????????? ????????????????????? ??????
                                        String[] t = en_t.split(",");
                                        String[] h = en_h.split(",");

                                        if (mode == 3) { // ??????????????? ?????? ?????? ?????? 0?????? ?????????.
                                            h[0] = "0";
                                        }

                                        try {
                                            String e_id = aes256Chiper.encoding(id, key);
                                            String e_t_sec = aes256Chiper.encoding(t[0], key);
                                            String e_h_sec = aes256Chiper.encoding(h[0], key);

                                            BluetoothRequest bluetoothRequest = new BluetoothRequest(e_id, e_t_sec,
                                                    date, time, e_h_sec, responseListener);

                                            queue.add(bluetoothRequest);

                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        } catch (NoSuchPaddingException e) {
                                            e.printStackTrace();
                                        } catch (InvalidKeyException e) {
                                            e.printStackTrace();
                                        } catch (InvalidAlgorithmParameterException e) {
                                            e.printStackTrace();
                                        } catch (IllegalBlockSizeException e) {
                                            e.printStackTrace();
                                        } catch (BadPaddingException e) {
                                            e.printStackTrace();
                                        }

                                        b++;
                                        if (b == end) {
                                            notnom = false;
                                            b = 0;
                                            end = 10;
                                        }
                                    }
                                    beforet = tempQueue.dequeue();
                                    beforeh = heartQueue.dequeue();
                                    beforem = muscleQueue.dequeue();
                                    heartQueue.enqueue(en_h);
                                    tempQueue.enqueue(en_t); //????????? ?????? ?????? ?????? ?????? ??????.
                                    muscleQueue.enqueue(en_m);
                                } else { //?????? ?????? ??????????????? ????????????,
                                    heartQueue.enqueue(en_h);
                                    tempQueue.enqueue(en_t); //?????? 1??????????????? ??? ??????.
                                    muscleQueue.enqueue(en_m);


                                }
                                a++;



                            }
                        }
                    }catch(IOException e){
                        break;
                    }



                }


            }



        private void call119(String value) {
            String tel = "tel:119";
            Intent showIntent = new Intent("tell");
            showIntent.putExtra("check",value);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(showIntent);
        }
        private String setFbDate() { // ?????? ?????? ?????????
            Calendar date = Calendar.getInstance();
            date.setTime(new Date());
            date.add(Calendar.DATE,1);
            String day = dformat.format(date.getTime());
            modecheck = "";
            modecheck += mode+"";
            return day;
        }
        public void write(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "????????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "?????? ?????? ??? ????????? ??????????????????.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void Requestalarm() {
        Intent intent = new Intent("com.example.action.bluetoothservice.alarm");
        intent.putExtra("alarm_request", "vibrate");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }



    interface QueueInterface {

        public abstract boolean isFull(); // ?????? ?????????????????? ???????????? ?????????

        public abstract boolean isEmpty(); // ?????? ??????????????? ???????????? ?????????

        public abstract void enqueue(String val); // ?????? ???????????? ???????????? ?????????

        public abstract String dequeue(); // ?????? ???????????? ???????????? ???????????? ?????????

        public abstract String peek(int index); // ????????? dequeue ??? ????????? ???????????? ?????????

    }

    public class MyQueue implements QueueInterface {

        int front; // ?????? ?????????
        int rear; // ?????? ?????????
        int Qsize; // ?????? ?????? ?????????
        String[] QArray; // Qsize??? ???????????? ?????? ?????? ??????

        public MyQueue(int Qsize) { // ?????? ???????????? ?????????
            front = 0; //
            rear = 0; // ????????? ??????,?????? ???????????? ?????? 0????????? ???????????? ??????.
            this.Qsize = Qsize;
            QArray = new String[Qsize];
            System.out.println("???????????? : " + Qsize);
        }

        public int getFront() {
            return front;
        }

        public int getRear() {
            return rear;
        }

        @Override
        public boolean isFull() { // ?????? ?????? ???????????? ???????????? ?????????
            if (((rear + 1) % this.Qsize) == front) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isEmpty() { // ?????? ??????????????? ???????????? ?????????.
            return rear == front; // rear??? front??? ???????????? ???????????? ????????? ?????? ????????? ???????????? ????????? ?????? ???????????? ??????????????? ??????.
        }

        @Override
        public void enqueue(String val) {
            if (isFull()) { // ?????? ????????? ?????? ????????? ??????.
                System.out.println("?????? ??? ??? ????????????.");
            } else {
                rear = (++rear) % this.Qsize; // ?????????????????? ??????????????? 1?????????. ????????? 3->0?????? ??????????????? ???????????????
                QArray[rear] = val; // ??????????????? rear??? ???????????? ????????? ????????? ??????.
                System.out.println(val + "??? " + rear + "?????????" + " ??????");
            }
        }

        @Override
        public String dequeue() { //????????? ??? ( ?????? ?????? ????????? ??? ?????? )
            if (isEmpty()) {
                return "0";
            } else {
                front = (++front) % this.Qsize;
                return QArray[(front) % Qsize];
            }
        }

        @Override
        public String peek(int index) {
            System.out.println(QArray[(front + 1) % this.Qsize]);
            return QArray[index];
        }

    }
}