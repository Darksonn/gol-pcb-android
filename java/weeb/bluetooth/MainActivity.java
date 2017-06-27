/*
  gol-pcb-android

  Written in 2016

  To the extent possible under law, the author has dedicated all copyright and
  related and neighboring rights to this software to the public domain
  worldwide. This software is distributed without any warranty.

  You should have received a copy of the CC0 Public Domain Dedication along
  with this software. If not, see http://creativecommons.org/publicdomain/zero/1.0/
 */

package weeb.bluetooth;

// Import all the things we are going to use
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Define the variables we are going to use throughout the program
    private static final int REQUEST_ENABLE_BT = 2;
    BluetoothAdapter mBluetoothAdapter;
    ArrayAdapter<String> BTArrayAdapter;
    ListView myListView;
    TextView text;
    Button scan;

    GridLayout grid;
    ToggleButton[] buttons = new ToggleButton[25];
    ToggleButton gameButton;

    BluetoothDevice selectedDevice;
    Set<BluetoothDevice> pairedDevices;

    // Networking
    BluetoothSocket mmSocket;
    InputStream mmInStream;
    BufferedReader bufReader;
    OutputStream mmOutStream;


    // Runs when the app starts
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Display the activity

        grid = (GridLayout) findViewById(R.id.grid);
        // Go over each button, and set a listener to fire whenever they change
        // This listener will send their state via bluetooth if the game isn't running
        for(int i = 1; i<26; i++) {
            buttons[i-1] = (ToggleButton) findViewById(this.getResources().getIdentifier("toggleButton" + i, "id", this.getPackageName()));
            buttons[i-1].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton view, boolean isChecked) {
                    if(!((ToggleButton) findViewById(R.id.toggleGame)).isChecked())
                        sendStateUpdate(view.getId());
                }
            });
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // Get the bluetooth adapter

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        myListView = (ListView)findViewById(R.id.listView);
        text = (TextView) findViewById(R.id.textView);
        scan = (Button) findViewById(R.id.scanButton);

        // create the arrayAdapter that contains the BTDevices, and set it to the ListView
        // This will list all paired bluetooth devices
        BTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        myListView.setAdapter(BTArrayAdapter);

        // Make a listener for when we click on an item in the list
        // This will set the active bluetooth device to the selected device and try to connect to it
        myListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView parentView, View childView,
                                       int position, long id)
            {
                String selectedFromList = (myListView.getItemAtPosition(position)).toString();
                for(BluetoothDevice dev : pairedDevices){
                    if(dev.getAddress().equals(selectedFromList.substring(selectedFromList.indexOf("\n")+1))){
                        selectedDevice = dev;
                        Log.i("Blue2th", "Selected device " + selectedDevice.getAddress());
                        initiateConnection(selectedDevice);
                        text.setText(selectedFromList);
                        break;
                    }
                }

            }

        });

        // Makes the scan button look for new paired devices when clicked
        // If the 'scan' button is changed to the 'clear' button, send a clear signal to the arduino instead
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(scan.getText().toString().equals("Refresh")) {
                    scanForDevices();
                }else{
                    write(("clr!").getBytes());
                    for(ToggleButton b : buttons){
                        b.setChecked(false);
                    }
                }
            }
        });

        gameButton = (ToggleButton) findViewById(R.id.toggleGame);
        // Set a listener so we can toggle the game of life on and off
        gameButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                write(("g " + (b == true ? "1" : "0") + "!").getBytes());
            }
        });
    }

    // Checks for paired devices and adds them to the BTArrayAdapter list
    public void scanForDevices(){
        pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            BTArrayAdapter.clear();
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                BTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }
    }

    // Try to connect to a bluetooth device
    // If the connection is successful, change the app to display the game of life controls
    public void initiateConnection(BluetoothDevice dev){
        try {
            mmSocket = dev.createRfcommSocketToServiceRecord(dev.getUuids()[0].getUuid());
            Log.i("Blue2th", "Created socket");
            mmSocket.connect();
            Log.i("Blue2th", "Connected to device");
            mmOutStream = mmSocket.getOutputStream();
            mmInStream = mmSocket.getInputStream();
            bufReader = new BufferedReader(new InputStreamReader(mmInStream));
            grid.setVisibility(View.VISIBLE);
            gameButton.setVisibility(View.VISIBLE);
            scan.setText("Clear");
            // Create a thread to listen for input from the Arduino's bluetooth
            (new Thread() {
                public void run() {
                    try {
                        while(true) {
                            final String s = bufReader.readLine();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < buttons.length; i++) {
                                        buttons[i].setChecked((s.charAt(i) == 'L' ? false : true));
                                    }
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }catch(IOException e) {
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
            Log.e("Blue2th", e.getMessage()); }

    }

    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    // Called when the app is started (after onCreate)
    // Asks for bluetooth permissions and performs and initial check for devices
    @Override
    public void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            text.setText("Status: Enabled");
            scanForDevices();
        }

    }

    // Set the status message to whether or not bluetooth is enabled when we ask for permissions
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        if(requestCode == REQUEST_ENABLE_BT){
            if(mBluetoothAdapter.isEnabled()) {
                text.setText("Status: Enabled");
            } else {
                text.setText("Status: Disabled");
            }
        }
    }

    // Converts a buttons state to a string formatted as "id <0/1>" eg. "5 1" if 5 is on
    public String buttonStatusToString(int id){
        for(int i = 0; i<25; i++){
            if(buttons[i].getId() == id)
                return i + " " + (buttons[i].isChecked() == true ? "1" : "0") + "!";
        }
        return "";
    }

    // Sends a single squares state to the Arduino
    public void sendStateUpdate(int id){
        String status = buttonStatusToString(id);
        if(status == ""){
            Log.e("Blue2th", "No button found!");
            return;
        }
        Log.i("Blue2th", "Sending: \"" + status + "\"");
        write(status.getBytes());
    }

    // (Unused) sends all pins states to the Arduino
    public void sendCompleteState(){
        for(int i = 0; i<25; i++){
            write(((i + " " + (buttons[i].isChecked() == true ? "1" : "0")) + "!").getBytes());
        }
    }

}
