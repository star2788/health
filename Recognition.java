package com.example.main;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;

import com.example.main.Request.RecognitionRequest;
import com.example.main.interfaces.DialogflowBotReply;
import com.example.main.utils.SendMessageInBg;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import io.opencensus.metrics.LongGauge;

import static android.speech.tts.TextToSpeech.ERROR;

public class Recognition extends RecognitionService implements DialogflowBotReply {

    public static final int MSG_VOICE_RECO_READY = 0;
    public static final int MSG_VOICE_RECO_END = 1;
    public static final int MSG_VOICE_RECO_RESTART = 2;
    boolean mBoolVoiceRecoStarted;

    int rec_case = 0, check = 0;
    boolean timelist[] = new boolean[8];

    private Intent mIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    private TextToSpeech tts;
    SpeechRecognizer mRecognizer;
    private String msg, rsp;
    private Handler mHandler = new Handler(Looper.getMainLooper()); //tts??? ?????? ?????? ?????? ?????? ???????????????
    private SessionsClient sessionsClient;
    private SessionName sessionName;
    private String uuid = UUID.randomUUID().toString();
    private static String TAG = "tag";
    private String userID = "";
    private RecognitionRequest recognitionRequest;
    private String date;
    private String receiver = "", docName = "";
    private RequestQueue queue;
    private SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static int mode = 0;
    // 0 : ??????????????????, 1 : ????????????, 2 : ????????????, 3 : ????????????
    public static Intent serviceIntent = null;
    private AES256Chiper aes256Chiper;
    private boolean checkreceiver = false;
    private Frag_main frag_main;


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreate() {
        super.onCreate();
        queue = Volley.newRequestQueue(getApplicationContext());
        aes256Chiper = new AES256Chiper();
        frag_main = new Frag_main();
        setUpBot();
        startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceIntent = intent;
        userID = intent.getStringExtra("userID");
        receiver = intent.getStringExtra("docID");
        docName = intent.getStringExtra("docName");

        return super.onStartCommand(intent, flags, startId);
    }

    protected void setAlarmTimer() {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.add(Calendar.SECOND, 1);
        Intent intent = new Intent(this, ServiceAlarmReceiver.class);
        intent.putExtra("userID",userID);
        intent.putExtra("docName", docName);
        intent.putExtra("docID",receiver);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0,intent,0);

