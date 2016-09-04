package com.cardio.program;

import android.app.ActionBar;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.provider.BaseColumns;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.sql.Time;
import java.text.DecimalFormat;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;
import com.cardio.program.bt_code.DeviceConnector;
import com.cardio.program.bt_code.DeviceData;
import com.cardio.program.bt_code.Utils;


public class infoActivity extends ActionBarActivity {

    Button btnDis, btnStart, btnShow;//btnClear,
    //TextView output;
    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    //SPP UUID. Look for it
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    InputStream inStream = null;
    DeviceConnector connector;
    BluetoothResponseHandler handler;
    DatabaseHelper databaseHelper;
    SQLiteDatabase database;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public int number;
    public boolean isBusy = false;

    public static final String DATABASE = "cardio_data.db";
    public static final String DATATABLE = "cardio_table";

    private class MyPlotUpdater implements Observer {
        Plot plot;

        public MyPlotUpdater(Plot plot) {
            this.plot = plot;
        }

        @Override
        public void update(Observable o, Object arg) {
            plot.redraw();
        }
    }

    public XYPlot dynamicPlot;
    public MyPlotUpdater plotUpdater;
    SampleDynamicXYDatasource data;
    public Thread myThread;
    private long[] Points = new long[29];
    private Random r = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device

        //view of the ledControl
        setContentView(R.layout.activity_info);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        //call the widgtes
        btnDis = (Button)findViewById(R.id.button4);
        btnStart = (Button)findViewById(R.id.button2);
        //btnClear = (Button)findViewById(R.id.btnClear);
        btnShow = (Button)findViewById(R.id.btnShow);

        // создаем класс helper для работы с базой
        databaseHelper = new DatabaseHelper(this, DATABASE, null, 1);
        database = databaseHelper.getReadableDatabase();

        //output = (TextView)findViewById(R.id.txtArduino);

        btnDis.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isBusy){
                    Disconnect(); //close connection
                }
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isBusy){
                    isBusy = true;

                    Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
                    number = cursor.getCount()-1;
                    cursor.close();

                    String CREATE_SCRIPT = "create table "
                            + DatabaseHelper.DATABASE_TABLE + number + " (" + DatabaseHelper.KEY_ID
                            + " integer primary key autoincrement, " + DatabaseHelper.LEVEL
                            + " integer, " + DatabaseHelper.TIME + " time); ";

                    database.execSQL(CREATE_SCRIPT);

                    SendData(new String("1").getBytes()); //отправляем сигнал для запуска

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            SendData(new String("0").getBytes()); //сигнал для остановки
                            isBusy = false;
                        }
                    }, 60000);
                }
            }
        });

        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isBusy)
                {
                    Intent i = new Intent(infoActivity.this, databaseList.class);
                    startActivity(i);
                }
            }
        });

        //output.setMovementMethod(new ScrollingMovementMethod());

        try
        {
            handler = new BluetoothResponseHandler(this);
        }
        catch(Exception e)
        {
            Log.d("connection", "Error occured: " + e.toString());
        }


        /****** для графика *****/
        // get handles to our View defined in layout.xml:
        dynamicPlot = (XYPlot) findViewById(R.id.dynamicXYPlot);

        plotUpdater = new MyPlotUpdater(dynamicPlot);

        // only display whole numbers in domain labels
        dynamicPlot.getGraphWidget().setDomainValueFormat(new DecimalFormat("0"));

        // getInstance and position datasets:
        data = new SampleDynamicXYDatasource();
        SampleDynamicSeries sine1Series = new SampleDynamicSeries(data, 0, "");

        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                Color.RED, null, null, null);
        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        formatter1.getLinePaint().setStrokeWidth(5);
        dynamicPlot.addSeries(sine1Series,
                formatter1);

        // hook up the plotUpdater to the data model:
        data.addObserver(plotUpdater);

        // thin out domain tick labels so they dont overlap each other:
        dynamicPlot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        dynamicPlot.setDomainStepValue(5);

        dynamicPlot.setRangeStepMode(XYStepMode.INCREMENT_BY_VAL);
        dynamicPlot.setRangeStepValue(10);

        dynamicPlot.setRangeValueFormat(new DecimalFormat("###.#"));

        // uncomment this line to freeze the range boundaries:
        dynamicPlot.setRangeBoundaries(0, 100, BoundaryMode.FIXED);

        // create a dash effect for domain and range grid lines:
        DashPathEffect dashFx = new DashPathEffect(
                new float[] {PixelUtils.dpToPix(3), PixelUtils.dpToPix(3)}, 0);
        dynamicPlot.getGraphWidget().getDomainGridLinePaint().setPathEffect(dashFx);
        dynamicPlot.getGraphWidget().getRangeGridLinePaint().setPathEffect(dashFx);
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        new ConnectBT().execute(); //Call the class to connect
    }

    @Override
    public void onResume() {
        // kick off the data generating thread:
        myThread = new Thread(data);
        myThread.start();
        super.onResume();
    }

    @Override
    public void onPause() {
        data.stopThread();
        super.onPause();
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Ошибка");}
        }
        finish(); //return to the first layout

    }

    private void SendData(byte[] data)
    {
        connector.write(data);
    }

    // fast way to call Toast
    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_info, menu);
        //Intent intent = new Intent(this, DeviceList.class);
        //startActivity(intent);
        return true;
    }

    /**
     * Добавление ответа в лог
     *
     * @param message  - текст для отображения
     */
    //здесь принимаем сообщения от Arduino
    void appendLog(String message, boolean hexMode) {

        StringBuilder msg = new StringBuilder();

        msg.append(hexMode ? Utils.printHex(message) : message);

        for (int i = 0; i < Points.length-1; i++) {
            Points[i] = Points[i + 1];
        }
        Points[Points.length-1]=Integer.parseInt(msg.toString().replaceAll("[\\D]",""));

        ContentValues newValues = new ContentValues();
        newValues.put(databaseHelper.LEVEL, msg.toString());
        newValues.put(databaseHelper.TIME, (DateFormat.format("dd-MM-yyyy hh:mm", new java.util.Date()).toString()));

        database.insert(DATATABLE + number, null, newValues);

        /*try
        {
            final int scrollAmount = output.getLayout().getLineTop(output.getLineCount()) - output.getHeight();
            if (scrollAmount > 0)
            output.scrollTo(0, scrollAmount);
            else output.scrollTo(0, 0);
        }
        catch(Exception e)
        {
            Log.d("appendLog", "error occured: " + e.toString());
        }*/
    }
    // =========================================================================

    public class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(infoActivity.this, "Подключение...", "Пожалуйста, подождите");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                    //outStream = btSocket.getOutputStream();
                    inStream = btSocket.getInputStream(); //получаем поток для вывода

                    connector = new DeviceConnector(new DeviceData(dispositivo, "HC-07"),handler);
                    connector.connect();
                    connector.connected(btSocket);
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Не удалось соединиться :(");
                finish();
            }
            else
            {
                msg("Подключен");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

    /**
     * Обработчик приёма данных от bluetooth-потока
     */
    private static class BluetoothResponseHandler extends Handler {
        private WeakReference<infoActivity> mActivity;

        public BluetoothResponseHandler(infoActivity activity) {
            mActivity = new WeakReference<infoActivity>(activity);
        }

        public void setTarget(infoActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<infoActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            infoActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:

                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                               // bar.setSubtitle(MSG_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                               // bar.setSubtitle(MSG_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                               // bar.setSubtitle(MSG_NOT_CONNECTED);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        final String readMessage = (String) msg.obj;
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false);
                        }
                        break;

                    case MESSAGE_DEVICE_NAME:
                        //activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }
    }
