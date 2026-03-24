<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-brightgreen?style=for-the-badge&logo=mojangstudios&logoColor=white" alt="Minecraft 1.21.x"/>
  <img src="https://img.shields.io/badge/Paper-API-blue?style=for-the-badge&logo=paperlessngx&logoColor=white" alt="Paper API"/>
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/github/license/Sh4doS3kr/NBTShield?style=for-the-badge" alt="License"/>
  <img src="https://img.shields.io/github/v/release/Sh4doS3kr/NBTShield?style=for-the-badge&color=blueviolet" alt="Release"/>
</p>

<h1 align="center">🛡️ NBTShield</h1>

<p align="center">
  <strong>The ultimate protection against NBT exploits, chunk bans, book bans, and packet-level attacks for Minecraft servers.</strong>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-how-it-works">How It Works</a> •
  <a href="#-installation">Installation</a> •
  <a href="#%EF%B8%8F-configuration">Configuration</a> •
  <a href="#-commands--permissions">Commands</a> •
  <a href="#-building-from-source">Building</a> •
  <a href="#-contributing">Contributing</a> •
  <a href="#-license">License</a>
</p>

---

## 🚨 The Problem

Malicious players can crash your server or permanently ban players from chunks by exploiting oversized NBT data in items, books, signs, entities, and packets. These exploits include:

| Exploit | Effect |
|---|---|
| Oversized shulker boxes | Chunk becomes unloadable — **chunk ban** |
| Book with massive pages | Server crash on read — **book ban** |
| Malformed VarInt/VarLong | Protocol-level crash |
| Badly compressed packets | Memory allocation crash |
| Oversized custom payloads | `PacketTooLargeException` crash |
| Entity NBT overflow | Item frames/armor stands cause chunk bans |
| Sign text overflow | Payload size exploit |
| Creative mode NBT injection | Arbitrary oversized items |
| Book/item ClickEvent injection | Admin runs `/op` unknowingly — **OP exploit** |
| Command block placement | Unauthorized command execution |

**NBTShield stops ALL of them.**

---

## ✨ Features

### 🔒 Multi-Layer Protection