        AlarmManager mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mAlarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), sender);
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(tts != null) {

            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS Destroyed");
        }
        serviceIntent = null;
        //setAlarmTimer();

    }
    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {

    }

    private Handler mHdrVoiceRecoState = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_VOICE_RECO_READY:
                    break;
                case MSG_VOICE_RECO_END: {
                    stopListening();
                    sendEmptyMessageDelayed(MSG_VOICE_RECO_RESTART, 1500);
                    break;
                }
                case MSG_VOICE_RECO_RESTART:
                    startListening();
                    break;
                default:
                    super.handleMessage(msg);
            }

        }
    };

    public void startListening(){  //???????????? ??????(??????)
        Log.v("????????????","??????????????????");
        Toast.makeText(getApplicationContext(),"startListening",Toast.LENGTH_LONG);
        mIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,this.getPackageName());
        mIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");

        if (tts == null){
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != ERROR) {
                        tts.setLanguage(Locale.KOREAN);
                        System.out.println("????????????????????????????????????????????????");
                    }
                }
            });
            Log.v("????????????","tts??????");
        }

        mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
            @Override
            public void run() {

                if (mBoolVoiceRecoStarted == false) // ?????? ?????? ?????? ?????? ????????????,
                {
                    if (mRecognizer == null) //????????? ????????????????????? ?????????,
                    {
                        mRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                        mRecognizer.setRecognitionListener(listener);
                    }
                    if (mRecognizer.isRecognitionAvailable(getApplicationContext())) //????????????????????? ?????? ????????? ????????????(????????? ?????????)
                    {
                        //????????? ??????(?????????????????? ??????)
                        mIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
                        mIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, (getApplication()).getPackageName());
                        mIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
                        mIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 50);

                        mRecognizer.startListening(mIntent);


                    }
                }

                mBoolVoiceRecoStarted = true; // ???????????? ????????? ??????

            }
        }, 3000);

    }

    public void stopListening() //???????????? ??????
    {
        try {
            if (mRecognizer != null && mBoolVoiceRecoStarted == true) // ??????????????????????????? ???????????????, ?????? ????????? ????????????,
            {
                mRecognizer.stopListening(); // ?????? ??????
                mRecognizer.destroy();
                mRecognizer = null;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        mBoolVoiceRecoStarted = false; // ???????????? ????????? ?????? ?????? ??????
    }

    @Override
    protected void onCancel(Callback listener) {
    }

    @Override
    protected void onStopListening(Callback listener) {
    }

    private RecognitionListener listener = new RecognitionListener() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onReadyForSpeech(Bundle params) {
            //Toast.makeText(getApplicationContext(), "??????????????? ???????????????.", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.v("????????????","6");

        }

        @Override
        public void onError(int error) {
            String message;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "??????????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "???????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "????????? ????????????";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "?????? ??? ??????";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER??? ??????";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "????????? ?????????";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "????????? ????????????";
                    break;
                default:
                    message = "??? ??? ?????? ?????????";
                    break;
            }
            //Toast.makeText(getApplicationContext(), "????????? ?????????????????????. 1 : " + message, Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onResults(Bundle results) { // ?????? ?????? ArrayList??? ????????? ?????? textView??? ????????? ???????????????.
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            msg = matches.get(0);
            System.out.println("msg : " + msg);


            if ("????????????".equals(msg)) {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                //Toast.makeText(getApplicationContext(), "??????????????? ???????????????.", Toast.LENGTH_SHORT).show();
                readText("??? ????????? ???????????????????");
                mHandler.postDelayed(new Runnable() { //tts???????????? ?????????
                    @Override
                    public void run() {
                        mRecognizer.setRecognitionListener(listener1); // ?????? ????????? ?????? ?????? ??? ??????
                        mRecognizer.startListening(mIntent);
                    }
                }, 3000);
            } else {
                mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                    @Override
                    public void run() {
                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                    }
                }, 3000);
            }


        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    };

    private RecognitionListener listener1 = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(getApplicationContext(), "??????????????????", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            MainActivity.checkreceiver = false;
        }

        @Override
        public void onError(int error) {
            String message;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "??????????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "???????????? ??????";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "????????? ????????????";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "?????? ??? ??????";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER??? ??????";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "????????? ?????????";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "????????? ????????????";
                    break;
                default:
                    message = "??? ??? ?????? ?????????";
                    break;
            }
            Toast.makeText(getApplicationContext(), "????????? ?????????????????????.2 : " + message, Toast.LENGTH_SHORT).show();

            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @Override
        public void onResults(Bundle results) { // ?????? ?????? ArrayList??? ????????? ?????? textView??? ????????? ???????????????.

            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            msg = matches.get(0); //????????? ????????? ??????

            System.out.println("????????? : " + msg);
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            sendMessageToBot(msg);//??????????????? ???????????? ??????


            mHandler.postDelayed(new Runnable() {//???????????????
                public void run() {
                    System.out.println("392rsp"+rsp);
                    Log.d("123Rsp",rsp);
                    if ("??????".equals(rsp) || "??????".equals(rsp)) {
                        readText("????????? ?????? ????????? ?????? ???????????????.");
                        rec_case = 0;
                    } else if (rsp == null || rsp =="" || rsp =="null") { //????????? ??? (??????) ????????? ???
                        readText("?????? ????????? ??? ?????? ?????? ????????????.");
                        rec_case = 0;
                    } else {
                        String object = rsp.split("/")[0]; // ??????
                        String action = rsp.split("/")[1]; // ????????????
                        System.out.println("obj : " + object);
                        System.out.println("act : " + action);

                        if ("??????".equals(object)) { //?????? ???????????? ??????
                            if ("??????".equals(action) || "???".equals(action)) {
                                // ????????????
                                readText("?????? ????????? ???????????????????");
                                rec_case = 2;

                            } else { //????????? ???????????? ?????????
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("??????".equals(object)) {
                            if ("??????".equals(action)) {
                                int check = 0;
                                readText("????????? ????????? ????????? ?????????");
                                rec_case = 1;

                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("?????????".equals(object)) {
                            if ("??????".equals(action) || "???".equals(action)) {
                                readText("????????? ????????? ????????? ????????? ?????????");
                                rec_case = 3;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("?????????".equals(object)) {
                            if ("??????".equals(action)) {
                                readText("????????? ????????? ????????? ???????????????");
                                rec_case = 4;
                            } else if ("???".equals(action) || "??????".equals(action)) {
                                readText("?????? ????????? ????????? ???????????? ???????????????????");
                                rec_case = 5;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("????????????".equals(object)) {
                            if ("??????".equals(action)) {
                                //BluetoothService.mode = 3;
                                rec_case = 0;
                            } else if ("??????".equals(action)) {
                                //BluetoothService.mode = 1;
                                rec_case = 0;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("????????????".equals(object)) {
                            if ("??????".equals(action)) {
                                // BluetoothService.mode = 2;
                                rec_case = 0;
                            } else if ("??????".equals(action)) {
                                //BluetoothService.mode = 1;
                                rec_case = 0;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("????????????".equals(object)) {
                            if ("??????".equals(action)) {
                                //BluetoothService.mode = 1;
                                rec_case = 0;
                            } else if ("??????".equals(action)) {
                                // BluetoothService.mode = 0;
                                rec_case = 0;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("????????? ??????".equals(object)) {
                            if ("??????".equals(action)) {
                                replace_muscle();
                                rec_case = 0; // ????????? ????????? ???????????? ???????????? ?????? ???????????? ???????????? ????????????
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        } else if ("?????????".equals(object)) {
                            if ("??????".equals(action)) {
                                replace_confirm();
                                rec_case = 0;
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                rec_case = 0;
                            }
                        }
                        else{
                            readText("?????? ??????????????? ??? ???????????????.");
                            rec_case = 0;
                        }
                    }

                    switch (rec_case) {
                        case 0:
                            System.out.println("rec_case = 0");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                                }
                            },3000);
                            break;
                        case 1: //?????? ??????
                            System.out.println("rec_case = 1");

                            mHandler.postDelayed(new Runnable() {
                                public void run() {
                                    check = 0;
                                    mRecognizer.setRecognitionListener(listener2); // ?????? ????????? ?????? ?????? ??? ??????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                            break;
                        case 2:
                            System.out.println("rec_case = 2");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    check = 0;
                                    mRecognizer.setRecognitionListener(listener3); // ?????????????????? ?????????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                            break;
                        case 3:
                            System.out.println("rec_case = 3");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    check = 0;
                                    mRecognizer.setRecognitionListener(listener5); // ?????????????????? ?????????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                            break;
                        case 4: //ok
                            System.out.println("rec_case = 4");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    check = 0;
                                    MainActivity.checkreceiver = true;
                                    mRecognizer.setRecognitionListener(listener7); // ????????? ?????? ?????????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                            break;
                        case 5:
                            System.out.println("rec_case = 5");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    check = 0;
                                    mRecognizer.setRecognitionListener(listener8); // ?????? ?????? ????????? ?????? ?????????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                            break;
                    }

                }
            }, 3000);


        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

    };
    private RecognitionListener listener3 = new RecognitionListener() { //?????? ???????????? ???????????? ?????????
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();

            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String msg = result.get(0);

            sendMessageToBot(msg);//??????????????? ???????????? ??????

            mHandler.postDelayed(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void run() {
                    if ("???".equals(rsp) || "???".equals(rsp)) {
                        try {
                            readMyRes(); //???????????? ??????????????? ???????????? ????????? ?????????
                        } catch (NoSuchPaddingException e) {
                            e.printStackTrace();
                        } catch (InvalidAlgorithmParameterException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (IllegalBlockSizeException e) {
                            e.printStackTrace();
                        } catch (BadPaddingException e) {
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            e.printStackTrace();
                        }
                    } else if ("??????".contains(rsp) || "??????".contains(rsp)) {
                        readText("????????? ?????? ????????? ?????? ???????????????.");
                    } else {
                        readText("?????? ??????????????? ??? ???????????????.");
                    }

                }
            }, 3000);


        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener2 = new RecognitionListener() { //???????????? ??????????????????(????????????)
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();

            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onResults(Bundle results) { //??????????????????????????????
            ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String msg = result.get(0);
            System.out.println("?????????2 : " + msg);

            if (msg.contains("???") && msg.contains("???")) {
                int m = Integer.parseInt(msg.split("???")[0]);
                int d = Integer.parseInt(msg.split("???")[1].replace(" ", "").split("???")[0]);
                if(checkDay_booking(m,d)){
                    System.out.println(m + "???" + d + "???");
                    Calendar cal = Calendar.getInstance();
                    int year = cal.get(Calendar.YEAR);
                    if(cal.get(Calendar.MONTH) == 12 && m == 1){
                        year++;
                    }
                    date = year+ "/" + m + "/" + d;
                    Toast.makeText(Recognition.this, date, Toast.LENGTH_SHORT).show();
                    Response.Listener<String> responseRes = new Response.Listener<String>() {
                        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONArray jsonArray = new JSONArray(response);
                                int i = 0;
                                Arrays.fill(timelist, true);
                                while (i < jsonArray.length()) {
                                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                                    boolean success = jsonObject.getBoolean("success");
                                    String time = aes256Chiper.decoding(jsonObject.getString("resTime"),MainActivity.key);
                                    System.out.println("?????? : " + time);
                                    if (success) {
                                        switch (time) {
                                            case "10:30":
                                                timelist[0] = false;
                                                break;
                                            case "11:00":
                                                timelist[1] = false;
                                                break;
                                            case "13:30":
                                                timelist[2] = false;
                                                break;
                                            case "14:00":
                                                timelist[3] = false;
                                                break;
                                            case "14:30":
                                                timelist[4] = false;
                                                break;
                                            case "15:00":
                                                timelist[5] = false;
                                                break;
                                            case "15:30":
                                                timelist[6] = false;
                                                break;
                                            case "16:00":
                                                timelist[7] = false;
                                                break;
                                            default:
                                                break;
                                        }
                                    } else {

                                    }
                                    i++;
                                }
                                readText("??????????????? ????????? ");
                                for (int j = 0; j < 8; j++) {
                                    if (timelist[j] == true) {
                                        findTime(j);
                                        SystemClock.sleep(1500);
                                    }
                                }
                                readText("?????????. ????????? ?????? ????????? ????????? ?????????.");
                                mHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        check = 0;
                                        mRecognizer.setRecognitionListener(listener4); // ?????? ????????? ?????? ?????? ??? ??????
                                        mRecognizer.startListening(mIntent);
                                    }
                                }, 3000);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            } catch (BadPaddingException e) {
                                e.printStackTrace();
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            } catch (InvalidKeyException e) {
                                e.printStackTrace();
                            } catch (InvalidAlgorithmParameterException e) {
                                e.printStackTrace();
                            } catch (NoSuchPaddingException e) {
                                e.printStackTrace();
                            } catch (IllegalBlockSizeException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    try {
                        String e_id = aes256Chiper.encoding(userID, MainActivity.key);
                        String e_date = aes256Chiper.encoding(date, MainActivity.key);
                        recognitionRequest = new RecognitionRequest(e_id, e_date, responseRes);
                        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                        queue.add(recognitionRequest);
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
                else{
                    if (check < 2) {
                        readText("????????? ????????? ??? ???????????????. ?????? ????????? ???????????????.");
                        check++;
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mRecognizer.setRecognitionListener(listener2); // ?????? ????????? ?????? ?????? ??? ??????
                                mRecognizer.startListening(mIntent);
                            }
                        }, 3000);
                    } else {
                        readText("?????? ??????????????? ??? ???????????????.");
                        check = 0;
                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);

                    }
                }



            } else {
                if (check < 2) {
                    readText("?????? ?????? ?????? ????????? ???????????????.");
                    check++;
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            mRecognizer.setRecognitionListener(listener2); // ?????? ????????? ?????? ?????? ??? ??????
                            mRecognizer.startListening(mIntent);
                        }
                    }, 3000);
                } else {
                    readText("?????? ??????????????? ??? ???????????????.");
                    check = 0;
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);

                }

            }

        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener4 = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {

        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String msg = result.get(0);
            System.out.println("?????????4 : " + msg);
            int h = 0, m = 0;
            String mm = "00";

            if (msg.contains("???")) {
                h = Integer.parseInt(msg.split("???")[0]);
                if (h < 10) {
                    h += 12;
                }

                if (msg.contains("???")) {
                    m = Integer.parseInt(msg.split("???")[1].replaceAll(" ", "").split("???")[0]);
                    mm = Integer.toString(m);
                } else if (msg.contains("???")) {
                    m = 30;
                    mm = Integer.toString(m);
                }

                System.out.println(h + "??? " + mm + "???");
                Toast.makeText(getApplicationContext(),"?????? : "+ h + "??? " + mm + "???" , Toast.LENGTH_SHORT).show();
                String time = h + ":" + mm;
                boolean ch = false;
                try {
                    ch = bookTime(time, date); //????????? ?????? ????????? ?????? ????????? ??? ??????
                    if (ch == false) {
                        if (check < 2) {
                            readText("????????? ??? ?????? ???????????????. ?????? ???????????? ??????????????????.");
                            check++;
                            mHandler.postDelayed(new Runnable() {
                                public void run() {
                                    mRecognizer.setRecognitionListener(listener4); // ?????? ????????? ?????? ?????? ??? ??????
                                    mRecognizer.startListening(mIntent);
                                }
                            }, 3000);
                        } else {
                            readText("?????? ??????????????? ??? ???????????????.");
                            mHandler.postDelayed(new Runnable() {
                                public void run() {
                                    check = 0;
                                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                                }
                            }, 3000);
                        }
                    } else {
                        readText("?????? ????????? ????????? ?????????????????????.");
                        Toast.makeText(getApplicationContext(),"????????????" , Toast.LENGTH_SHORT).show();
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                check = 0;
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                            }
                        }, 3000);
                    }
                } catch (NoSuchPaddingException e) {
                    e.printStackTrace();
                } catch (InvalidAlgorithmParameterException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
                System.out.println("ch : " + ch);

            } else {
                readText("?????? ??????????????? ??? ???????????????.");
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        check = 0;
                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                    }
                }, 3000);

            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener5 = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();

            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String msg = result.get(0);
            System.out.println("?????????2 : " + msg);
            Toast.makeText(getApplicationContext(), "????????? ??????", Toast.LENGTH_SHORT).show();

            sendMessageToBot(msg);

            mHandler.postDelayed(new Runnable() {
                Handler mHandler2 = new Handler();
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void run() {
                    if (rsp.equals("??????")) {
                        readText("?????? ?????? ???????????? ???????????????????");
                        mHandler2.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mRecognizer.setRecognitionListener(listener6); // ?????? ????????? ?????? ?????? ??? ??????
                                mRecognizer.startListening(mIntent);
                            }
                        }, 3000);
                    } else if (rsp.equals("??????")) {
                        readText("????????? ?????? ????????? ?????? ???????????????");
                        mHandler2.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1

                            }
                        }, 3000);
                    } else if (rsp.equals("??????")) { // ?????? ??????????????? ?????? ???????????? ????????? ??????
                        if (msg.contains("???") && msg.contains("???") && msg.contains("???")) {//????????? ?????? ????????? ???????????? (?????? ??? ????????????.)
                            String message = msg.replaceAll(" ","");
                            int y = Integer.parseInt(message.split("???")[0]);
                            int m = Integer.parseInt(message.split("???")[1].split("???")[0]);
                            int d = Integer.parseInt(message.split("???")[1].split("???")[1].split("???")[0]);
                            Toast.makeText(getApplicationContext(), "???????????????????????? : "+message, Toast.LENGTH_SHORT).show();
                            String mm = "";
                            if(checkDay_feedb(y,m,d)){
                                System.out.println(m + "???" + d + "???");
                                if (m < 10) {
                                    mm = "0" + m;
                                }
                                date = y + "/" + mm + "/" + d;
                                Toast.makeText(getApplicationContext(), "???????????????????????? : "+date, Toast.LENGTH_SHORT).show();
                                try {
                                    readFb(date);
                                } catch (NoSuchPaddingException e) {
                                    e.printStackTrace();
                                } catch (InvalidAlgorithmParameterException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (IllegalBlockSizeException e) {
                                    e.printStackTrace();
                                } catch (BadPaddingException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                }
                            }
                            else{
                                if (check < 2) {
                                    readText("????????? ??? ?????? ???????????????. ????????? ?????? ??????????????????.");
                                    check++;
                                    mHandler.postDelayed(new Runnable() {
                                        public void run() {
                                            mRecognizer.setRecognitionListener(listener5); // ?????? ????????? ?????? ?????? ??? ??????
                                            mRecognizer.startListening(mIntent);
                                        }
                                    }, 3000);
                                } else {
                                    readText("?????? ??????????????? ??? ???????????????.");
                                    check = 0;
                                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                                }
                            }

                        } else {
                            if (check < 2) {
                                readText("?????? ?????? ?????? ????????? ???????????????.");
                                check++;
                                mHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        mRecognizer.setRecognitionListener(listener5); // ?????? ????????? ?????? ?????? ??? ??????
                                        mRecognizer.startListening(mIntent);
                                    }
                                }, 3000);
                            } else {
                                readText("?????? ??????????????? ??? ???????????????.");
                                check = 0;
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                            }
                        }
                    }
                    else {
                        readText("?????? ??????????????? ??? ???????????????.");
                        check = 0;
                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END);
                    }
                }
            }, 3000);
        }


        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener6 = new RecognitionListener() { //?????????????????????
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> result = (ArrayList<String>) results.get(SpeechRecognizer.RESULTS_RECOGNITION);
            String msg = result.get(0);
            System.out.println("?????????6 : " + msg);
            sendMessageToBot(msg);
            mHandler.postDelayed(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void run() {
                    if ("???".equals(rsp) || "???".equals(rsp)) {
                        try {
                            readFb("no");
                        } catch (NoSuchPaddingException e) {
                            e.printStackTrace();
                        } catch (InvalidAlgorithmParameterException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (IllegalBlockSizeException e) {
                            e.printStackTrace();
                        } catch (BadPaddingException e) {
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            e.printStackTrace();
                        }
                    } else { //??????, ??????
                        readText("????????? ?????? ????????? ?????? ???????????????.");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                            }
                        }, 3000);
                    }
                }
            }, 3000);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener7 = new RecognitionListener() { //????????? ?????? ?????????
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 1000);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            msg = matches.get(0); //????????? ????????? ??????

            Date d = new Date();
            String _date = format.format(d);

            Response.Listener<String> responseListener_send = new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        boolean success = jsonObject.getBoolean("success");
                        if (success) {
                            readText("???????????? ?????????????????????.");
                            MainActivity.chatDataArrayList.add(new ChatData(msg, _date, userID, receiver));
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                                }
                            }, 3000);


                        } else {
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            };

            try {
                String e_msg = aes256Chiper.encoding(msg, MainActivity.key);
                String e_date = aes256Chiper.encoding(_date, MainActivity.key);
                String e_id = aes256Chiper.encoding(userID, MainActivity.key);
                String e_receiver = aes256Chiper.encoding(receiver, MainActivity.key);
                String e_send = aes256Chiper.encoding("1",MainActivity.key);
                String e_chk = aes256Chiper.encoding("1",MainActivity.key);
                RecognitionRequest recognitionRequest = new RecognitionRequest(2, e_msg, e_date, e_id, e_receiver, e_send,e_chk,responseListener_send);
                queue.add(recognitionRequest);
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

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private RecognitionListener listener8 = new RecognitionListener() { //????????? ??????
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {
            Toast.makeText(getApplicationContext(), "????????? ????????????????????? : " + error, Toast.LENGTH_SHORT).show();
            mHandler.postDelayed(new Runnable() { //tts ????????????, ????????? ???????????? ?????? ?????????
                @Override
                public void run() {
                    mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                }
            }, 3000);
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            msg = matches.get(0); //????????? ????????? ??????
            sendMessageToBot(msg);


            mHandler.postDelayed(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void run() {

                    if ("???".equals(rsp) || "???".equals(rsp)) {
                        Response.Listener<String> responseListener2 = new Response.Listener<String>() {
                            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                            @Override
                            public void onResponse(String response) {
                                try {
                                    JSONObject jsonObject = new JSONObject(response);
                                    boolean success = jsonObject.getBoolean("success");
                                    if (success) {
                                        String msg = jsonObject.getString("msg");
                                        String date = jsonObject.getString("date");

                                        String e_msg = aes256Chiper.decoding(msg, MainActivity.key);
                                        String e_date = aes256Chiper.decoding(date, MainActivity.key);

                                        String[] tmp_d = e_date.split(" ")[0].split("/");
                                        String[] tmp_t = e_date.split(" ")[1].split(":");

                                        String re_date = tmp_d[0] + "??? " + tmp_d[1] + " ???" + tmp_d[2] + " ???";
                                        String re_time = tmp_t[0] + "??? ";
                                        if (!"00".contains(tmp_t[1])) {
                                            re_time = re_time + tmp_t[1] + "???";
                                        }

                                        readText(re_date + " " + re_time + "??? ????????? ??????????????????.");
                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                readText(e_msg);
                                                Handler mHandler2 = new Handler();
                                                mHandler2.postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                                                    }
                                                }, 3000);
                                            }
                                        }, 3000);
                                    }
                                    else{
                                        readText("????????? ???????????? ????????????.");
                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                                            }
                                        }, 3000);

                                    }

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        try {
                            String e_id = aes256Chiper.encoding(userID, MainActivity.key);
                            recognitionRequest = new RecognitionRequest(3, 3, e_id, responseListener2);
                            queue.add(recognitionRequest);
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

                    } else {
                        readText("????????? ?????? ????????? ?????? ???????????????.");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                            }
                        }, 3000);
                    }
                }
            }, 3000);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

    private void replace_muscle() {
        Intent intent = new Intent("com.example.action.recognition.trans");
        intent.putExtra("recog_trans", "muscle");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void  replace_confirm() {
        Intent intent = new Intent("com.example.action.recognition.trans");
        intent.putExtra("recog_trans", "data");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void readFb(String d) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        Response.Listener<String> responseListener = new Response.Listener<String>() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onResponse(String response) {
                Toast.makeText(getApplicationContext(), "????????? ??????212121", Toast.LENGTH_SHORT).show();

                try {
                    JSONObject jsonObject = new JSONObject(response);
                    boolean success = jsonObject.getBoolean("success");
                    Toast.makeText(Recognition.this, "????????? ????????? ?????????", Toast.LENGTH_SHORT).show();
                    if (success) {

                        String feedback = aes256Chiper.decoding(jsonObject.getString("fContent"),MainActivity.key);
                        String symptom = aes256Chiper.decoding(jsonObject.getString("fSymptom"),MainActivity.key);
                        String doctor = aes256Chiper.decoding(jsonObject.getString("docName"),MainActivity.key);

                        Toast.makeText(Recognition.this, "????????? ????????? :"+symptom, Toast.LENGTH_SHORT).show();


                        readText(doctor + " ???????????? " + symptom + " ????????? ?????? ????????? ??????????????????.");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                readText(" " + feedback);
                                Handler mHandler2 = new Handler();
                                mHandler2.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                                    }
                                }, 10000);
                            }
                        }, 7000);
                    } else {
                        readText("?????? ???????????? ???????????? ???????????? ????????????.");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // 1
                            }
                        }, 3000);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        String e_id = aes256Chiper.encoding(userID,MainActivity.key);
        String e_date = aes256Chiper.encoding(d, MainActivity.key);

        if (d.equals("no")) {
            recognitionRequest = new RecognitionRequest(6, e_id, responseListener); //?????????????????????
        } else {
            recognitionRequest = new RecognitionRequest(4, e_id, e_date, responseListener); //???????????????????????????
        }
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(recognitionRequest);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public boolean bookTime(String t, String d) throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        String time = "no";
        final boolean[] check = {true};
        switch (t) {
            case "10:30":
                if (timelist[0] == true)
                    time = "10:30";
                break;
            case "11:00":
                if (timelist[1] == true)
                    time = "11:00";
                break;
            case "13:30":
                if (timelist[2] == true)
                    time = "13:30";
                break;
            case "14:00":
                if (timelist[3] == true)
                    time = "14:00";
                break;
            case "14:30":
                if (timelist[4] == true)
                    time = "14:30";
                break;
            case "15:00":
                if (timelist[5] == true)
                    time = "15:00";
                break;
            case "15:30":
                if (timelist[6] == true)
                    time = "15:30";
                break;
            case "16:00":
                if (timelist[7] == true)
                    time = "16:00";
                break;
            default:
                break;
        }

        if (!(time.equals("no"))) {
            Response.Listener<String> responseListener = new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        boolean success = jsonObject.getBoolean("success");
                        Toast.makeText(getApplicationContext(), "?????? ???????????????", Toast.LENGTH_SHORT).show();
                        if (success) {
                            check[0] = true;
                        } else {
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            };

            String e_id = aes256Chiper.encoding(userID,MainActivity.key);
            String e_date = aes256Chiper.encoding(d, MainActivity.key);
            String e_time = aes256Chiper.encoding(time, MainActivity.key);
            String e_doc = aes256Chiper.encoding(receiver, MainActivity.key);
            recognitionRequest = new RecognitionRequest(e_id, e_date, e_time, e_doc, responseListener);
            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
            queue.add(recognitionRequest);
        } else {
            check[0] = false;
        }

        return check[0];
    }
    public boolean checkDay_feedb(int y, int m, int d){
        Date date = new Date();
        SimpleDateFormat form = new SimpleDateFormat("yyyy/MM/dd");
        String day = form.format(date);
        int yy = Integer.parseInt(day.split("/")[0]);
        int mm =Integer.parseInt(day.split("/")[1]);
        int dd = Integer.parseInt(day.split("/")[2]);

        if(m > 12 || m < 1 || d < 0 || d > 31){ // ????????? ???????????? ???????????? ?????? ???
            return false;
        }
        else if(m > mm){ //?????? ?????? ??????????????? 12????????? ????????? 1?????????
            if(yy > y){ //?????? ?????? ????????? ??????????????? ?????????
                return true;
            }
            else{
                return false;
            }
        }
        else if(m == mm){
            if(dd > d){
                return true;
            }
            else{
                return false;
            }
        }
        else{
            Toast.makeText(getApplicationContext(), "???????????? :"+true, Toast.LENGTH_SHORT).show();

            return true;
        }
    }
    public boolean checkDay_booking(int m, int d){
        Calendar date = Calendar.getInstance();
        date.setTime(new Date());
        SimpleDateFormat form = new SimpleDateFormat("MM/dd");
        String day = form.format(date.getTime());

        int mm = Integer.parseInt(day.split("/")[0]);
        int dd = Integer.parseInt(day.split("/")[1]);

        date.add(Calendar.DATE,31);

        String limit_day = form.format(date.getTime());
        int limit_m = Integer.parseInt(limit_day.split("/")[0]);
        int limit_d = Integer.parseInt(limit_day.split("/")[1]);


        if(m > 12 || m < 1 || d < 0 || d > 31){ // ????????? ???????????? ???????????? ?????? ???
            return false;
        }
        else if(m < mm || (mm == m && d < dd)){// ?????????????????? ????????? ????????? ???
            if(mm == 12 && m == 1){ //??????????????? ????????? ?????????
                return true;
            }
            else{
                return false;
            }
        }
        else if(m == mm && d == dd){ //?????????????????? (???????????? ???????????? )
            return false;
        }
        else if(m >= limit_m ){ //????????????????????? ????????????(????????????????????? ????????????????????????.)
            if(m == limit_m+1 && d <= limit_d){ // ?????? ????????? ?????? ??????????????? ????????? ?????????, (????????? 31??? ????????? ????????? ????????? ?????? ???????????? ????????? ???)
                return true;
            }
            else{
                return false;
            }

        }
        else{
            return true;
        }
    }


    public void readText(String str) {

        System.out.println("1");
        tts.setPitch(1.0f);         // ?????? ?????? ?????? ??????
        System.out.println("2");
        tts.setSpeechRate(1.0f);    // ?????? ????????? 0.5???????????? ??????
        System.out.println("3");
        // editText??? ?????? ????????? ?????????.
        tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
        System.out.println("4");
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void readMyRes() throws NoSuchPaddingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        System.out.println("??????");
        Response.Listener<String> responseListener = new Response.Listener<String>() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onResponse(String response) {
                try {
                    JSONArray jsonArray = new JSONArray(response);
                    if (jsonArray.length() != 0) {
                        int i = 0;
                        while (i < jsonArray.length()) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            boolean success = jsonObject.getBoolean("success");
                            if (success) {
                                String date = jsonObject.getString("resDate");
                                String time = jsonObject.getString("resTime");

                                String d_date = aes256Chiper.decoding(date,MainActivity.key);
                                String d_time = aes256Chiper.decoding(time, MainActivity.key);

                                String str[] = d_date.split("/");
                                String str2[] = d_time.split(":");
                                date = str[0] + "??? " + str[1] + "??? " + str[2] + "??? ";
                                time = str2[0] + "??? ";
                                if (!"00".equals(str2[1])) {
                                    time += str2[1] + "??? ";
                                }
                                readText(date + time); //?????? ?????? ?????? ?????? ??????
                                System.out.println(date + time);
                                SystemClock.sleep(4000); //1?????????
                                System.out.println("???");
                                i++;

                            }
                        }
                        readText("??? ?????? ?????? ????????? ????????????.");
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // ?????? ???????????? ?????? ??? ??????
                            }
                        }, 3000);
                    } else {
                        readText("?????? ?????? ????????? ???????????? ????????????.");
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mHdrVoiceRecoState.sendEmptyMessage(MSG_VOICE_RECO_END); // ?????? ???????????? ?????? ??? ??????
                            }
                        }, 3000);
                    }


                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        String e_id = aes256Chiper.encoding(userID, MainActivity.key);
        recognitionRequest = new RecognitionRequest(e_id, responseListener);
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(recognitionRequest);
    }


    public void findTime(int index) {
        //???????????? ?????? ??? ????????? (???????????? ??????)
        switch (index) {
            case 0:
                readText("?????? ?????????");
                break;
            case 1:
                readText("?????????");
                break;
            case 2:
                readText("?????? ?????????");
                break;
            case 3:
                readText("??????");
                break;
            case 4:
                readText("?????? ?????????");
                break;
            case 5:
                readText("??????");
                break;
            case 6:
                readText("?????? ?????????");
                break;
            case 7:
                readText("??????");
                break;

        }
    }

    private void setUpBot() {
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.credential);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream).
                    createScoped("https://www.googleapis.com/auth/cloud-platform");
            String projectid = ((ServiceAccountCredentials) credentials).getProjectId();

            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.
                    create(credentials)).build();
            sessionsClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectid, uuid);
            Log.d(TAG, "projectid : " + projectid);
        } catch (IOException e) {
            Log.d(TAG, "setUpBot : " + e.getMessage());
        }
    }

    //dialogflow??? message??? ????????? ?????????
    private void sendMessageToBot(String message) {
        rsp = null;
        QueryInput input = QueryInput.newBuilder()
                .setText(TextInput.newBuilder().setText(message).setLanguageCode("ko-KR")).build();
        new SendMessageInBg(this, sessionName, sessionsClient, input).execute();
    }

    public void callback(DetectIntentResponse returnResponse) {
        //dialogflowAgent??? ?????? ????????? ??????
        if (returnResponse != null) {
            String dialogflowBotReply = returnResponse.getQueryResult().getFulfillmentText();
            Log.d("text", returnResponse.getQueryResult().toString());
            //getFulfillmentText??? ?????? ??????
            if (!dialogflowBotReply.isEmpty()) {
                rsp = dialogflowBotReply;
                System.out.println("???????????????. : " + dialogflowBotReply);
            } else {
                Toast.makeText(this, "something went wrong", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "failed to connect!", Toast.LENGTH_SHORT).show();
        }
    }

}
