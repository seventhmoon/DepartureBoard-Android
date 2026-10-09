# Google Play Explanation: FOREGROUND_SERVICE_SPECIAL_USE Permission

## Why This Permission Is Required

The `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` permission is required for this application because it provides real-time train departure tracking functionality that displays ongoing notifications to users.

## App Functionality

This Android app provides live train and bus departure tracking with the following features:

1. **Real-time Departure Updates**: Users can select specific train or bus departures to track
2. **Ongoing Notifications**: The app displays continuous countdown notifications showing:
   - Time remaining until departure
   - Current location of the vehicle (when available)
   - Number of stops away from destination
3. **Special Use Foreground Service**: Required for Android 14+ to maintain ongoing notifications

## Why Special Use Foreground Service Is Needed

### For Android 14+ (API Level 34+):
- Starting with Android 14, ongoing notifications require special use foreground services
- The `FOREGROUND_SERVICE_SPECIAL_USE` permission is mandatory for apps that need to show ongoing notifications
- This permission allows the app to display status notifications while tracking departures

### Technical Implementation:
The `TrainTrackingService` uses:
- `ServiceCompat.startForeground()` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for Android 14+
- `startForeground()` as fallback for older versions
- Ongoing notifications that show real-time departure tracking information

## User Benefits

This permission enables users to:
- Receive timely updates about their train/bus departures
- Track progress in real-time without constantly opening the app
- See accurate countdown times and location information
- Have reliable notifications even when the app is in background

## Compliance

This permission is used only for legitimate tracking of public transport services and does not access sensitive user data beyond what's necessary to provide train departure tracking functionality.

## App Limitations

This app does NOT:
- Access location services for tracking user's position
- Use GPS or other location-based APIs
- Access personal information beyond what's needed for transport tracking

The `FOREGROUND_SERVICE_SPECIAL_USE` permission is required solely to provide ongoing notifications for the live train departure tracking feature.