```
┌─────────────────────────────────────────────────────┐
│                    NBTShield v2.0                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Layer 1: PREVENTIVE                                │
│  ├─ Scans items on join, click, drop, pickup        │
│  ├─ Scans chunks on load (containers + entities)    │
│  ├─ Blocks oversized books, signs, creative items   │
│  └─ Removes illegal items before they cause damage  │
│                                                     │
│  Layer 2: NETWORK                                   │
│  ├─ Netty pipeline injection per player             │
│  ├─ Catches PacketTooLarge, VarInt, VarLong errors  │
│  ├─ Handles badly compressed / oversized packets    │
│  └─ Graceful disconnect instead of server crash     │
│                                                     │
│  Layer 3: COMMAND EXPLOIT                            │
│  ├─ Scans books/items for ClickEvent JSON injection │
│  ├─ Detects /op, /deop, /lp, /pex and 30+ commands │
│  ├─ Blocks command block & structure block placement│
│  └─ Auto-kick on critical exploit attempts          │
│                                                     │
│  Layer 4: RESPONSE                                  │
│  ├─ Strike system tracks repeat offenders           │
│  ├─ Auto-removes ALL dangerous items on threshold   │
│  ├─ Admin notifications in real-time                │
│  └─ Full logging of every action                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 📋 Full Feature List

- **NBT Size Scanning** — Checks every item's serialized byte size against configurable limits
- **Shulker Box Protection** — Detects and removes oversized shulker boxes (items + placed blocks)
- **Container Protection** — Covers chests, barrels, hoppers, droppers, dispensers
- **Book Ban Protection** — Limits page count, page length, and total book size
- **Sign Protection** — Prevents oversized sign text that causes payload exploits
- **Entity Protection** — Scans item frames and armor stands for oversized items
- **Creative Mode Protection** — Blocks arbitrary NBT injection via creative inventory
- **Packet Protection** — Netty handler catches 15+ different packet-level exceptions
- **Chunk Load Scanning** — Automatically cleans chunks as they load
- **Ender Chest Scanning** — Also checks player ender chests
- **Strike System** — Escalating response for repeat offenders
- **Admin Notifications** — Real-time alerts to online admins
- **Fully Configurable** — Every limit and toggle is in `config.yml`
- **OP Exploit Protection** — Detects ClickEvent JSON injection in books, item names, lore, and lecterns
- **Command Block Protection** — Prevents unauthorized command block and structure block placement
- **Auto-Kick** — Immediately kicks players attempting critical exploits (OP/permission escalation)
- **30+ Dangerous Commands** — Detects `/op`, `/deop`, `/lp`, `/pex`, `/execute`, `run_command`, and more
- **Lightweight** — Zero external dependencies, minimal performance impact
- **Paper 1.21.x** — Built for modern Paper servers (1.21.4+)

---

## 🔍 How It Works

### 1. Item Scanning

Every time a player **joins**, **clicks an inventory slot**, **picks up an item**, **drops an item**, or **places a block**, NBTShield serializes the item using Bukkit's `BukkitObjectOutputStream` and checks the byte size:

```
Item → Serialize → Check byte size → Remove if oversized
```

Different item types have different limits:
- **Regular items**: `max-item-nbt-bytes` (default: 256 KB)
- **Shulker boxes**: `max-shulker-nbt-bytes` (default: 500 KB)
- **Containers**: `max-container-nbt-bytes` (default: 500 KB)
- **Books**: `max-book-bytes` (default: 32 KB) + page count/length checks

### 2. Chunk Load Protection

When a chunk loads, NBTShield scans:
1. **All container tile entities** (shulker boxes, chests, barrels, etc.) — removes the block if contents are oversized
2. **All item entities** on the ground — removes oversized dropped items
3. **All item frames and armor stands** — clears oversized items from their slots

This prevents chunk bans from already-placed exploit items.

### 3. Book Ban Protection

The `BookListener` intercepts `PlayerEditBookEvent` and checks:
- **Page count** vs `max-book-pages` (default: 100)
- **Individual page length** vs `max-book-page-length` (default: 320 chars)
- **Total serialized size** vs `max-book-bytes` (default: 32 KB)

Existing books in inventories are also scanned during regular item checks.

### 4. Packet-Level Protection

NBTShield injects a custom Netty `ChannelDuplexHandler` into each player's network pipeline on join. This handler intercepts exceptions **before they crash the server**:

```
Player ↔ [NBTShield Handler] ↔ [Minecraft Packet Handler] ↔ Server
```

Caught exceptions include:
- `PacketTooLargeException` (8388608 bytes)
- `VarInt too big` / `VarLong too big`
- `Badly compressed packet` (2097152 bytes)
- `Length wider than 21-bit`
- `Unable to fit X into 3`
- `Tried to read NBT tag that was too big`
- `Payload may not be larger than 1048576/32767 bytes`
- `Packet over maximum protocol size`
- And more...

When caught, the connection is **closed gracefully** (no crash), the player's inventory is scanned, and admins are notified.

### 5. Command Exploit Protection

The `CommandExploitListener` scans all books, item names, lore, and lecterns for **malicious JSON ClickEvent data** that could trick admins into running dangerous commands:

```
Malicious book page:
{"text":"Click here!","clickEvent":{"action":"run_command","value":"/op HackerName"}}

→ NBTShield detects "run_command" + "/op" → Removes item → Kicks player → Alerts admins
```

**Detection covers:**
- `run_command` / `suggest_command` JSON actions
- 30+ dangerous commands: `/op`, `/deop`, `/stop`, `/ban`, `/lp`, `/pex`, `/execute`, `/give @`, etc.
- Obfuscated command variants
- Books, item display names, lore lines, lectern books
- Command block and structure block placement (requires `nbtshield.commandblock`)

**Response to critical exploits (OP/permission related):**
1. Immediately removes all malicious items from inventory + ender chest
2. Kicks the player (configurable)
3. Sends high-priority alert to all admins
4. Logs full details to console

### 6. Strike System

If a player triggers NBTShield protections **3 times within 60 seconds** (configurable), the plugin escalates:
1. Removes **ALL shulker boxes** from their inventory
2. Removes **ALL oversized items** from their inventory and ender chest
3. Notifies admins of the escalation

---

## 📥 Installation

1. Download `NBTShield-2.0.0.jar` from [Releases](../../releases)
2. Place it in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/NBTShield/config.yml` to customize limits
5. Use `/nbs reload` to apply changes

