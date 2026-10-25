# FloatingFrames

A small server-side plugin for Folia 26.2 and Java 25. No client mod required.

## Player controls

| Action | Result |
| --- | --- |
| Crouch + left-click a placed frame | Toggle visible / invisible without removing its item |
| Remove the support behind a plugin-hidden frame | Frame remains in place |
| Left-click without crouching | Normal item removal / frame breaking |
| Right-click | Normal item insertion / rotation |

Works with normal and glow item frames, empty or filled. To float a frame, place it against a temporary block, hide it, then remove that block. Making it visible again restores the support requirement: an unsupported frame can break on its next support check. Replace its support first if you want to keep it visible.

The setting is stored on the entity and is intended to survive chunk unloads and server restarts. Breaking and placing the frame creates a normal visible frame. Invisible frames created by other tools, fixed frames, and invulnerable frames are left alone.

## Install using Crafty

1. Stop the test server.
2. Upload `dist/FloatingFrames-0.1.0.jar` from this download into the server's `plugins` folder. For updates, remove the previous FloatingFrames JAR first.
3. Start the server. Check the console for successful loading and `/plugins` for FloatingFrames.
4. Join using a normal Minecraft client and try the checklist below.

There are no commands or configuration files. `floatingframes.toggle` is granted to everyone by default. LuckPerms can deny it for selected groups. Players without this permission retain normal frame interactions, including ordinary damage when they crouch-punch.

Stop/start the server for updates; do not use `/reload`. Before uninstalling, provide support for floating frames you want to retain: without this plugin, they can break when their support is checked.

## Test on your server

The automated tests check event-handler decisions with mocked Bukkit objects. They do **not** replace testing Minecraft, Folia, or Homestead together.

- In survival, test an empty normal frame and one holding a map: crouch-left-click hides each without popping the map; repeat to show it.
- Repeat with glow frames, and frames placed on walls, floors, and ceilings. Also check creative mode.
- Hide a frame, remove its support, and wait at least 10 seconds. It should remain. Insert/rotate an item, then stand and punch to remove the item; punch again to break the frame.
- Make an unsupported frame visible again. It should resume normal support checks and eventually drop. A supported frame should remain.
- Leave a hidden floating frame, unload its chunk and return, then stop/start the server and return. Confirm it still floats and can be toggled.
- As a **non-op** with no claim bypass, try empty and filled frames in someone else's Homestead claim. Denied hits must not toggle or damage them. Confirm permitted interactions in your own claim still work.
- Deny `floatingframes.toggle` to a test player and verify it cannot toggle frames.
- Check arrows, explosions, and block obstruction: this plugin does not cancel those removal causes. Other plugins and vanilla rules may affect their behavior.

## How it works

Item frames are entities. The plugin does not disable block updates or use a repeating task. It marks frames it hides using a persistent entity tag, and cancels `HangingBreakEvent` only when its cause is `PHYSICS` and the frame is both tagged and invisible. Other frames and paintings are unaffected.

Paper also uses PHYSICS for piston-induced hanging-entity removal, so managed hidden frames resist that removal too. Obstruction is a separate cause and is not cancelled; placing a block in the frame's occupied space can still break it.

Using `setFixed(true)` would interfere with normal item interaction, so this implementation leaves that property alone. Empty and filled frame hits take different server event paths, both handled here. Event callbacks mutate only their own frame synchronously, without cross-region operations, and the plugin declares Folia support.

Protection: cancelled interaction events are respected. Homestead is an optional load-order dependency so its HIGHEST protection listeners register before this plugin's HIGHEST listeners. This is based on the inspected Homestead source, not a live integration test. Verify the claim checklist with your installed version; arbitrary protection plugins registering later at the same priority are not guaranteed to run first.

## Build / develop

Open this folder (the one containing `build.gradle`) in VS Code. Install JDK 25.

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew clean build
```

The output is `build/libs/FloatingFrames-0.1.0.jar`. `dist` contains the prebuilt download copy; Gradle does not update that copy. Upload the new JAR from `build/libs` after rebuilding.

Change `version` in `gradle.properties` when releasing an update. The build injects it into `plugin.yml`. Commit source and Gradle wrapper files; do not commit `build`, `.gradle`, `bin`, or `dist`. Publish a built JAR as a GitHub release asset if distributing the plugin.

The project uses package `urza.floatingframes`, Paper API `26.2.build.129-stable`, Gradle 9.8.0, and Java 25. It is intended for your Folia 26.2 server; actual compatibility with your exact build must be confirmed on the test server. Runtime dependencies are provided by the server. Homestead is optional, not bundled.
