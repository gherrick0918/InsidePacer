# Room Database Migration

This document describes the migration from JSON file storage to Room Database in InsidePacer.

## Overview

The app has been migrated from JSON file-based storage to Room Database for improved reliability, performance, and data integrity. The migration is **automatic and transparent** to users - existing data is preserved.

## Architecture

### Database Structure

- **Database**: `AppDatabase` (version 1)
- **Tables**: `sessions`, `templates`, `programs`
- **Location**: `/data/data/app.insidepacer/databases/insidepacer_database`

### Components

#### Entities
- `SessionEntity` - Workout sessions
- `TemplateEntity` - Workout templates
- `ProgramEntity` - Training programs

#### DAOs (Data Access Objects)
- `SessionDao` - CRUD operations for sessions
- `TemplateDao` - CRUD operations for templates
- `ProgramDao` - CRUD operations for programs

#### Repositories
- `SessionRepoRoom` - Session data operations
- `TemplateRepoRoom` - Template data operations
- `ProgramRepoRoom` - Program data operations

#### Type Converters
- `Converters` - Handles serialization of complex types:
  - `List<Segment>` for workout segments
  - `List<List<String?>>` for program grids

## Automatic Migration

### First Launch After Update

When the app first launches with Room:

1. **Detection**: Checks for `.room_migration_complete` marker file
2. **Reading**: Reads existing JSON files (`sessions.json`, `templates.json`, `programs.json`)
3. **Migration**: Inserts all data into Room database
4. **Verification**: Creates marker file to prevent re-migration
5. **Archiving**: Renames JSON files to `.json.backup_<timestamp>`

### Migration Safety

- **Non-destructive**: Original JSON files are archived, not deleted
- **Idempotent**: Migration only runs once
- **Error handling**: Failed migrations are logged, app continues to function
- **Rollback**: Backup files can be manually restored if needed

## Backward Compatibility

### Backup/Restore System

The backup/restore system continues to work seamlessly:

- **Export**: Room repos provide `exportToJson()` method
- **Import**: Room repos provide `importFromJson()` method
- **Format**: JSON format is identical to original implementation
- **No changes**: Backup infrastructure unchanged

### API Compatibility

Original repository APIs are preserved:

```kotlin
// Original API still works
val repo = SessionRepo(context)
val sessions = repo.loadAll()
repo.append(newSession)
```

The original repository classes now delegate to Room implementations internally.

## Benefits

### Reliability
- ✅ **ACID transactions**: Atomic, Consistent, Isolated, Durable
- ✅ **Data integrity**: Foreign key constraints and validation
- ✅ **Error handling**: Better error recovery and reporting
- ✅ **Crash safety**: Database survives app crashes

### Performance
- ✅ **Faster queries**: Indexed lookups vs. full file reads
- ✅ **Lazy loading**: Load only what's needed
- ✅ **Background threads**: Automatic thread management
- ✅ **Caching**: Query results are cached

### Developer Experience
- ✅ **Type safety**: Compile-time SQL verification
- ✅ **Reactive updates**: Flow/LiveData support
- ✅ **Testing**: Room testing framework
- ✅ **Debugging**: Database Inspector in Android Studio

## Testing

### Unit Tests

Located in `app/src/test/java/app/insidepacer/data/db/`:

- `ConvertersTest` - Type converter tests
- `MappersTest` - Entity/domain mapper tests

Run tests:
```bash
./gradlew test
```

### Database Inspector

View database in Android Studio:
1. Run app in debug mode
2. View → Tool Windows → App Inspection
3. Select "Database Inspector"

## Troubleshooting

### Migration Issues

If migration fails:

1. **Check logs**: Look for "DatabaseMigration" tag
2. **Inspect backups**: JSON backups in `files/` directory
3. **Manual restore**: Copy backup files and remove marker:
   ```bash
   adb shell
   cd /data/data/app.insidepacer/files
   cp sessions.json.backup_* sessions.json
   cp templates.json.backup_* templates.json
   cp programs.json.backup_* programs.json
   rm .room_migration_complete
   ```

### Clear Database

To start fresh (loses all local data):

```bash
adb shell pm clear app.insidepacer
```

Or via Settings → Apps → InsidePacer → Storage → Clear data

## Future Enhancements

Potential improvements:

1. **Schema versioning**: Proper migration paths for schema changes
2. **Indexes**: Add indexes for frequently queried fields
3. **Foreign keys**: Link sessions to programs
4. **Full-text search**: Search templates by name
5. **Aggregations**: Pre-computed statistics
6. **Multi-user**: Support multiple user profiles

## Dependencies

Added to `app/build.gradle.kts`:

```kotlin
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
testImplementation("androidx.room:room-testing:2.6.1")
```

KSP plugin:
```kotlin
id("com.google.devtools.ksp") version "2.0.21-1.0.28"
```

## Resources

- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Migrating to Room](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Testing Room](https://developer.android.com/training/data-storage/room/testing-db)
