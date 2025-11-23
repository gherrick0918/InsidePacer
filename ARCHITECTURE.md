# InsidePacer Architecture

This document provides an overview of the InsidePacer application architecture, design patterns, and key components.

## Table of Contents

- [Overview](#overview)
- [Architecture Pattern](#architecture-pattern)
- [Module Structure](#module-structure)
- [Core Layers](#core-layers)
- [Data Flow](#data-flow)
- [Key Components](#key-components)
- [Third-Party Integrations](#third-party-integrations)
- [Testing Strategy](#testing-strategy)

## Overview

InsidePacer is a fitness tracking Android application built with modern Android development practices:

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose with Material 3
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14+ (API 36)
- **Build System**: Gradle with Kotlin DSL
- **Architecture**: Clean Architecture with MVVM pattern

## Architecture Pattern

The app follows **Clean Architecture** principles with clear separation of concerns:

```
┌─────────────────────────────────────────────┐
│          Presentation Layer (UI)            │
│  Jetpack Compose Screens & Components      │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│           Domain Layer                      │
│  Business Logic & Models                    │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│            Data Layer                       │
│  Repositories & Data Sources                │
└─────────────────┬───────────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
┌────────▼──────┐  ┌──────▼────────┐
│  Local DB     │  │  External     │
│  (Room)       │  │  APIs         │
└───────────────┘  └───────────────┘
```

### Key Principles

1. **Dependency Rule**: Dependencies point inward (UI → Domain ← Data)
2. **Single Responsibility**: Each component has one clear purpose
3. **Immutability**: Prefer immutable data structures
4. **Testability**: Design for easy unit testing

## Module Structure

### Root Project Structure

```
InsidePacer/
├── app/                    # Main application module
├── healthconnect/          # Health Connect integration module
├── docs/                   # Documentation
├── scripts/                # Build and automation scripts
└── gradle/                 # Gradle wrapper and configuration
```

### App Module Structure

```
app/src/main/java/app/insidepacer/
├── analytics/              # Analytics and event tracking
├── audio/                  # Audio playback and cues
├── backup/                 # Backup/restore functionality
│   ├── ui/                 # Backup UI screens
│   ├── store/              # Local backup storage
│   └── drive/              # Google Drive integration
├── core/                   # Core utilities and formatters
├── csv/                    # CSV export functionality
├── data/                   # Data layer (repositories)
│   └── db/                 # Room database implementation
├── di/                     # Dependency injection
├── domain/                 # Domain models and business logic
├── engine/                 # Session engine and scheduling
├── healthconnect/          # Health Connect sync
├── service/                # Background services
└── ui/                     # UI layer (Compose)
    ├── components/         # Reusable UI components
    ├── history/            # Workout history screens
    ├── navigation/         # Navigation setup
    ├── onboarding/         # Onboarding flow
    ├── profile/            # User profile
    ├── programs/           # Training programs
    ├── schedule/           # Schedule and calendar
    ├── session/            # Active session screens
    ├── settings/           # App settings
    ├── statistics/         # Statistics dashboard
    └── theme/              # Material 3 theme
```

## Core Layers

### 1. Presentation Layer (UI)

**Technology**: Jetpack Compose with Material 3

**Responsibilities**:
- Display data to users
- Handle user interactions
- Navigate between screens
- Manage UI state

**Key Files**:
- `AppNav.kt` - Main navigation setup
- `SessionScreen.kt` - Active workout session
- `HistoryScreen.kt` - Workout history
- `StatisticsScreen.kt` - Statistics dashboard
- `ProgramEditorScreen.kt` - Training program creation

**Patterns**:
- Composable functions for UI components
- State hoisting for data management
- Side effects with `LaunchedEffect` and `DisposableEffect`
- Material 3 design system

### 2. Domain Layer

**Location**: `app/insidepacer/domain/`

**Responsibilities**:
- Define core business entities
- Encapsulate business rules
- Provide models for data transfer

**Key Models**:
```kotlin
// Session data
data class SessionLog(
    val id: String,
    val programId: String?,
    val startMillis: Long,
    val endMillis: Long,
    val totalSeconds: Int,
    val segments: List<Segment>,
    val aborted: Boolean,
    val notes: String? = null
)

// Workout segment
data class Segment(
    val speed: Double, 
    val seconds: Int
)

// Training template
data class Template(
    val id: String,
    val name: String,
    val segments: List<Segment>
)

// Training program
data class Program(
    val id: String,
    val name: String,
    val startEpochDay: Long,
    val weeks: Int,
    val daysPerWeek: Int,
    val grid: List<List<String?>>
)

// Statistics
data class WorkoutStatistics(...)
data class PersonalRecord(...)
data class PeriodStatistics(...)
```

**Session State**:
```kotlin
data class SessionState(
    val status: SessionStatus,
    val currentSegment: Int,
    val segments: List<Segment>,
    val elapsedSeconds: Int,
    val totalSeconds: Int,
    val currentSegmentLabel: String?
)
```

### 3. Data Layer

**Location**: `app/insidepacer/data/`

**Responsibilities**:
- Manage data sources (local database, external APIs)
- Provide repositories for data access
- Handle data persistence and caching

**Key Repositories**:

1. **SessionRepo** (`SessionRepoRoom.kt`)
   - CRUD operations for workout sessions
   - Query historical data
   - Update session notes

2. **TemplateRepo** (`TemplateRepo.kt`)
   - Manage workout templates
   - CRUD operations for templates

3. **ProgramRepo** (`ProgramRepoRoom.kt`)
   - Manage training programs
   - Schedule management

4. **StatisticsRepository** (`StatisticsRepository.kt`)
   - Calculate workout statistics
   - Track personal records
   - Analyze trends

5. **SettingsRepo** (`SettingsRepo.kt`)
   - User preferences
   - App configuration
   - DataStore integration

**Database**: Room Persistence Library
- `AppDatabase.kt` - Main database class
- `SessionEntity`, `TemplateEntity`, `ProgramEntity` - Database entities
- `Converters.kt` - Type converters for complex types
- `Mappers.kt` - Entity to domain model mapping

## Data Flow

### Typical Data Flow Example: Viewing Workout History

```
1. User navigates to History screen
   ↓
2. HistoryScreen composable launches
   ↓
3. HistoryScreen calls SessionRepo.getAllSessions()
   ↓
4. SessionRepo queries Room database
   ↓
5. Database returns SessionEntity list
   ↓
6. Mappers convert entities to SessionLog models
   ↓
7. SessionRepo returns List<SessionLog>
   ↓
8. State is updated with session list
   ↓
9. Compose recomposes UI with data
   ↓
10. User sees workout history
```

### State Management

- **UI State**: Managed with `remember`, `mutableStateOf`, `derivedStateOf`
- **Persistent State**: DataStore Preferences and Room Database
- **Flow-based**: Reactive data streams with Kotlin Flow
- **Lifecycle-aware**: Using `collectAsState()` in Compose

## Key Components

### 1. Session Engine

**Location**: `app/insidepacer/engine/`

**Purpose**: Manages active workout sessions

**Key Classes**:
- `CountdownCuePlanner.kt` - Plans audio cues for segments
- Session scheduling and timing logic

**Features**:
- Countdown timers
- Segment transitions
- Audio cue planning
- State management

### 2. Session Service

**Location**: `app/insidepacer/service/`

**Purpose**: Background service for active workouts

**Key Files**:
- `SessionService.kt` - Foreground service
- `SessionIntents.kt` - Service intent handling

**Features**:
- Runs in foreground with notification
- Manages session lifecycle
- Handles play/pause/stop actions
- Updates notification status

### 3. Analytics

**Location**: `app/insidepacer/analytics/`

**Purpose**: Track user behavior and app performance

**Integration**: Firebase Analytics (optional)

**Events Tracked**:
- Session start/complete/abort
- Template creation/use
- Program creation
- Feature usage

### 4. Backup System

**Location**: `app/insidepacer/backup/`

**Purpose**: Backup and restore user data

**Features**:
- Local encryption (AES-256)
- Google Drive integration
- Backup all templates, programs, and sessions
- Privacy-focused (encrypted before upload)

**Key Components**:
- `BackupRepository.kt` - Backup orchestration
- `LocalCrypto.kt` - Encryption utilities
- `GoogleDriveBackupStore.kt` - Drive API integration
- `BackupScreen.kt` - User interface

### 5. Health Connect Integration

**Location**: `app/insidepacer/healthconnect/` and `healthconnect/` module

**Purpose**: Sync workouts with Android Health Connect

**Features**:
- Write workout sessions to Health Connect
- Track exercise sessions with heart rate zones
- Permissions management
- Privacy-compliant data sharing

**Key File**: `HealthConnectSessionSyncer.kt`

### 6. CSV Export

**Location**: `app/insidepacer/csv/`

**Purpose**: Export workout data to CSV files

**Features**:
- Export all sessions to CSV
- Include all metadata (notes, segments, timing)
- Compatible with spreadsheet applications

**Key File**: `CsvWriter.kt`

## Third-Party Integrations

### Firebase (Optional)

**Services Used**:
- **Analytics**: User behavior and feature usage tracking
- **Crashlytics**: Crash reporting and diagnostics
- **Performance Monitoring**: App performance metrics
- **Remote Config**: Feature flags and A/B testing

**Graceful Degradation**: App works fully without Firebase; services disabled if not configured.

### Google Play Services

- **Google Drive API**: Backup and restore
- **Google Sign-In**: Authentication for Drive access

### Android Jetpack

- **Compose**: Modern declarative UI
- **Room**: Local database
- **DataStore**: Preferences storage
- **Navigation**: Navigation component
- **Lifecycle**: Lifecycle-aware components

### Health Connect

- Android Health Connect API for fitness data interoperability

### Kotlin Libraries

- **Kotlinx Serialization**: JSON serialization
- **Coroutines**: Asynchronous programming
- **Flow**: Reactive streams

## Testing Strategy

### Unit Tests

**Location**: `app/src/test/java/`

**Coverage Areas**:
1. **Domain Models** (`domain/ModelsTest.kt`, `SessionStateTest.kt`)
   - Model validation
   - State transitions

2. **Data Layer** (`data/SessionRepoRoomTest.kt`, `StatisticsRepositoryTest.kt`)
   - Repository operations
   - Database queries
   - Data mapping

3. **Core Utilities** (`core/FormattersTest.kt`)
   - Formatters and converters
   - Edge case handling

4. **Engine** (`engine/CountdownCuePlannerTest.kt`)
   - Session logic
   - Cue planning

5. **Services** (`service/SessionIntentsTest.kt`)
   - Intent handling
   - Service logic

6. **Analytics** (`analytics/AnalyticsServiceTest.kt`)
   - Event tracking
   - Event properties

7. **Backup** (`backup/BackupRepositoryTest.kt`, `LocalCryptoTest.kt`)
   - Encryption/decryption
   - Backup/restore logic

8. **CSV** (`csv/CsvWriterTest.kt`)
   - CSV generation
   - Data formatting

9. **Health Connect** (`healthconnect/HealthConnectSessionSyncerTest.kt`)
   - Health Connect integration
   - Data synchronization

**Test Framework**: JUnit 4 with Kotlin test utilities

### UI Tests

**Location**: `app/src/test/java/app/insidepacer/ui/`

**Tests**:
- `SettingsScreenTest.kt` - Settings screen component tests

### Instrumentation Tests

**Location**: `app/src/androidTest/java/`

**Purpose**: Integration tests on real/emulated devices

### CI/CD Testing

**GitHub Actions** workflow runs on every PR:
- Build verification
- All unit tests
- Lint checks
- Test result artifacts

See [docs/CI_CD_SETUP.md](docs/CI_CD_SETUP.md) for details.

## Design Patterns

### Repository Pattern

Abstracts data access logic from business logic:
```kotlin
interface SessionRepo {
    suspend fun getAllSessions(): List<SessionLog>
    suspend fun getSession(id: String): SessionLog?
    suspend fun insertSession(session: SessionLog)
    suspend fun updateNotes(sessionId: String, notes: String?)
}
```

### Dependency Injection

Manual dependency injection in `di/` package:
- Simple factory pattern
- Singleton instances where appropriate
- Context-based injection

### Observer Pattern

Kotlin Flow for reactive data:
```kotlin
val sessions: Flow<List<SessionLog>> = sessionRepo.getAllSessionsFlow()
```

### State Management

Compose state management:
```kotlin
var sessions by remember { mutableStateOf<List<SessionLog>>(emptyList()) }

LaunchedEffect(Unit) {
    sessions = sessionRepo.getAllSessions()
}
```

## Security & Privacy

### Data Protection

1. **Local Encryption**: User data encrypted before backup
2. **Secure Storage**: Room database, DataStore
3. **Network Security**: HTTPS only
4. **Permissions**: Minimal permissions requested

### Privacy

1. **Local-First**: All data stored locally by default
2. **Opt-in Cloud**: Google Drive backup is optional
3. **No Tracking**: Firebase is optional; no third-party trackers
4. **Data Portability**: CSV export for user data

## Performance Considerations

### Database

- Indexed queries for fast lookups
- Lazy loading for large datasets
- Efficient queries with Room

### UI Performance

- Compose recomposition optimization
- `remember` for expensive computations
- `LazyColumn` for scrollable lists
- Avoid unnecessary recompositions

### Background Work

- Foreground service for active sessions
- Kotlin coroutines for async operations
- Efficient notification updates

## Future Enhancements

### Planned Features

1. **Search Enhancement**: Advanced filtering and search
2. **Workout Reminders**: Scheduled notifications
3. **Companion Apps**: Wear OS, web dashboard
4. **Enhanced Metadata**: Segment labels and descriptions
5. **Social Features**: Share workouts and progress

### Technical Debt

- Migrate to full Koin/Hilt for DI
- Add UI testing coverage
- Implement WorkManager for scheduled tasks
- Add offline-first architecture patterns

## Resources

### Documentation

- [README.md](README.md) - Project overview
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) - Firebase configuration
- [docs/CI_CD_SETUP.md](docs/CI_CD_SETUP.md) - CI/CD pipeline
- [docs/SESSION_NOTES.md](docs/SESSION_NOTES.md) - Session notes feature

### External References

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3 Design](https://m3.material.io/)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

This architecture is designed to be maintainable, testable, and scalable. For questions or suggestions, please open an issue or discussion on GitHub.
