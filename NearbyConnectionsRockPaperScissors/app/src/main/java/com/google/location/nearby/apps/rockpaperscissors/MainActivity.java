package com.google.location.nearby.apps.rockpaperscissors;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status;
import com.google.android.gms.nearby.connection.Strategy;

/**
 * Activity controlling the Nearby Connections
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NearbyConnections";

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
            };
    private static final String[] REQUIRED_PERMISSIONS_Q =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;

    // Our handle to Nearby Connections
    private ConnectionsClient connectionsClient;

    // Our randomly generated name
    private final String codeName = CodenameGenerator.generate();

    private String receiverEndpointId;
    private String receiverName;
    private String message;

    private Button connectButton;
    private Button disconnectButton;
    private Button sendMessageButton;
    private Button sendFileButton;

    private TextView recievedMessageText;
    private TextView receiverText;
    private TextView statusText;

    // Callbacks for receiving payloads
    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NonNull String endpointId, Payload payload) {
                    message = new String(payload.asBytes(), UTF_8);
                    setMessageText(message);
                }

                @Override
                public void onPayloadTransferUpdate(@NonNull String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == Status.SUCCESS) {
                        // TODO
                    }
                }
            };

    // Callbacks for finding other devices
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    Log.i(TAG, "onEndpointFound: endpoint found, connecting");
                    connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                }
            };

    // Callbacks for connections to other devices
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                    receiverName = connectionInfo.getEndpointName();
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful");

                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();

                        receiverEndpointId = endpointId;
                        setReceiverName(receiverName);
                        setStatusText(getString(R.string.status_connected));
                        setButtonState(true);
                    } else {
                        Log.i(TAG, "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the receiver");
                    resetUI();
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);

        connectButton = findViewById(R.id.connect);
        disconnectButton = findViewById(R.id.disconnect);
        sendMessageButton = findViewById(R.id.sendMessage);
        sendFileButton = findViewById(R.id.sendFile);

        receiverText = findViewById(R.id.receiver_name);
        statusText = findViewById(R.id.status);
        recievedMessageText = findViewById(R.id.recievedMessage);

        TextView nameView = findViewById(R.id.name);
        nameView.setText(getString(R.string.codename, codeName));

        connectionsClient = Nearby.getConnectionsClient(this);

        resetUI();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasPermissions(this, REQUIRED_PERMISSIONS_Q)) {
                requestPermissions(REQUIRED_PERMISSIONS_Q, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        } else {
            if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        }

        // Connect and Pair to other users
        connectButton.setOnClickListener(view -> connect());
        // Disconnect from other users
        disconnectButton.setOnClickListener(view -> disconnect());
        // Send Message
        sendMessageButton.setOnClickListener(view -> {
            setStatusText("");
            AlertDialog.Builder alert = new AlertDialog.Builder(view.getContext());
            final EditText edittext = new EditText(view.getContext());
            alert.setMessage("Enter Your Message");
            alert.setView(edittext);
            alert.setPositiveButton("Send", (dialog, whichButton) -> {
                String messageInput = edittext.getText().toString();
                sendMessage(messageInput);
            });
            alert.show();
        });
        // Send File
        sendFileButton.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            startActivity(intent);
        });
    }

    @Override
    protected void onStop() {
        connectionsClient.stopAllEndpoints();
        resetUI();
        super.onStop();
    }

    /**
     * Returns true if the app was granted all the permissions. Otherwise, returns false.
     */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                connectButton.setEnabled(false);
                return;
            }
        }
        recreate();
    }

    /**
     * Finds people to connect with using Nearby Connections.
     */
    public void connect() {
        startAdvertising();
        startDiscovery();
        setStatusText(getString(R.string.status_searching));
        connectButton.setEnabled(false);
    }

    /**
     * Disconnects and reset the UI.
     */
    public void disconnect() {
        connectionsClient.disconnectFromEndpoint(receiverEndpointId);
        resetUI();
    }

    public void sendMessage(String message) {
        connectionsClient.sendPayload(receiverEndpointId, Payload.fromBytes(message.getBytes(UTF_8)));
        setStatusText("Message sent");
    }

    public void sendFile(View view) {
        // TODO send file
        // connectionsClient.sendPayload(receiverEndpointId, Payload.fromFile());
        //setStatusText("You sent file to " + receiverEndpointId);
    }

    /**
     * Starts looking for other players using Nearby Connections.
     */
    private void startDiscovery() {
        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startDiscovery(
                getPackageName(), endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    /**
     * Broadcasts our presence using Nearby Connections so other players can find us.
     */
    private void startAdvertising() {
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
                codeName, getPackageName(), connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }

    /**
     * Wipes all game state and updates the UI accordingly.
     */
    private void resetUI() {
        receiverEndpointId = null;
        receiverName = null;

        setReceiverName(getString(R.string.no_receiver));
        setStatusText(getString(R.string.status_disconnected));
        setMessageText("");
        setButtonState(false);
    }

    /**
     * Enables/disables buttons depending on the connection status.
     */
    private void setButtonState(boolean connected) {
        connectButton.setEnabled(true);
        connectButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);

        setChoicesEnabled(connected);
    }

    /**
     * Enables/disables the rock, paper, and scissors buttons.
     */
    private void setChoicesEnabled(boolean enabled) {
        sendMessageButton.setEnabled(enabled);
        sendFileButton.setEnabled(enabled);
    }

    /**
     * Updates the message on the UI.
     */
    private void setMessageText(String name) {
        recievedMessageText.setText(name);
    }

    /**
     * Shows a status message to the user.
     */
    private void setStatusText(String text) {
        statusText.setText(text);
    }

    /**
     * Updates the receiver name on the UI.
     */
    private void setReceiverName(String name) {
        receiverText.setText(getString(R.string.receiver_name, name));
    }
}
