# Project Plan

An Android app to display the departure board of a London tube or bus station.
UI concept inspired by Google Pixel Clock app's World Clock screen:
- Main screen displays a list/cards of user-selected stations (tube / bus stops) showing real-time or simulated/API-backed upcoming departures, line badges, platform/direction, and departure countdowns/times.
- Floating Add button (+) to add new stations.
- Station Search / Picker screen or bottom sheet allowing users to search London Underground stations and bus stops and select them.
- Selected stations persist and show their departure boards on the main screen.
- Stations can be reordered or removed (swipe to delete or delete action).
- Modern Material 3 Expressive UI design (expressive shapes, typography, dynamic colors, smooth transitions, empty state, badges).

## Project Brief

# Project Brief: London Transit Departure Board

## Features
- **Departure Board Dashboard:** View real-time upcoming departures for saved London Underground stations and bus stops in Pixel Clock-style cards, displaying official transit line badges, platform/direction details, and live arrival countdowns.
- **Station Search & Picker:** Search and select London Underground stations and bus stops via an intuitive search sheet to add them to the departure board.
- **Station Management:** Reorder or remove saved stations (with swipe-to-delete or remove action) to tailor the home screen.
- **Saved Stations Persistence:** Automatically store user-selected stations so preferences persist seamlessly across app restarts.

## High-Level Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3 Expressive theming and components
- **Navigation & Adaptive Strategy:** Jetpack Navigation 3 (state-driven) and Jetpack Compose Material Adaptive (`androidx.compose.material3.adaptive`)
- **Architecture & Concurrency:** MVVM with Android Architecture Components (`ViewModel`, `StateFlow`) and Kotlin Coroutines
- **Networking:** Ktor Client / Retrofit for consuming TfL (Transport for London) Unified API
- **Persistence:** Jetpack DataStore Preferences for storing selected station identifiers and order

## UI Design Image
![UI Design](/Users/fung/AndroidStudioProjects/DepartureBoard/input_images/departure_board_mockup.jpg)
Image path = /Users/fung/AndroidStudioProjects/DepartureBoard/input_images/departure_board_mockup.jpg

## Implementation Steps

### Task_1_DataAndNetworkSetup: Implement TfL Unified API networking client/models, DataStore station persistence, and transit repository for fetching station searches and live arrivals with line branding colors/badges.
- **Status:** COMPLETED
- **Updates:** Task_1 completed: TfL API networking, Moshi models, line colors/badges, offline fallback, DataStore persistence, TransitRepository, and unit tests implemented and verified with assembleDebug and testDebugUnitTest.
- **Acceptance Criteria:**
  - TfL API models, endpoints and network client configured to search stations/stops and fetch arrival predictions
  - DataStore repository configured for persisting and reordering saved stations list
  - Transit repository provides Flow of departures and station search functionality
  - Offline fallback/sample station data integrated if TfL API network limits are hit
  - build pass
- **Duration:** N/A

### Task_2_DepartureBoardDashboardUI: Build the Departure Board Dashboard screen using Jetpack Compose and Material 3 Expressive, featuring Pixel Clock-inspired departure cards with TfL line badges, platform/direction details, live countdowns, pull-to-refresh/auto-refresh, and swipe/action to remove stations.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Dashboard displays saved stations with upcoming departures styled like Pixel Clock cards
  - Official TfL line colors, badges, and real-time countdowns rendered accurately
  - Swipe-to-delete or remove action to delete saved stations with undo or prompt
  - Pull-to-refresh and periodic auto-refresh updating departure countdowns
  - The implemented UI must match the design provided in /Users/fung/AndroidStudioProjects/DepartureBoard/input_images/departure_board_mockup.jpg
  - build pass
- **StartTime:** 2026-10-03 14:48:17 BST

### Task_3_StationSearchAndPicker: Implement the Station Search & Picker sheet/dialog allowing users to search London Underground stations and bus stops, view query results, and add them to their saved departure board.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Add (+) FAB or top button opens searchable sheet for Tube stations and bus stops
  - Live query search against TfL API displaying matching stations with available transport modes
  - Selecting a station adds it to DataStore and immediately populates the departure board
  - The implemented UI must match the design provided in /Users/fung/AndroidStudioProjects/DepartureBoard/input_images/departure_board_mockup.jpg
  - build pass

### Task_4_RunAndVerify: Run and verify the application end-to-end. Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Application builds and launches cleanly without crashes
  - End-to-end station search, persistence across restarts, live departures and deletion verified
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - The implemented UI must match the design provided in /Users/fung/AndroidStudioProjects/DepartureBoard/input_images/departure_board_mockup.jpg

