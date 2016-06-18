package com.khmelenko.lab.miband;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.khmelenko.lab.miband.listeners.NotifyListener;
import com.khmelenko.lab.miband.model.Profile;

import java.util.HashMap;
import java.util.UUID;

/**
 * Defines Bluetooth communication
 *
 * @author Dmytro Khmelenko (d.khmelenko@gmail.com)
 */
final class BluetoothIO extends BluetoothGattCallback {
    private static final String TAG = "BluetoothIO";

    public static final int STATUS_ERROR = -1;

    private BluetoothGatt mBluetoothGatt;

    private final BluetoothListener mBluetoothListener;
    private final HashMap<UUID, NotifyListener> mNotifyListeners;

    /**
     * Constructor
     *
     * @param listener Callback listener
     */
    public BluetoothIO(BluetoothListener listener) {
        mBluetoothListener = listener;
        mNotifyListeners = new HashMap<>();
    }

    /**
     * Connects to the Bluetooth device
     *
     * @param context Context
     * @param device  Device to connect
     */
    public void connect(Context context, BluetoothDevice device) {
        device.connectGatt(context, false, this);
    }

    /**
     * Gets remote connected device
     *
     * @return Connected device or null
     */
    public BluetoothDevice getConnectedDevice() {
        BluetoothDevice connectedDevice = null;
        if (mBluetoothGatt != null) {
            connectedDevice = mBluetoothGatt.getDevice();
        }
        return connectedDevice;
    }

    /**
     * Writes data to the service
     *
     * @param serviceUUID        Service UUID
     * @param characteristicUUID Characteristic UUID
     * @param value              Value to write
     */
    public void writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, byte[] value) {
        checkConnectionState();

        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
            if (characteristic != null) {
                characteristic.setValue(value);
                if (!mBluetoothGatt.writeCharacteristic(characteristic)) {
                    notifyWithFail(STATUS_ERROR, "BluetoothGatt write operation failed");
                }
            } else {
                notifyWithFail(STATUS_ERROR, "BluetoothGattCharacteristic " + characteristicUUID + " does not exist");
            }
        } else {
            notifyWithFail(STATUS_ERROR, "BluetoothGattService " + serviceUUID + " does not exist");
        }
    }

    /**
     * Reads data from the service
     *
     * @param serviceUUID Service UUID
     * @param uuid        Characteristic UUID
     */
    public void readCharacteristic(UUID serviceUUID, UUID uuid) {
        checkConnectionState();

        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
            if (characteristic != null) {
                if (!mBluetoothGatt.readCharacteristic(characteristic)) {
                    notifyWithFail(STATUS_ERROR, "BluetoothGatt read operation failed");
                }
            } else {
                notifyWithFail(STATUS_ERROR, "BluetoothGattCharacteristic " + uuid + " does not exist");
            }
        } else {
            notifyWithFail(STATUS_ERROR, "BluetoothGattService " + serviceUUID + " does not exist");
        }
    }

    /**
     * Reads Received Signal Strength Indication (RSSI)
     */
    public void readRssi() {
        checkConnectionState();

        if (!mBluetoothGatt.readRemoteRssi()) {
            notifyWithFail(STATUS_ERROR, "Request RSSI value failed");
        }
    }

    /**
     * Sets notification listener for specific service and specific characteristic
     *
     * @param serviceUUID      Service UUID
     * @param characteristicId Characteristic UUID
     * @param listener         New listener
     */
    public void setNotifyListener(UUID serviceUUID, UUID characteristicId, NotifyListener listener) {
        checkConnectionState();

        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
            if (characteristic != null) {
                mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Profile.UUID_DESCRIPTOR_UPDATE_NOTIFICATION);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
                mNotifyListeners.put(characteristicId, listener);
            } else {
                notifyWithFail(STATUS_ERROR, "BluetoothGattCharacteristic " + characteristicId + " does not exist");
            }
        } else {
            notifyWithFail(STATUS_ERROR, "BluetoothGattService " + serviceUUID + " does not exist");
        }
    }

    /**
     * Removes notification listener for the service and characteristic
     *
     * @param serviceUUID      Service UUID
     * @param characteristicId Characteristic UUID
     */
    public void removeNotifyListener(UUID serviceUUID, UUID characteristicId) {
        checkConnectionState();

        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
            if (characteristic != null) {
                mBluetoothGatt.setCharacteristicNotification(characteristic, false);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Profile.UUID_DESCRIPTOR_UPDATE_NOTIFICATION);
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(descriptor);
                mNotifyListeners.remove(characteristicId);
            } else {
                notifyWithFail(STATUS_ERROR, "BluetoothGattCharacteristic " + characteristicId + " does not exist");
            }
        } else {
            notifyWithFail(STATUS_ERROR, "BluetoothGattService " + serviceUUID + " does not exist");
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices();
        } else {
            gatt.close();
            if (mBluetoothListener != null) {
                mBluetoothListener.onDisconnected();
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            mBluetoothGatt = gatt;
            checkAvailableServices();
            notifyWithResult(null);
        } else {
            notifyWithFail(status, "onServicesDiscovered fail");
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        if (BluetoothGatt.GATT_SUCCESS == status) {
            notifyWithResult(characteristic);
        } else {
            notifyWithFail(status, "onCharacteristicRead fail");
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        if (BluetoothGatt.GATT_SUCCESS == status) {
            notifyWithResult(characteristic);
        } else {
            notifyWithFail(status, "onCharacteristicWrite fail");
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        if (mNotifyListeners.containsKey(characteristic.getUuid())) {
            mNotifyListeners.get(characteristic.getUuid()).onNotify(characteristic.getValue());
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        if (BluetoothGatt.GATT_SUCCESS == status) {
            Log.d(TAG, "onReadRemoteRssi:" + rssi);
        } else {
            notifyWithFail(status, "onCharacteristicRead fail");
        }
    }

    /**
     * Checks connection state.
     *
     * @throws IllegalStateException if device is not connected
     */
    private void checkConnectionState() throws IllegalStateException {
        if (mBluetoothGatt == null) {
            Log.e(TAG, "Connect device first");
            throw new IllegalStateException("Device is not connected");
        }
    }

    /**
     * Checks available services, characteristics and descriptors
     */
    private void checkAvailableServices() {
        for (BluetoothGattService service : mBluetoothGatt.getServices()) {
            Log.d(TAG, "onServicesDiscovered:" + service.getUuid());

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                Log.d(TAG, "  char:" + characteristic.getUuid());

                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                    Log.d(TAG, "    descriptor:" + descriptor.getUuid());
                }
            }
        }
    }

    /**
     * Notifies with success result
     *
     * @param data Result data
     */
    private void notifyWithResult(BluetoothGattCharacteristic data) {
        if (mBluetoothListener != null && data != null) {
            mBluetoothListener.onSuccess(data);
        }
    }

    /**
     * Notifies with failed result
     *
     * @param errorCode Error code
     * @param msg       Message
     */
    private void notifyWithFail(int errorCode, String msg) {
        if (mBluetoothListener != null) {
            mBluetoothListener.onFail(errorCode, msg);
        }
    }

}
