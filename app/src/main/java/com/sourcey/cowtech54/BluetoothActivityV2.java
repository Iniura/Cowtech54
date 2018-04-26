package com.sourcey.cowtech54;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Created by Wily on 07/03/2018.
 */



public class BluetoothActivityV2 extends AppCompatActivity {

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private Button mScanBtn;
    private Button mOffBtn;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private CheckBox mLED1;

    private String mMessage; //Message to transmit to main

    private final String TAG = BluetoothActivityV1.class.getSimpleName();
    private static Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    // Defines
    private XYPlot plot;
    private Button updPlotBtn;
    private Button listIDsBtn;

    //BtMessageManager
    public BtMessageManager btMessageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        btMessageManager = new BtMessageManager();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt);



        updPlotBtn = (Button)findViewById(R.id.updatePlotBtn);
        listIDsBtn = (Button) findViewById(R.id.listIdsBtn);

        mBluetoothStatus = (TextView)findViewById(R.id.bluetoothStatus);
        mReadBuffer = (TextView) findViewById(R.id.readBuffer);
        mScanBtn = (Button)findViewById(R.id.scan);
        mOffBtn = (Button)findViewById(R.id.off);
        mDiscoverBtn = (Button)findViewById(R.id.discover);
        mListPairedDevicesBtn = (Button)findViewById(R.id.PairedBtn);
        mLED1 = (CheckBox)findViewById(R.id.checkboxGps);

        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView)findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);


        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        //Connect to COW if available
        spawnConnectThread("98:D3:32:20:E2:08","COW");

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    btMessageManager.addNewMessage(readMessage);
                    LinkedList t1 = btMessageManager.MatIDs.get(50).getTime();
                    LinkedList v1 = btMessageManager.MatIDs.get(50).getByte8();
                    LinkedList v2 = btMessageManager.MatIDs.get(50).getByte8();
                    //plotManager(t1,v1,v2);
                    mReadBuffer.setText(readMessage);
                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        mBluetoothStatus.setText("Connected to Device: " + (String)(msg.obj));
                    else
                        mBluetoothStatus.setText("Connection Failed");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {

            mLED1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    if(mConnectedThread != null) //First check to make sure thread created
                        mConnectedThread.write("1");
                }
            });


            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });

            mOffBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    bluetoothOff(v);
                }
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices(v);
                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discover(v);
                }
            });

            //Plot
            updPlotBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    updatePlot(v);
                }
            });

            //IDsList
            listIDsBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    updateIDsList(v);
                }
            });
        }

        //======= declare XYPlot =========
        plot = (XYPlot) findViewById(R.id.plot);
    }

    private void updateIDsList(View view){
        Spinner spinner = (Spinner) findViewById(R.id.listIdsSpinner);
        //
        TreeSet<String> sortedSet =new TreeSet<String>(btMessageManager.IDsListSet);
        String[] newStringList = sortedSet.toArray(new String[sortedSet.size()]);

        //String[] newStringList = btMessageManager.IDsListSet.toArray(new String[btMessageManager.IDsListSet.size()]);


        //String[] newStringList = {"A", "B", "C"};


        ArrayAdapter dataAdapter;

        dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,newStringList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);
    }
    private void updatePlot(View view){


        LinkedList t1 = btMessageManager.MatIDs.get(50).getTime();
        LinkedList v1 = btMessageManager.MatIDs.get(50).getByte7();
        LinkedList v2 = btMessageManager.MatIDs.get(50).getByte8();
        if(t1.size()>1) {

            Log.d("PLOT", "Plotting! ");

            Runnable plotRun = new PlotRun(this, t1, v1, v2, plot);

            new Thread(plotRun).start();
        }
        else{
            Log.d("PLOT", "Err404: There are not values in Matrix ");
        }

    }
    private void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Enabled");
            }
            else
                mBluetoothStatus.setText("Disabled");
        }
    }

    private void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void discover(View view){
        // Check if the device is already discovering
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();

                // Register for broadcasts when a device is discovered.
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

                Log.d("BT", "Dev found f2");
            }
            else{
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d("BT", "Broadcast Receiver init");
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                Log.d("BT", "Dev found f1");
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            mBluetoothStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            spawnConnectThread(address, name);

        }
    };

    public void spawnConnectThread(final String address, final String name){
        new Thread()
        {
            public void run() {
                boolean fail = false;


                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);


                Log.d("BluetoothW", "Spawn threading");

                try {
                    mBTSocket = createBluetoothSocket(device);

                    Log.d("BluetoothW", "bt socket created");
                } catch (IOException e) {
                    fail = true;
                    Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                }
                // Establish the Bluetooth socket connection.
                try {
                    Log.d("BluetoothW", "bt socket TRYING TO connect!");
                    mBTSocket.connect();
                    //mBTSocket.connect();
                    Log.d("BluetoothW", "bt socket connected!");
                } catch (IOException e) {
                    try {
                        fail = true;
                        mBTSocket.close();

                        Log.d("BluetoothW", "bt socket TRYed, now Closed");
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e2) {
                        //insert code to deal with this
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                }
                if(fail == false) {
                    mConnectedThread = new ConnectedThread(mBTSocket);
                    mConnectedThread.start();

                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget();
                }
            }
        }.start();
    }


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer = new byte[30720]; //supossing a 300Hz freq of IDs //byte[1024];
                        SystemClock.sleep(1000); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        //    Call this from the main activity to send data to the remote device
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
                Log.d("BluetoothW", "Sending String as Byte");
            } catch (IOException e) { }
        }

        // Call this from the main activity to shutdown the connection
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }




    public void onBackPressed() {
        // Disable going back to the MainActivity
        //moveTaskToBack(true);

        //mCurrentBackgroundColor = selectedColor;
        mMessage = "No Device - Unknown";//mDevicesListView.getSelectedItem().toString();


        Intent returnIntent = new Intent();
        returnIntent.putExtra(MainActivity.EXTRA_BT_MESSAGE, mMessage);
        //returnIntent.putExtra(MainActivity.EXTRA_COLOR, mCurrentBackgroundColor);
        setResult(BluetoothActivityV1.RESULT_OK, returnIntent);


        finish();
        overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
    }


    //----------*-------------------*-------------------------------

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((CheckBox) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radioTime:
                if (checked){

                }
                // Pirates are the best
                break;
            case R.id.radioLen:
                if (checked)
                    // Ninjas rule
                    break;
            case R.id.radioB1:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radioB2:
                if (checked)
                    // Ninjas rule
                    break;
            case R.id.radioB3:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radioB4:
                if (checked)
                    // Ninjas rule
                    break;
            case R.id.radioB5:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radioB6:
                if (checked)
                    // Ninjas rule
                    break;
            case R.id.radioB7:
                if (checked)
                    // Pirates are the best
                    break;
            case R.id.radioB8:
                if (checked)
                    // Ninjas rule
                    break;
        }
    }


    //----------*-------------------*-------------------------------
    //----------*-------------------*-------------------------------
    //----------*-------------------*-------------------------------

    class DynamicXYDatasource implements Runnable {

        // encapsulates management of the observers watching this datasource for update events:
        class MyObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers();
            }
        }


        private static final int SAMPLE_SIZE = 31;

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
                boolean isRising = true;
                while (keepRunning) {

                    Thread.sleep(10); // decrease or remove to speed up the refresh rate.

                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public int getItemCount(int series) {
            return SAMPLE_SIZE;
        }

        public Number getX(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            return index;
        }

        public Number getY(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            /*double angle = (index + (phase))/FREQUENCY;
            double amp = sinAmp * Math.sin(angle);
            switch (series) {
                case SINE1:
                    return amp;
                case SINE2:
                    return -amp;
                default:
                    throw new IllegalArgumentException();
            }*/
            return 1; //
        }

        public void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }

        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }

    }

    class DynamicSeries implements XYSeries {
        private DynamicXYDatasource datasource;
        private int seriesIndex;
        private String title;

        public DynamicSeries(DynamicXYDatasource datasource, int seriesIndex, String title) {
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
            return datasource.getX(seriesIndex, index);
        }

        @Override
        public Number getY(int index) {
            return datasource.getY(seriesIndex, index);
        }
    }

}


