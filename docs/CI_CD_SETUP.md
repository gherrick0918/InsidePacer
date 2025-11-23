# CI/CD Setup

This document describes the Continuous Integration and Continuous Deployment (CI/CD) setup for InsidePacer.

## GitHub Actions Workflow

The project uses GitHub Actions for automated builds, testing, and code quality checks. The workflow is defined in `.github/workflows/ci.yml`.

### Workflow Triggers

The CI pipeline runs on:
- Push to the `main` branch
- Pull requests targeting the `main` branch

### Pipeline Steps

1. **Checkout Code**: Uses `actions/checkout@v4` to clone the repository
2. **Setup JDK 21**: Installs Temurin JDK 21 required for the project
3. **Setup Android SDK**: Configures the Android SDK environment
4. **Grant Permissions**: Makes the Gradle wrapper executable
5. **Cache Gradle**: Caches Gradle dependencies for faster builds
6. **Create Firebase Stub**: Generates a test `google-services.json` file for CI builds
7. **Build**: Compiles the project using `./gradlew build`
8. **Run Tests**: Executes all unit tests with `./gradlew test`
9. **Run Lint**: Performs code quality checks with `./gradlew lint`
10. **Upload Artifacts**: Saves test results and lint reports (retained for 30 days)

### Firebase Configuration for CI

The workflow automatically creates a stub `google-services.json` file to allow builds without requiring actual Firebase credentials. This ensures the CI can build and test the app even without Firebase setup.

### Artifacts

The workflow uploads the following artifacts on every run:
- **test-results**: JUnit test reports and results
- **lint-results**: Android Lint HTML and XML reports

These artifacts can be downloaded from the Actions tab on GitHub for debugging failures.

## Running Tests Locally

To run tests locally before pushing:

```bash
# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :app:testDebugUnitTest

# Run lint checks
./gradlew lint

# Build the entire project
./gradlew build
```

## Test Coverage

The project includes comprehensive unit tests covering:
- **Domain Models**: Data classes and state management
- **Core Utilities**: Formatters, converters, and utility functions
- **Data Layer**: Database converters, mappers, and repositories
- **Engine**: Session scheduling and cue planning
- **Services**: Session management and intent handling
- **Analytics**: Event tracking and logging
- **Backup**: Encryption and backup operations
- **CSV**: Data export functionality
- **Health Connect**: Fitness data synchronization
- **Audio**: Beep player and audio cues

Current test count: **18 test files** with over 150 test cases.

## Continuous Improvement

The CI/CD setup is designed to:
- Catch bugs early through automated testing
- Maintain code quality with linting
- Ensure consistent builds across environments
- Provide quick feedback on pull requests
- Archive test results for debugging

## Troubleshooting

### Build Failures

If the CI build fails:
1. Check the workflow logs in the Actions tab
2. Download test artifacts to see detailed error messages
3. Run the same Gradle commands locally to reproduce the issue
4. Ensure your local build works before pushing

### Test Failures

If tests fail:
1. Review the test results artifact
2. Run the failing test locally: `./gradlew test --tests "TestClassName"`
3. Use `--info` or `--debug` flags for more verbose output

### Lint Issues

If lint checks fail:
1. Download the lint results artifact
2. Review the HTML report for details
3. Fix issues or suppress false positives appropriately
4. Re-run locally: `./gradlew lint`
