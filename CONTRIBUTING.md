# Contributing to InsidePacer

Thank you for your interest in contributing to InsidePacer! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Testing](#testing)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)
- [Reporting Bugs](#reporting-bugs)
- [Feature Requests](#feature-requests)

## Code of Conduct

This project follows a simple code of conduct:
- Be respectful and inclusive
- Focus on constructive feedback
- Welcome newcomers and help them get started
- Respect differing viewpoints and experiences

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/InsidePacer.git
   cd InsidePacer
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/gherrick0918/InsidePacer.git
   ```

## Development Setup

### Prerequisites

- **Android Studio** Arctic Fox or later
- **JDK 21** (required for Kotlin 2.0)
- **Android SDK 26+** (Android 8.0 Oreo or higher)
- **Git** for version control

### Initial Setup

1. Open the project in Android Studio
2. Sync Gradle files (should happen automatically)
3. (Optional) Set up Firebase for full feature testing:
   - See [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) for instructions
   - The app works without Firebase; features will be disabled gracefully
4. Build the project: `./gradlew build`
5. Run tests: `./gradlew test`

## Making Changes

### Branch Strategy

- Create a new branch for each feature or bugfix:
  ```bash
  git checkout -b feature/your-feature-name
  ```
- Use descriptive branch names:
  - `feature/add-workout-reminders`
  - `bugfix/fix-session-timer`
  - `docs/update-readme`

### Commit Messages

Write clear, concise commit messages:
- Use present tense ("Add feature" not "Added feature")
- Limit first line to 72 characters
- Add detailed description if needed

Good examples:
```
Add accessibility labels to session controls

- Add content descriptions for play/pause/stop buttons
- Ensure TalkBack properly announces workout state
- Add semantic properties to segment timeline
```

## Testing

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :app:testDebugUnitTest

# Run with detailed output
./gradlew test --info
```

### Writing Tests

- Add unit tests for all new functionality
- Follow existing test patterns in the codebase
- Test files are located in `app/src/test/java/`
- Current test coverage includes:
  - Domain models and state management
  - Data layer (database, repositories)
  - Core utilities (formatters, converters)
  - Services and background tasks
  - Analytics and backup functionality

### Test Organization

Tests are organized by layer:
- `domain/` - Domain model tests
- `data/` - Repository and database tests
- `core/` - Utility and formatter tests
- `service/` - Service and background task tests
- `ui/` - UI component and screen tests

## Code Style

### Kotlin Style Guide

Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):
- Use 4 spaces for indentation
- Use meaningful variable and function names
- Prefer immutable data structures (`val` over `var`)
- Use Kotlin's null safety features properly

### Compose UI Guidelines

- Use Material 3 design components
- Follow existing component patterns
- Add content descriptions for accessibility
- Use semantic properties for screen readers
- Keep composables focused and reusable

### Code Quality

```bash
# Run lint checks
./gradlew lint

# Review lint report
open app/build/reports/lint-results-debug.html
```

Fix all lint errors before submitting. Suppress warnings only when necessary with proper justification.

## Submitting Changes

### Pull Request Process

1. **Update your branch** with latest upstream changes:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests and lint**:
   ```bash
   ./gradlew test lint
   ```

3. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Create Pull Request**:
   - Go to the original repository on GitHub
   - Click "New Pull Request"
   - Select your branch
   - Fill in the PR template with:
     - Description of changes
     - Related issues (if any)
     - Testing performed
     - Screenshots (for UI changes)

5. **CI/CD Checks**:
   - GitHub Actions will automatically run tests and lint
   - Ensure all checks pass
   - Fix any issues identified by CI

### Pull Request Guidelines

- **One feature per PR** - Keep changes focused
- **Update documentation** - If you change functionality, update docs
- **Add tests** - New features should include tests
- **Keep it small** - Smaller PRs are easier to review
- **Be responsive** - Address review feedback promptly

## Reporting Bugs

### Before Reporting

1. Check if the bug has already been reported in Issues
2. Try to reproduce with the latest version
3. Gather relevant information

### Bug Report Template

When creating a bug report, include:

- **Description**: Clear description of the bug
- **Steps to Reproduce**: Numbered steps to reproduce the issue
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Environment**:
  - Android version
  - Device model
  - App version
- **Logs/Screenshots**: Any relevant error messages or screenshots
- **Additional Context**: Any other relevant information

## Feature Requests

We welcome feature requests! When proposing a new feature:

1. **Check existing issues** - It may already be planned
2. **Describe the problem** - What problem does this solve?
3. **Propose a solution** - How would you implement it?
4. **Consider alternatives** - What other approaches did you consider?
5. **Additional context** - Screenshots, mockups, or examples

## Project Structure

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed information about:
- App architecture and design patterns
- Module organization
- Data flow and state management
- Key components and their responsibilities

## Code Review

All submissions require review. We aim to:
- Provide feedback within a few days
- Be constructive and helpful
- Focus on code quality and maintainability
- Ensure consistency with project standards

## Getting Help

- **Questions?** Open a discussion on GitHub
- **Stuck?** Review existing code for examples
- **Documentation unclear?** Open an issue to improve it

## Recognition

Contributors will be recognized in:
- GitHub contributors list
- Release notes (for significant contributions)
- Project documentation (for major features)

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (see LICENSE file).

---

Thank you for contributing to InsidePacer! Your efforts help make this app better for everyone.
