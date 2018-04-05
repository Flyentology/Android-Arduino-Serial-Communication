package damiankedzior.bluetoothconnect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.app.Activity;
import android.speech.RecognizerIntent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements OnClickListener {
    Handler h;
    private StringBuilder sb = new StringBuilder();
    private TextView ArduinoData;
    private ListView BluetoothPairedDevices;
    private EditText BluetoothInputMessage;
    private BluetoothSocket btSocket = null;
    private BluetoothAdapter btAdapter = null;
    private List<String> QueryDevices_final;
    private ArrayAdapter<String> arrayAdapter;
    public Button speakButton;
    public Button Send;
    public Button Disconnect;
    public Button Clear;
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    // Adres MAC
    private static String address = null;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectedThread mConnectedThread;
    private ConnectThread mConnectThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speakButton = (Button) findViewById(R.id.Record);
        speakButton.setOnClickListener(this);
        Send = (Button) findViewById(R.id.Send);
        Send.setOnClickListener(this);
        Disconnect = (Button) findViewById(R.id.Disconnect);
        Disconnect.setOnClickListener(this);
        Clear = (Button) findViewById(R.id.Clear);
        Clear.setOnClickListener(this);
        BluetoothPairedDevices = (ListView) findViewById(R.id.PairedDevices);
        BluetoothInputMessage = (EditText) findViewById(R.id.InputMessage);
        ArduinoData = (TextView) findViewById(R.id.ArduinoData);
        ArduinoData.setMovementMethod(new ScrollingMovementMethod());

        // initialization of list
        QueryDevices_final = new ArrayList<String>();
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, QueryDevices_final);
        BluetoothPairedDevices.setAdapter(arrayAdapter);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetoothState();

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case 1:
                        String readBuf = msg.obj.toString();
                        sb.append(readBuf);
                        int endOfLineIndex = sb.indexOf("\r\n");
                        if (endOfLineIndex > 0) {
                            String sbprint = sb.substring(0, endOfLineIndex);
                            sb.delete(0, sb.length());
                            ArduinoData.setText("Data from Arduino: " + sbprint);
                        }
                        break;
                    case 2:
                        Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        Toast.makeText(getApplicationContext(), "Connection failed", Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            ;
        };

        BluetoothPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() { //obsługa kliknięcia na pojedyńczy element listy
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Object obj = BluetoothPairedDevices.getItemAtPosition(i);
                String choosenDevice = obj.toString().substring(0, 17);
                address = choosenDevice;
                mConnectThread = new ConnectThread(btAdapter.getRemoteDevice(address));
                mConnectThread.start();
                Toast.makeText(getApplicationContext(), "Connecting", Toast.LENGTH_SHORT).show();
                new CountDownTimer(300, 100) {
                    @Override
                    public void onTick(long l) {

                    }

                    @Override
                    public void onFinish() {
                        mConnectedThread = new ConnectedThread(btSocket);
                        mConnectedThread.start();
                    }
                }.start();
            }
        });

    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    @Override
    public void onResume() {
        super.onResume();
        Boolean loop = true;
        while (loop) {
            if (btAdapter.isEnabled()) {
                loop = false;
                Set<BluetoothDevice> pairedDevice = btAdapter.getBondedDevices();
                if (pairedDevice.size() > 0) {
                    for (BluetoothDevice device : pairedDevice) {
                        String name = device.getName();
                        String address = device.getAddress();
                        String toBeAdded = address + " " + name;
                        if (!QueryDevices_final.contains(toBeAdded)) {
                            QueryDevices_final.add(toBeAdded);
                            arrayAdapter.notifyDataSetChanged();
                        }
                    }
                }
                Toast.makeText(getApplicationContext(), "Click on device you want to connect with", Toast.LENGTH_LONG).show();
            }

        }

    }

    public void onPause() {
        super.onPause();
        try {
            //mConnectedThread.cancel();
            //Toast.makeText(getApplicationContext(), "Connection closed", Toast.LENGTH_SHORT).show();
        } catch (NullPointerException e) {

        }


    }


    private void checkBluetoothState() {
        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device not supported", Toast.LENGTH_LONG).show();
            this.finishAffinity();
        } else {
            if (!btAdapter.isEnabled()) {
                btAdapter.enable();
            }
        }
    }


    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {

            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[512];

            while (true) {
                try {
                    int bytes = mmInStream.read(buffer);
                    String receivedMessage = new String(buffer, 0, bytes);
                    h.obtainMessage(1, receivedMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String message) {

            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {

            }
        }

        // shutdown connection
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private class ConnectThread extends Thread {

        public ConnectThread(BluetoothDevice device) {
            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {

            }
        }

        public void run() {
            try {
                btSocket.connect();
                h.obtainMessage(2).sendToTarget();
            } catch (IOException e) {
                try {
                    h.obtainMessage(3).sendToTarget();
                    btSocket.close();
                } catch (IOException e2) {
                }
            }
        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e2) {
            }
        }
    }

    public void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Mów teraz");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Fill the list view with the strings the recognizer thought it
            // could have heard
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            for(String word : results)
            {
                String message_to_send = word.toUpperCase();
                ArduinoData.append(word + "\n");
                switch (message_to_send)
                {
                    case "ROBOT":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "START":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "STOP":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "PRAWO":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "LEWO":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "OBRÓT":
                        mConnectedThread.write(message_to_send);
                        break;
                    case "TYŁ":
                        mConnectedThread.write(message_to_send);
                        break;
                    default:
                            break;
                }
            }

        }
    }

    // onClick for every part of interface
    public void onClick(View v) {
        if (v == speakButton) {
            startVoiceRecognitionActivity();
        }
        if (v == Send) {
            String userMessage = BluetoothInputMessage.getText().toString();
            try {
                mConnectedThread.write(userMessage);
            } catch (NullPointerException e) {
                Toast.makeText(getApplicationContext(), "You are not connected to any device", Toast.LENGTH_SHORT).show();
            }
            BluetoothInputMessage.getText().clear();
        }
        if (v == Disconnect)
        {
            try {
                mConnectedThread.cancel();
                Toast.makeText(getApplicationContext(), "Connection closed", Toast.LENGTH_SHORT).show();
            } catch (NullPointerException e) {

            }
        }
        if (v == Clear) {
                ArduinoData.setText("");
        }
    }
}