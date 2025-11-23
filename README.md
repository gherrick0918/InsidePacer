# InsidePacer

A fitness tracking app for Android with workout sessions, training programs, and health monitoring.

## Features

- **Workout Sessions**: Track interval training with customizable segments
- **Templates**: Create and reuse workout templates
- **Training Programs**: Build or generate multi-week training plans
- **Health Connect Integration**: Sync workouts with Android Health Connect
- **Google Drive Backup**: Securely backup and restore your data
- **Firebase Integration**: Analytics, crash reporting, and performance monitoring

## Firebase Services (Optional)

InsidePacer integrates with Firebase to provide enhanced features while staying within the free tier:

- **Analytics**: Track feature usage and engagement
- **Crashlytics**: Automatic crash reporting for better reliability
- **Performance Monitoring**: Monitor app performance
- **Remote Config**: Feature flags and remote configuration

See [Firebase Setup Guide](docs/FIREBASE_SETUP.md) for detailed setup instructions.

## Setup

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 26+ (Android 8.0)
- JDK 21

### Building

1. Clone the repository
2. (Optional) Set up Firebase (see [Firebase Setup Guide](docs/FIREBASE_SETUP.md))
3. Open in Android Studio
4. Build and run

### Firebase Setup (Optional)

Firebase services are optional but recommended for better analytics and crash reporting:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app with package name `app.insidepacer`
3. Download `google-services.json` and place in `app/` directory
4. Enable Analytics, Crashlytics, and Performance Monitoring in Firebase Console

For detailed instructions, see [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md)

**Note**: The app works perfectly without Firebase configuration - Firebase features will be disabled if the configuration file is not present.

## Development

### Testing

The project includes comprehensive unit tests. To run tests:

```bash
./gradlew test
```

For more information on testing and CI/CD, see [CI/CD Setup Guide](docs/CI_CD_SETUP.md).

### Code Quality

The project uses Android Lint for code quality checks:

```bash
./gradlew lint
```

## Contributing

Pull requests are automatically built and tested using GitHub Actions. Ensure all tests pass locally before submitting.
