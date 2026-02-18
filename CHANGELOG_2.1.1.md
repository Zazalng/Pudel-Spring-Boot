# Pudel v2.1.1 - Bug Fixes & Security Enhancements

## Release Date: 2026-02-18

This release addresses several issues reported after the 2.0.0-rc release and includes security enhancements.

---

## 🐛 Bug Fixes

### [Core] Circular Dependency Fix (Critical)
- **Fixed**: Application startup failure caused by circular bean dependencies
- **Root Cause**: `JDA` → `InteractionEventListener` → `CommandExecutionService` → `JDA` cycle
- **Solution**:
  - Removed unnecessary JDA dependency from `CommandExecutionService` (it receives Guild objects in methods)
  - Added `@Lazy` annotation to JDA injection in `PingCommandHandler` and `PluginService`
- **Related Issues**: Boot failure, APPLICATION FAILED TO START error

### [Core] DPoP Thread Memory Leak Fix (#12 related)
- **Fixed**: `dpop-jti-cleanup` thread not stopping on application shutdown
- **Solution**: Implemented `DisposableBean` interface in `DPoPService` with proper thread shutdown handling
- **Impact**: Clean shutdown without memory leak warnings

### [Core] CORS DPoP Header Support (#12 related)
- **Fixed**: CORS policy blocking DPoP header in preflight requests
- **Error**: `Request header field dpop is not allowed by Access-Control-Allow-Headers`
- **Solution**: Added `DPoP` to allowed headers and `DPoP-Nonce`, `WWW-Authenticate` to exposed headers
- **File**: `SecurityConfiguration.java`

### [Core] Plugin State Synchronization (#5, #6)
- **Already implemented in previous versions**: `resetPluginStatesOnStartup()` method resets all plugin states to disabled on bot restart
- **Behavior**: Plugins are marked as disabled in database on startup, must be re-enabled by admin
- **Note**: This is by design to prevent stale plugin state issues

### [Core] Slash Command Logging (#7)
- **Status**: Already implemented in `InteractionEventListener`
- **Verification**: Both slash commands and context menus properly log to guild log channels via `CommandExecutionService.sendCommandLog()`
- **Note**: Ensure guild has a log channel configured (`/settings logchannel`)

---

## ✨ Enhancements

### [API] Help Command Improvements
- **Enhanced**: `/help` and `!help` now show both text commands AND slash commands
- **Added**: Slash commands section on first page of help
- **Added**: Support for `!help /commandname` to show slash command details
- **Added**: Dynamic descriptions from `CommandMetadataRegistry` for plugin commands
- **Added**: Plugin name display in command details
- **Dependencies**: Added `CommandMetadataRegistry` to `HelpCommandHandler`

### [API] Enhanced Annotation Permissions (#10)
- **Status**: Already implemented in previous versions
- **Annotations**: Both `@TextCommand` and `@SlashCommand` use JDA `Permission[]` type
- **Benefit**: Type-safe permissions with IDE autocomplete

### [Vue] Security Enhancement - Logout Behavior (#12)
- **Fixed**: User logout now also clears admin session by default
- **Reason**: Prevents potential token reuse attacks
- **New signature**: `logout(clearAdmin = true)`
- **DPoP**: Keys are properly cleared on logout

---

## 📝 Notes

### Issue #4 - Agent Tools Role Restrictions
- **Status**: Deferred for future consideration
- **Reason**: Complex implementation requiring significant resource investment
- **Alternative**: Use slash commands with built-in permission checks for sensitive operations

### Issue #8 - Plugin Database Handler Behavior
- **Status**: Requires further investigation
- **Note**: Plugin IDs are normalized in `PluginDatabaseService` to prevent case-sensitivity issues
- **Recommendation**: Use consistent plugin names across versions

---

## 📋 Files Changed

### Java Backend (pudel-core)
- `CommandExecutionService.java` - Removed JDA dependency, no longer extends BaseService
- `PingCommandHandler.java` - Added @Lazy to JDA injection
- `PluginService.java` - Added @Lazy to JDA injection
- `AuthService.java` - Added @Lazy to JDA injection
- `BotService.java` - Added @Lazy to JDA injection
- `GuildSettingsService.java` - Added @Lazy to JDA injection
- `DPoPService.java` - Implemented DisposableBean for proper shutdown
- `SecurityConfiguration.java` - Added DPoP to CORS allowed headers
- `DiscordAPIService.java` - Improved error logging for OAuth failures
- `HelpCommandHandler.java` - Added CommandMetadataRegistry dependency, slash command support
- `HelpSessionManager.java` - Enhanced buildHelpEmbed to show slash commands

### Vue Frontend (Pudel-Vue)
- `stores/auth.js` - Enhanced logout to clear admin session by default

---

## 🚀 Upgrade Instructions

1. **Rebuild the application**:
   ```bash
   mvn clean package -DskipTests
   ```

2. **No database migrations required** for this release

3. **Plugin developers**: No API changes - existing plugins remain compatible

4. **Administrators**: After restart, plugins will be in disabled state (by design) - re-enable as needed

---

## ⚠️ Known Issues

- Maven warnings about `sun.misc.Unsafe` are from Maven itself, not Pudel code
- IntelliJ may show warnings about unused parameters - these are false positives for framework-level code

---

**Full Changelog**: Compare v2.0.0-rc...v2.1.1

