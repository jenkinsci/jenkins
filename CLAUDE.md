# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

Jenkins uses Maven as its primary build system with Yarn for frontend assets.

### Core Building Commands
```bash
# Build WAR file quickly without tests
mvn -am -pl war,bom -Pquick-build clean install

# Launch development instance
MAVEN_OPTS='--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED' mvn -pl war jetty:run

# Build with frontend assets (two terminals needed)
# Terminal 1: Start dev server without frontend processing
MAVEN_OPTS='--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED' mvn -pl war jetty:run -Dskip.yarn

# Terminal 2: Start webpack dev server
yarn start
```

### Testing Commands
```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=ClassName

# Run tests in specific module
mvn -pl core test
```

### Frontend Development
```bash
# Enable Corepack (if Node.js already installed)
corepack enable

# Add Node/Yarn to path (if using downloaded versions)
export PATH=$PWD/node:$PWD/node/node_modules/corepack/shims:$PATH

# Install dependencies
yarn

# Build frontend assets
yarn start
```

## Code Architecture and Structure

### High-Level Architecture

Jenkins follows a **modular plugin architecture** built around several core concepts:

**Core Modules:**
- `core/` - Jenkins core functionality (models, services, extension points)
- `war/` - Web application assembly and frontend assets
- `cli/` - Command-line interface
- `test/` - Integration tests
- `bom/` - Bill of materials for dependency management

**Key Architectural Patterns:**

**Extension Point System**: Jenkins uses `@Extension` annotations with automatic discovery
- ExtensionPoint interfaces define plugin contracts
- ExtensionList/ExtensionFinder manages discovery and loading
- Plugins extend core functionality without modifying core code

**Stapler Web Framework**: URLs map to Java object methods
- `jenkins.model.Jenkins` is the root web object
- Method names become URL paths (e.g., `doSomething()` → `/something`)
- Jelly templates provide UI rendering

**Node Provisioning Architecture** (relevant to recent cloud provisioning work):
- `Cloud` - Abstract base for cloud providers (EC2, Kubernetes, etc.)
- `NodeProvisioner` - Coordinates agent provisioning across clouds
- `NodeProvisioner.Strategy` - Pluggable provisioning algorithms
- `NoDelayProvisionerStrategy` - Immediate provisioning for cloud environments

### Recent Cloud Provisioning Enhancements

**New Cloud Provisioning Limits System** (added for over-provisioning prevention):
- `CloudProvisioningLimits` - Thread-safe tracking of executor counts per cloud/template
- Enhanced `Cloud` interface with `supportsProvisioningLimits()`, `getGlobalProvisioningCap()`, `getTemplateProvisioningCap()`
- `ProvisioningNodeListener` - Automatic cleanup when nodes are deleted
- `NoDelayProvisionerStrategy` - Enhanced with limits checking before provisioning

This system prevents the over-provisioning issues seen in BEE-60267 by enforcing caps and maintaining accurate provisioning counts.

### Core Package Structure

**`hudson.model`** - Core domain objects
- `Jenkins` - Main singleton, root of object graph
- `Computer/Node` - Agent representation (Computer = runtime, Node = configuration)
- `Job/Run` - Build job definitions and executions
- `Queue` - Build queue management
- `User` - Authentication and user management

**`hudson.slaves`** - Agent/node management
- `Cloud` - Cloud provider abstraction
- `NodeProvisioner` - Agent provisioning coordination
- `ComputerLauncher` - Agent connection strategies
- `RetentionStrategy` - Agent lifecycle management

**`hudson.security`** - Security framework
- `SecurityRealm` - Authentication providers
- `AuthorizationStrategy` - Permission systems
- `Permission` - Fine-grained access control

**`hudson.tasks`** - Build step implementations
- `Builder` - Build steps that create artifacts
- `Publisher` - Post-build actions
- `BuildWrapper` - Environment setup/teardown

## Code Style and Import Ordering

### Java Import Order (lexicographical)
Follow this exact order when adding imports:
1. Static imports (`import static ...`)
2. Third-party imports (`edu.umd.cs.findbugs...`)
3. Hudson/Jenkins imports (`hudson...`)
4. Jakarta imports (`jakarta...`)
5. Java standard library (`java...`)
6. Jenkins model imports (`jenkins...`)
7. Org imports (`org...`)

**Example from Computer.java:**
```java
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher.ProcStarter;
import jakarta.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpRedirect;
```

### Development Requirements
- **Java**: JDK 17 or 21 (Eclipse Temurin/OpenJDK recommended)
- **Maven**: 3.9.6 or above
- **Node.js**: For frontend development (with Corepack enabled)

### Key Conventions
- Use `@Extension` for automatic discovery of plugin components
- Follow existing code patterns in similar classes
- Security-sensitive code requires special attention to ACL and Permission usage
- UI changes require corresponding Jelly templates in `src/main/resources`
- Extension points must be thread-safe due to concurrent plugin loading

### Plugin Development Context
When working on cloud provisioning plugins:
- Extend `AbstractCloudImpl` or implement `Cloud` directly
- Override `supportsProvisioningLimits()` to enable the new provisioning limits system
- Use `CloudProvisioningLimits.getInstance().register()` pattern for limit enforcement
- Follow naming conventions for template identification (e.g., "template-name-instance-id")