### Requirements

| Requirement | Version |
|---|---|
| **Server** | Paper 1.21.4+ |
| **Java** | 21+ |

> **Note:** This plugin is designed for **Paper** servers. It uses Paper-specific APIs like `EntitiesLoadEvent`. It will not work on Spigot.

---

## ⚙️ Configuration

The full `config.yml` with explanations:

```yaml
# ---- General NBT Size Limits ----
max-item-nbt-bytes: 262144        # 256KB per item
max-shulker-nbt-bytes: 500000     # 500KB per shulker
max-container-nbt-bytes: 500000   # 500KB per container

# ---- Book Protection ----
book-protection: true
max-book-pages: 100               # Max pages per book
max-book-page-length: 320         # Max chars per page
max-book-bytes: 32000             # Max total book size

# ---- Sign Protection ----
sign-protection: true
max-sign-line-length: 384         # Max chars per sign line

# ---- Entity Protection ----
entity-protection: true
scan-entity-nbt-on-chunk-load: true

# ---- Packet Protection ----
packet-protection: true           # Netty pipeline injection

# ---- Scanning Triggers ----
scan-chunks-on-load: true
scan-on-join: true
scan-on-inventory-click: true
scan-dropped-items: true
scan-creative-actions: true

# ---- Command / OP Exploit Protection ----
command-exploit-protection: true  # Scan books/items/lore for command injection
command-block-protection: true    # Block unauthorized command block placement
kick-on-op-exploit: true          # Auto-kick on OP/permission exploits
op-exploit-kick-message: "&c&l[NBTShield] &eKicked for command exploit attempt."

# ---- Logging ----
log-removals: true
notify-admins: true

# ---- Messages (supports & color codes) ----
player-message: "&c&l[NBTShield] &eAn illegal oversized item was removed."
admin-message: "&c&l[NBTShield] &eRemoved oversized item from &6{player} &e(Size: &c{size}&e bytes)"

# ---- Strike System ----
strike-threshold: 3
strike-window-seconds: 60
strike-action-message: "&c&l[NBTShield] &eAll illegal items removed due to repeated violations."
```

---

## 💻 Commands & Permissions

### Commands

| Command | Description |
|---|---|
| `/nbs` | Show help |
| `/nbs reload` | Reload configuration |
| `/nbs scan` | Scan all online players' inventories |
| `/nbs scanplayer <name>` | Scan a specific player |

> Alias: `/nbtshield`

### Permissions

| Permission | Description | Default |
|---|---|---|
| `nbtshield.admin` | Access to all `/nbs` commands + receive notifications | OP |
| `nbtshield.bypass` | Bypass all NBTShield checks (**not recommended**) | false |
| `nbtshield.commandblock` | Allows placing command blocks and structure blocks | OP |

---

## 🏗️ Building from Source

### Prerequisites

- **Java 21+**
- **Maven 3.6+**

### Build

```bash
git clone https://github.com/Sh4doS3kr/NBTShield.git
cd NBTShield
mvn clean package
```

The compiled JAR will be at `target/NBTShield-2.0.0.jar`.

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

---

## 📄 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for a detailed history of changes.

---

## 🎮 Try It Live!

NBTShield is actively running on our test server. Join and try to break it!

```
play.mlmc.lat
```

<p align="center">
  <img src="https://img.shields.io/badge/Server-play.mlmc.lat-00AA00?style=for-the-badge&logo=minecraft&logoColor=white" alt="Server"/>
</p>

> Connect with Minecraft **1.21.4+** and see NBTShield in action. All protections are enabled with default configuration.

---

<p align="center">
  <strong>Made with ❤️ for the Minecraft community</strong>
</p>

<p align="center">
  <sub>If NBTShield helped protect your server, consider giving it a ⭐ on GitHub!</sub>
</p>
