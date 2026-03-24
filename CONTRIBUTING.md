# Contributing to NBTShield

Thank you for your interest in contributing to NBTShield! This document provides guidelines and instructions for contributing.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Guidelines](#coding-guidelines)
- [Submitting Changes](#submitting-changes)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

---

## Code of Conduct

- Be respectful and constructive in all interactions
- No harassment, discrimination, or personal attacks
- Focus on the code, not the person
- Help others learn and grow

---

## How Can I Contribute?

### 🐛 Report Bugs

Found a bug? [Open an issue](../../issues/new) with:
- Server version (e.g., Paper 1.21.4 build #123)
- Java version
- NBTShield version
- Steps to reproduce
- Expected vs actual behavior
- Relevant server logs

### 💡 Suggest Features

Have an idea? [Open an issue](../../issues/new) with:
- Clear description of the feature
- Why it would be useful
- Example use case
- (Optional) Suggested implementation approach

### 🔧 Submit Code

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Test thoroughly on a Paper server
5. Submit a pull request

---

## Development Setup

### Prerequisites

| Tool | Version |
|---|---|
| **Java (JDK)** | 21+ |
| **Maven** | 3.6+ |
| **Paper Server** | 1.21.4+ (for testing) |

### Clone & Build

```bash
git clone https://github.com/Sh4doS3kr/NBTShield.git
cd NBTShield
mvn clean package
```

### Test

1. Copy `target/NBTShield-2.0.0.jar` to your test server's `plugins/` folder
2. Start the server
3. Verify the plugin loads: check for `NBTShield v2.0 enabled` in console
4. Test your changes

---

## Project Structure

```
NBTShield/
├── src/main/java/com/nbtshield/
│   ├── NBTShield.java              # Main plugin class
│   ├── listeners/
│   │   ├── ItemListener.java       # Inventory, items, signs, creative mode
│   │   ├── ChunkListener.java      # Chunk load scanning
│   │   ├── BookListener.java       # Book exploit protection
│   │   └── EntityListener.java     # Entity NBT protection
│   ├── network/
│   │   └── PacketProtection.java   # Netty pipeline handler
│   └── utils/
│       └── NBTChecker.java         # Core NBT size checking utility
├── src/main/resources/
│   ├── plugin.yml                  # Plugin metadata
│   └── config.yml                  # Configuration
├── pom.xml                         # Maven build config
└── build.gradle                    # Gradle build config (alternative)
```

### Key Classes

| Class | Responsibility |
|---|---|
| `NBTShield` | Plugin lifecycle, commands, strike tracking, admin notifications |
| `NBTChecker` | Serializes items and checks byte sizes against configurable limits |
| `ItemListener` | Handles all item-related events (join, click, drop, pickup, place, signs, creative) |
| `ChunkListener` | Scans chunks on load for oversized containers and entities |
| `BookListener` | Intercepts book edits and validates page count/length/total size |
| `EntityListener` | Protects item frames and armor stands; scans entities in chunks |
| `PacketProtection` | Netty handler that catches packet-level exploit exceptions |

---

## Coding Guidelines

### Style

- **Java 21** — Use modern features (pattern matching, switch expressions, records where appropriate)
- **4 spaces** for indentation (no tabs)
- **Braces** on the same line: `if (condition) {`
- **Descriptive names** — avoid single-letter variables except in loops
- **Keep methods focused** — one method, one responsibility

### Conventions

- All event handlers use `EventPriority.HIGHEST` and `ignoreCancelled = true` (where applicable)
- Permission checks use `nbtshield.bypass` for player exemptions
- Config values are read with sensible defaults: `getConfig().getBoolean("key", true)`
- Log messages use category prefixes: `[BookProtection]`, `[ChunkScan]`, `[EntityLoad]`, etc.
- Color codes use `&` format, converted via `NBTShield.colorize()`

### Adding a New Protection

1. Create a new listener class in `com.nbtshield.listeners`
2. Add config toggles and limits to `config.yml`
3. Register the listener in `NBTShield.onEnable()`
4. Add bypass permission check: `player.hasPermission("nbtshield.bypass")`
5. Use `plugin.recordStrike()` for violations
6. Use `plugin.notifyAdmins()` for admin alerts
7. Update `README.md` and `CHANGELOG.md`

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add map NBT protection
fix: prevent NPE when scanning empty armor stand
docs: update README with new configuration options
refactor: extract common scanning logic to NBTChecker
```

Prefixes: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

---

## Submitting Changes

### Pull Request Process

1. **Update documentation** — If you add features, update `README.md` and `CHANGELOG.md`
2. **Test thoroughly** — Verify on a running Paper 1.21.4+ server
3. **One feature per PR** — Keep pull requests focused
4. **Describe your changes** — Explain what, why, and how in the PR description
5. **Reference issues** — Link related issues with `Fixes #123` or `Closes #123`

### PR Template

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
How did you test these changes?

## Checklist
- [ ] Code compiles without errors
- [ ] Tested on Paper 1.21.4+
- [ ] Updated config.yml (if needed)
- [ ] Updated README.md (if needed)
- [ ] Updated CHANGELOG.md
```

---

## Reporting Bugs

### Bug Report Template

```markdown
**Server Version:** Paper 1.21.4 build #XXX
**Java Version:** 21.0.X
**NBTShield Version:** 2.0.0

**Description:**
What happened?

**Steps to Reproduce:**
1. Step one
2. Step two
3. ...

**Expected Behavior:**
What should have happened?

**Actual Behavior:**
What actually happened?

**Logs:**
Relevant console output (use a paste service for long logs)
```

---

<p align="center">
  <strong>Thank you for helping make Minecraft servers safer! 🛡️</strong>
</p>
