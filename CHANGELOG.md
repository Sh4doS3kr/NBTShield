# Changelog

All notable changes to NBTShield will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.1.1] - 2026-03-25

### Added

- **Whitelist Unicode Detection** — New detection system that only allows Latin characters, common symbols, and emoji in user input. Blocks Korean, CJK, PUA, Braille, zero-width, and all other non-Latin scripts
- **System Item Protection** — Items with `CustomModelData` (server/resource pack textures) are now exempt from ALL size checks and removal
- **New config**: `skip-custom-model-data-items: true` — Prevents deletion of server-created textured items
- **Debug toggle**: `/nbts debug` command to enable/disable verbose logging (disabled by default)
- **Paper AsyncChatEvent** — Separate `PaperChatListener` class for Paper-specific chat handling (fails gracefully on non-Paper servers)

### Changed

- **Unicode detection switched from blacklist to whitelist** — Only whitelisted characters are allowed in user input (chat, commands, signs, books, anvil renames)
- **All debug/verbose logs gated behind `/nbts debug`** — No more console spam from chat codepoints, packet inspection, or injection messages

### Fixed

- **Chests with textures no longer deleted** — `enforceChunkNbtLimit()` now skips containers holding system items
- **`isContainerOversized()` / `isShulkerBoxOversized()`** — Skip system items in size calculations
- **Serialization failure no longer auto-deletes items** — `getItemByteSize()` returning `-1` no longer flags items as oversized
- **PacketProtection** — Now extracts actual message field from packets instead of using `toString()`

### Performance

- **Fast-path for vanilla items** — Items without `ItemMeta` skip serialization entirely (~90% reduction in serialization calls)
- **Size cache** — `getItemByteSize()` caches results for 5 seconds to avoid repeated serialization
- **Interact cooldown** — `onPlayerInteract` rate-limited to 1 check every 2 seconds per player

### Removed

- Inventory scanning for Unicode characters (items in player inventory no longer deleted)
- Item frame scanning for Unicode characters
- Join/slot change/hand swap/inventory close Unicode scanning
- Old detection methods: `hasIllegalUnicode()`, `objectContainsPua()`, `componentContainsPua()`

---

## [2.1.0] - 2026-03-24

### Added

- **OP Exploit Protection** — New `CommandExploitListener` that detects and blocks command injection exploits:
  - Scans book pages for malicious JSON `ClickEvent` with `run_command` / `suggest_command`
  - Scans item display names and lore for embedded dangerous commands
  - Scans lectern books for malicious content
  - Detects 30+ dangerous commands: `/op`, `/deop`, `/stop`, `/ban`, `/lp`, `/pex`, `/execute`, `/give @`, etc.
  - Detects obfuscated command variants
  - Auto-kicks players attempting critical OP/permission exploits (configurable)
  - Sends high-priority `CRITICAL` alerts to admins for OP-related attempts
- **Command Block Protection** — Blocks unauthorized command block, chain command block, repeating command block, structure block, and jigsaw block placement
- **New permission** `nbtshield.commandblock` — Required to place command/structure blocks (default: OP)
- **New config options**: `command-exploit-protection`, `command-block-protection`, `kick-on-op-exploit`, `op-exploit-kick-message`
- Scans items on pickup for malicious command data
- Scans dispensers to prevent command block dispensing

---

## [2.0.0] - 2026-03-24

### Added

- **Book Ban Protection** — New `BookListener` that blocks oversized books, limits page count and page length
- **Sign Protection** — Prevents oversized sign text via `SignChangeEvent`
- **Entity Protection** — New `EntityListener` that protects item frames and armor stands from oversized NBT items
- **Packet-Level Protection** — Netty `ChannelDuplexHandler` injected per player to catch:
  - `PacketTooLargeException`
  - `VarInt too big` / `VarLong too big`
  - `Badly compressed packet`
  - `Length wider than 21-bit`
  - `Unable to fit X into 3`
  - `Payload may not be larger than 1048576/32767 bytes`
  - `Tried to read NBT tag that was too big`
  - `Packet over maximum protocol size`
  - `Connection timed out`
- **Container Protection** — Now scans chests, barrels, hoppers, droppers, dispensers (not just shulker boxes)
- **Creative Mode Protection** — Intercepts `InventoryCreativeEvent` to block arbitrary NBT injection
- **Ender Chest Scanning** — Player ender chests are now scanned and cleaned
- **Chunk Entity Scanning** — Item frames and armor stands in chunks are scanned on load
- **Configurable limits** for all new protections in `config.yml`
- New config options: `book-protection`, `sign-protection`, `entity-protection`, `packet-protection`, `scan-creative-actions`, `scan-entity-nbt-on-chunk-load`

### Changed

- **Renamed plugin** from `AntiChunkBan` to `NBTShield`
- **Package** renamed from `com.antichunkban` to `com.nbtshield`
- **Main class** renamed from `AntiChunkBan` to `NBTShield`
- **Commands** changed from `/acb` to `/nbs` (alias `/nbtshield`)
- **Permissions** changed from `antichunkban.*` to `nbtshield.*`
- `removeAllShulkers()` upgraded to `removeAllDangerousItems()` — now removes all oversized items, not just shulkers
- `ChunkListener` now scans ALL container types, not just shulker boxes
- `ItemListener.onBlockPlace()` now blocks any oversized item placement, not just shulkers
- Strike action now also cleans ender chest
- Updated default NBT limits to more secure values

### Improved

- Better container detection with `isContainer()` utility method
- Better book detection with `isBook()` utility method
- More detailed logging with categorized prefixes (`[ChunkScan]`, `[EntityLoad]`, `[BookProtection]`, etc.)
- Netty handler names use `nbtshield_protection` identifier

---

## [1.0.0] - 2026-03-24

### Added

- Initial release
- **Shulker Box NBT Protection** — Detects and removes shulker boxes with oversized NBT data
- **Item NBT Scanning** — Checks serialized byte size of all items
- **Chunk Load Scanning** — Scans tile entities when chunks load
- **Inventory Scanning** — Scans on join, inventory click, item pickup, item drop, item spawn
- **Block Place Protection** — Prevents placing oversized shulker boxes
- **Strike System** — Tracks repeat offenders and escalates (removes all shulkers)
- **Admin Notifications** — Real-time alerts to admins with `antichunkban.admin` permission
- **Commands** — `/acb reload`, `/acb scan`, `/acb scanplayer <name>`
- **Configurable** — All limits and toggles in `config.yml`
- **Paper 1.21.4 support**