// ==========================================================================

    /*** для графика ***/
    class SampleDynamicXYDatasource implements Runnable {

        // encapsulates management of the observers watching this datasource for update events:
        class MyObservable extends Observable {
            @Override
            public void notifyObservers() {
                //***изменяем данные для графика***
                /*for (int i = 0; i < Points.length-1; i++) {
                    Points[i] = Points[i + 1];
                }
                Points[Points.length-1]=r.nextInt(100);*/
                //****************************************
                setChanged();
                super.notifyObservers();
            }
        }

        private MyObservable notifier;
        private boolean keepRunning = false;

        {
            notifier = new MyObservable();
        }

        public void stopThread() {
            keepRunning = false;
        }

        //@Override
        public void run() {
            try {
                keepRunning = true;
                while (keepRunning) {
                    Thread.sleep(1000); // decrease or remove to speed up the refresh rate.
                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public int getItemCount(int series) {
            return Points.length;
        }

        public Number getX(int index) {
            if (index >= Points.length) {
                throw new IllegalArgumentException();
            }
            return index;
        }

        public Number getY(int index) {
            return Points[index];
        }

        public void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }

        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }

    }

    class SampleDynamicSeries implements XYSeries {
        private SampleDynamicXYDatasource datasource;
        private int seriesIndex;
        private String title;

        public SampleDynamicSeries(SampleDynamicXYDatasource datasource, int seriesIndex, String title) {
            this.datasource = datasource;
            this.seriesIndex = seriesIndex;
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int size() {
            return datasource.getItemCount(seriesIndex);
        }

        @Override
        public Number getX(int index) {
            return datasource.getX(index);
        }

        @Override
        public Number getY(int index) {
            return datasource.getY(index);
        }
    }

    public class DatabaseHelper extends SQLiteOpenHelper implements BaseColumns {

        public static final String DATABASE_NAME = "cardio_data.db";
        public static final String DATABASE_TABLE = "cardio_table";
        public static final int DATABASE_VERSION = 1;
        public static final String KEY_ID = "_id";
        public static final String LEVEL = "level";
        public static final String TIME = "time";
        private static final String DATABASE_CREATE_SCRIPT = "create table "
                + DATABASE_TABLE + " (" + KEY_ID
                + " integer primary key autoincrement, " + LEVEL
                + " integer, " + TIME + " time); ";

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                              int version) {
            super(context, name, factory, version);
        }

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                              int version, DatabaseErrorHandler errorHandler) {
            super(context, name, factory, version, errorHandler);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE_SCRIPT);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Удаляем старую таблицу и создаём новую
            db.execSQL("DROP TABLE IF IT EXISTS " + DATABASE_TABLE);
            // Создаём новую таблицу
            onCreate(db);
        }
    }
}



