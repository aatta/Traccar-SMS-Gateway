# Support for Binary SMS (Teltonika FM2200, etc.)

Currently, the app only handles text-based SMS using `getMessageBody()`. This is insufficient for trackers that send binary data via SMS. This plan proposes adding a configuration option for binary mode and updating the receiver to handle binary data.

## Proposed Changes

### 1. [Preference Management](file:///F:/Abdullah/MyCode/GitHub/Traccar-SMS-Gateway/app/src/main/java/com/traccar/smsgateway/PreferenceManager.java)
- Add `KEY_BINARY_SMS` constant.
- Add `setBinarySmsEnabled` and `isBinarySmsEnabled` methods.

### 2. [User Interface](file:///F:/Abdullah/MyCode/GitHub/Traccar-SMS-Gateway/app/src/main/res/layout/activity_main.xml)
- Add a new `Switch` for "Binary SMS Mode" in the configuration section.
- Add a descriptive `TextView` explaining when to use this mode.

### 3. [MainActivity](file:///F:/Abdullah/MyCode/GitHub/Traccar-SMS-Gateway/app/src/main/java/com/traccar/smsgateway/MainActivity.java)
- Initialize the new switch.
- Load the state from preferences.
- Save the state to preferences.

### 4. [Network Client](file:///F:/Abdullah/MyCode/GitHub/Traccar-SMS-Gateway/app/src/main/java/com/traccar/smsgateway/TraccarTCPClient.java)
- Add `sendMessage(String host, int port, byte[] data)` to handle raw byte transmission.
- This method will NOT add a newline character, as binary protocols often rely on exact byte lengths or their own terminators.

### 5. [SMS Handling](file:///F:/Abdullah/MyCode/GitHub/Traccar-SMS-Gateway/app/src/main/java/com/traccar/smsgateway/SMSReceiver.java)
- Update `onReceive` to check the `isBinarySmsEnabled` preference.
- If enabled, use `message.getUserData()` to get the raw bytes.
- If disabled (or as a fallback), continue using `message.getMessageBody()`.
- Update `forwardToTraccar` to support both `String` and `byte[]`.

## Verification Plan

### Automated Tests
- None planned as this involves hardware-specific SMS reception which is hard to mock without a full integration test suite.

### Manual Verification
- Deploy to a device.
- Verify that the "Binary SMS Mode" switch appears and its state is persisted.
- (If a tracker is available) Verify that binary SMS from Teltonika is correctly forwarded to the Traccar server.
- Verify that standard text SMS still works in normal mode.
