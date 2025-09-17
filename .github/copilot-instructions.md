# Burp Zota Signer Extension
Burp Zota Signer is a Java 21 Burp Suite extension that automatically signs Zota API requests. It's built with Gradle and the Montoya API, packaged as a shadow JAR for easy distribution to Burp Suite users.

**CRITICAL**: Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Bootstrap and Build
**NEVER CANCEL builds or tests** - wait for completion. Use timeouts of 60+ minutes for safety.

#### Java 21 Setup (Required)
- Install Java 21: `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk`
- Set environment: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH`
- Verify: `java -version` should show OpenJDK 21

#### Build Commands
- **Full build**: `./gradlew clean build` -- takes ~30 seconds. Set timeout to 60+ minutes.
- **Plugin JAR**: `./gradlew clean shadowJar` -- takes ~5 seconds. Set timeout to 60+ minutes.
- **Run tests**: `./gradlew test` -- takes ~2 seconds. No unit tests exist yet.
- **Run checks**: `./gradlew check` -- takes ~2 seconds. Includes compilation and basic verification.

#### Build Outputs
- Built JAR: `build/libs/burp-zota-signer-0.1.1.jar` (shadow JAR, ~2.3MB)
- This JAR excludes the Montoya API (provided by Burp at runtime)
- Ready to load into Burp Suite via **Extensions → Add**

### Validation
**MANUAL VALIDATION REQUIREMENT**: Since this is a Burp Suite extension, you cannot fully exercise the application outside Burp Suite. However, you should validate:

1. **Build verification**: Ensure all build commands complete successfully
2. **Code compilation**: Verify no compilation errors or warnings (some are expected)
3. **JAR structure**: Check that the shadow JAR is created in `build/libs/` 
4. **Dependencies**: Verify Jackson libraries are bundled but Montoya API is excluded

**Important**: The extension requires Burp Suite to run. You cannot test the actual signing functionality in this environment, but you can validate the build process.

Always run `./gradlew check` before committing changes to ensure compilation succeeds.

## Project Structure

### Source Organization
```
src/main/java/burp/zota/
├── ZotaExtension.java                  # Main entry point, implements BurpExtension
├── config/ZotaConfig.java             # Configuration persistence
├── controller/ZotaController.java     # Central controller for UI and signing logic
├── profile/                           # Profile management
│   ├── ProfileManager.java            # Profile persistence and management
│   └── ZotaProfile.java              # Profile model (credentials, API base)
├── signer/ZotaSigner.java            # Core signing engine and endpoint detection
├── sample/SampleFactory.java         # Generate sample requests for testing
├── ui/                               # Swing UI components
│   ├── ZotaSettingsTab.java         # Main settings panel
│   └── menu/ZotaRepeaterContextMenu.java  # Context menu integration
└── util/                            # Utility classes
    ├── QueryString.java             # Query string parsing
    ├── SignatureUtil.java           # SHA-256 signature generation
    └── ZotaLogger.java             # Logging wrapper
```

### Key Files
- `build.gradle`: Build configuration, dependencies, shadow JAR setup
- `.github/workflows/build.yml`: CI/CD pipeline for releases
- `AGENTS.md`: Detailed development guidelines and conventions
- `README.md`: User-facing documentation and quick start guide

## Navigation and Development

### Important Code Locations
- **Entry point**: `src/main/java/burp/zota/ZotaExtension.java` - implements BurpExtension interface
- **Core signing logic**: `src/main/java/burp/zota/signer/ZotaSigner.java` - handles endpoint detection and signature generation
- **UI components**: `src/main/java/burp/zota/ui/ZotaSettingsTab.java` - main settings panel
- **Configuration**: `src/main/java/burp/zota/config/ZotaConfig.java` - persistent settings

### Common Development Patterns
- **Profile management**: Stored in Burp project using Montoya persistence API
- **Endpoint detection**: Uses path matching and HTTP method detection
- **Signature generation**: SHA-256 lowercase hex of concatenated parameters
- **UI updates**: Must be performed on Swing EDT
- **Logging**: Use `ZotaLogger` wrapper, never log secrets

### Supported Zota Endpoints
The extension recognizes and signs these Zota API endpoints:
- Deposit (`POST /api/v1/deposit/request/`)
- Payout (`POST /api/v1/payout/request/`)  
- Order Status (`GET /api/v1/query/order-status/`)
- Orders Report CSV (`GET /api/v1/query/orders-report/csv/`)
- Current Balance (`GET /api/v1/query/current-balance/`)
- Exchange Rates (`GET /api/v1/query/exchange-rates/`)

## Build Configuration Details

### Dependencies
- **Montoya API**: `compileOnly` dependency (provided by Burp at runtime)
- **Jackson**: Full BOM-managed JSON processing libraries (bundled in JAR)
- **Java 21**: Required for compilation and runtime

### Shadow JAR Configuration
- Archives exclude Montoya API to avoid conflicts
- Merges service files for proper dependency resolution
- Excludes signature files to prevent security issues
- Uses empty classifier to replace default JAR

### Release Process
- **Automatic**: Push to `main` creates GitHub release with version from `build.gradle`
- **Manual**: Push tag like `v1.0.0` triggers release workflow
- **CI timing**: Build takes ~1 minute, artifact upload takes ~30 seconds

## Common Tasks

### Adding New Functionality
1. Always check `AGENTS.md` for coding standards and module organization
2. Follow existing package layout: `burp.zota.*` 
3. Use proper naming conventions: PascalCase classes, camelCase methods
4. Keep utilities side-effect free and never log secrets
5. Update documentation if user-facing behavior changes

### Testing Changes
1. Run `./gradlew clean build` to ensure compilation
2. Check `build/libs/` for generated JAR
3. Verify JAR size (~2-3MB) and name matches version
4. For UI changes, load JAR in Burp Suite if available

### Repository Files Overview
```
burp-zota-signer/
├── .github/workflows/build.yml       # CI/CD pipeline
├── .gitignore                        # Git exclusions
├── AGENTS.md                         # Development guidelines  
├── LICENSE                           # MIT license
├── README.md                         # User documentation
├── build.gradle                      # Build configuration
├── gradle/wrapper/                   # Gradle wrapper files
├── gradlew                          # Unix Gradle wrapper
├── gradlew.bat                      # Windows Gradle wrapper
├── settings.gradle                  # Project settings
└── src/main/java/burp/zota/         # Source code
```

### Environment Variables for Development
Always set these when working with the project:
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
```

## Troubleshooting

### Build Issues
- **"Unsupported Java version"**: Ensure Java 21 is installed and JAVA_HOME is set
- **"Could not resolve dependencies"**: Check internet connection, dependencies are from Maven Central
- **"Permission denied"**: Use `sudo` for system-level Java installation

### Development Issues  
- **Compilation warnings**: Expected due to missing Javadoc, not errors
- **No tests found**: Normal, unit tests don't exist yet per `AGENTS.md`
- **JAR too large**: Should be ~2-3MB, if larger check shadow configuration

Remember: This extension requires Burp Suite for full functionality testing. Focus on build validation and code quality in this environment.