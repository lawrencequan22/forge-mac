# Forge iOS Port — Staff Engineer Handoff

**Mission:** Ship a working native iOS build of Forge (Magic: The Gathering) to a real iPad
(iPad-first) and iPhone, then to TestFlight for the owner + friends. You have **full authority** to
modify this fork, the build, the toolchain config, and to make pragmatic engineering calls.

**Repo / branch:** `github.com/lawrencequan22/forge-mac`, branch **`ios-port-wip`** (all iOS work
lives here; `master` holds the finished native macOS apps and must stay clean).

**Status at handoff:** The app **builds, signs, installs, and launches on a physical iPad**, and
runs through ~95% of startup. It currently dies during settings load on a **RoboVM↔Java-reflection
incompatibility** (`EnumMap` over an enum). Ten prior blockers are solved (see Log). The remaining
work is the reflection issue (below) and whatever follows it — likely more reflection friction,
because Forge uses reflection heavily (settings, XStream save files, card scripts).

---

## 1. Orientation: how the iOS port works

- Forge's mobile/tablet UI is the **libGDX** code in `forge-gui-mobile` (same code the desktop
  "Forge Adventure" app runs). The iOS app is the **`forge-gui-ios`** module: launcher
  `forge.ios.Main` (`forge-gui-ios/src/forge/ios/Main.java`), bundle id **`ca.lawrencequan.forge`**.
- **Toolchain:** **MobiVM 2.3.23** (the maintained RoboVM fork), driven by `robovm-maven-plugin`
  2.3.23, with `gdx-backend-robovm` 1.13.5. MobiVM **AOT-compiles all Java to native arm64**. Its
  runtime is **Java-8-level / Android-libcore-derived**, so anything newer (records, `java.nio.file`,
  `java.lang.management`, `ProcessHandle`) is **absent** and must be supplied.
- **The central challenge:** Forge is a desktop-grade Java-17 app; MobiVM is a constrained mobile
  runtime + an AOT compiler with limited reflection. Most of the work is bridging that gap **without
  editing upstream Forge source** (to keep `git merge upstream/master` clean). Two mechanisms do the
  bridging:
  1. **Boot-classpath shims** for missing `java.*` classes — compiled with
     `javac --patch-module java.base=...`, jarred into `forge-gui-ios/libs/*-stub.jar`, and listed in
     `forge-gui-ios/robovm.xml` `<bootclasspath>`. (Works for classes MobiVM *lacks*.)
  2. **Runtime-jar patches** for classes MobiVM *has* but that need a method added (RoboVM searches
     its `robovm-rt.jar` **before** the boot classpath — see §6 #9 — so you cannot override an
     existing class via boot classpath; you patch the rt jar's class bytecode directly). Done with an
     ASM tool, idempotently, by the build script.
  3. **De-record transform** (records aren't readable by MobiVM's Soot frontend): an ASM pass
     rewrites record/sealed classes to plain classes in the built jars before AOT.

---

## 2. Environment (already set up on this Mac)

- **Xcode 26.6**, iOS 26.5 SDK; `xcode-select -p` → `/Applications/Xcode.app/...`.
- **JDK 17 arm64** (Homebrew `openjdk@17`); Maven 3.9.x. (The build script pins JAVA_HOME to 17.)
- **libimobiledevice** (`idevicesyslog`, `idevicecrashreport`) — installed via Homebrew.
- **Apple signing (paid Developer Program):**
  - Team: **`4YRM325BL9`** ("LAWRENCE NATHAN QUAN", Individual).
  - Dev cert: **`Apple Development: LAWRENCE NATHAN QUAN (SS74Q2HK23)`** (valid — the WWDR
    intermediates were installed to fix trust; `security find-identity -v -p codesigning` shows it).
  - Wildcard dev provisioning profile **`97242004-c114-4338-8200-0fe751ffc655`** (App ID
    `4YRM325BL9.*`, covers both devices, expires 2027-06), in
    `~/Library/MobileDevice/Provisioning Profiles/`. Regenerate/extend via the stub-Xcode-project
    trick in §5 if it expires or a new device is added.
- **Devices (Developer Mode ON, must stay unlocked + plugged in to test):**
  - iPad Pro 12.9" (M1): UDID **`00008103-000A654E36D3001E`** (primary target).
  - iPhone 17 Pro Max: UDID **`00008150-00113D9202D9401C`**.

---

## 3. Build & deploy

```sh
# one-time per shell: nothing — the script pins JDK 17 itself
export FORGE_IOS_SIGN_IDENTITY="Apple Development: LAWRENCE NATHAN QUAN (SS74Q2HK23)"
export FORGE_IOS_PROFILE="97242004-c114-4338-8200-0fe751ffc655"

./scripts/build-ios.sh device     # AOT-compile + sign + install on the connected device (~15 min)
./scripts/build-ios.sh sim        # iPad Simulator — DOES NOT LINK (bundled gdx natives are
                                  # device-only); use device builds.
```

`scripts/build-ios.sh` (read it — it's the source of truth):
1. compiles the ASM tools (`RecordDesugar`, `FilePatcher`);
2. **patches `java.io.File.toPath()` into the SDK `robovm-rt.jar`** idempotently (see §6 #9);
3. runs the reactor with `-Drevision=2.0.14-SNAPSHOT -DskipTests` and profiles
   `-P ios-derecord,ios-device`, which (a) de-records the built jars at `prepare-package`, then
   (b) runs the MobiVM goal at `package`.

Build artifacts: `forge-gui-ios/target/robovm.tmp/forge.ios.Main.app` (+ `.dSYM`). The Maven plugin
auto-installs to the connected device; if it misses (device disconnected mid-build), install
manually: `xcrun devicectl device install app --device <UDID> <app>` then
`xcrun devicectl device process launch --device <UDID> ca.lawrencequan.forge`.

**Important:** `<revision>` is a CI-friendly `${revision}`; you MUST pass `-Drevision=2.0.14-SNAPSHOT`
and build in the reactor (`-am`) or resolution fails. The build script handles this.

---

## 4. The diagnostic loop (how every blocker was found — use this)

The app logs to **`forge.log`** inside its writable container. After a launch:

```sh
IPAD=00008103-000A654E36D3001E
xcrun devicectl device process launch --device $IPAD ca.lawrencequan.forge   # or --console
xcrun devicectl device copy from --device $IPAD \
  --domain-type appDataContainer --domain-identifier ca.lawrencequan.forge \
  --source Library/local/data/forge.log --destination ./forge.log
tail -40 forge.log         # Java stack of the failure (NoClassDefFoundError / UnsatisfiedLink / etc.)
```

For **native crashes / watchdog kills** (no Java stack), pull the crash report and **symbolicate**:

```sh
idevicecrashreport -u $IPAD -k ./crashes            # .ips files
# symbolicate forge.ios.Main offsets against the dSYM:
DSYM=forge-gui-ios/target/robovm.tmp/forge.ios.Main.app.dSYM/Contents/Resources/DWARF/forge.ios.Main
nm -arch arm64 -n "$DSYM" > /tmp/syms.txt          # symbols are like  _[J]forge.Foo.bar()V
# for a crash frame's imageOffset O and the report's forge image base B:
#   target = 0x100000000 + O   (dSYM preferred base is 0x100000000)
#   find the largest nm address <= target  -> that's the method
```
(A Python helper for this was used during the port — see git history / the session notes; reproduce
it: parse the `.ips` JSON `threads[*].frames[*].imageOffset`, add `0x100000000`, bisect into
`/tmp/syms.txt`.) This is how the `ExceptionHandler` watchdog deadlock was pinned to an exact method.

`forge.log` does **not** appear on the RoboVM `--console` stream (Forge redirects `System.out` to the
log file); always pull the file.

---

## 5. Provisioning (if the profile expires / a device is added)

RoboVM consumes an existing profile; it does not create one. Use Xcode's automatic signing via a
throwaway project (this is how the current profile + device registrations were made):

1. Generate a tiny Xcode app project with bundle id `ca.lawrencequan.forge`, team `4YRM325BL9`,
   `CODE_SIGN_STYLE=Automatic` (the `xcodeproj` Ruby gem bundled with fastlane works:
   `GEM_PATH=/opt/homebrew/Cellar/fastlane/<ver>/libexec ruby ...`).
2. `xcodebuild -project Stub.xcodeproj -scheme Stub -destination "id=<UDID>" \
     -allowProvisioningUpdates -allowProvisioningDeviceRegistration build`
   → registers the device + mints/updates the wildcard profile in
   `~/Library/Developer/Xcode/UserData/Provisioning Profiles/`.
3. **Copy** that `.mobileprovision` into `~/Library/MobileDevice/Provisioning Profiles/` (RoboVM only
   looks there) and pass its UUID as `FORGE_IOS_PROFILE`.

For **TestFlight** you'll additionally need an **Apple Distribution** cert + an **App Store**
provisioning profile + an App Store Connect app record, then `robovm:create-ipa` and upload via
**Transporter** or `xcrun altool`.

---

## 6. Solved blockers (chronological — symptom → cause → fix → files)

Each was found via §4 and committed individually on `ios-port-wip`. Read these to understand the
established patterns; you'll reuse them.

1. **Records unsupported.** MobiVM's Soot frontend can't read Java `record` bytecode
   (`Attempt to create RefType containing a /`). → **ASM de-record transform**
   (`forge-gui-ios/tools/RecordDesugar.java`) rewrites record/sealed classes to plain classes
   (regenerates `equals/hashCode/toString`, reparents to `Object`, strips `Record`/`PermittedSubclasses`)
   in the built module jars at `prepare-package` (profile `ios-derecord` in `forge-gui-ios/pom.xml`,
   wired via `exec-maven-plugin`). Proven: 15,586 classes AOT-compiled.
2. **Jetty bytecode** ("Exception reference used other than as the first statement…") in Forge's
   network-play server lib, which Forge never actually calls. → **excluded** `org.eclipse.jetty:*`
   from the `forge-gui` dependency in `forge-gui-ios/pom.xml`.
3. **Simulator can't link** — bundled gdx natives are device-only. → use **device builds**.
4. **Read-only assets / unwritable data.** Legacy `assetsDir = <storage>/../../X.app` is invalid since
   iOS 8 (bundle vs data are separate containers). → `forge/ios/Main.java`: assetsDir =
   `NSBundle.getMainBundle().getBundlePath()`; writable data/cache → the Documents container via
   `System.setProperty("forge.profile.userDir"/"cacheDir", …)`, honored by a small guard in
   `ForgeProfileProperties.getDefaultDirs()`. Also set `forge.assetsDir`. Added iPad + orientation
   detection (`UIDevice` idiom, `UIScreen` bounds).
5. **Letterboxed (tiny) UI** — no launch screen. → `Info.plist.xml`: `UILaunchScreen`,
   `UIRequiresFullScreen`, `arm64`, `MinimumOSVersion 13`.
6. **Launch watchdog kill (`0x8BADF00D`, scene-create 20 s).** `Forge.create()` runs on the main
   thread during launch and **hung** in `ExceptionHandler.registerErrorHandling()`'s cross-process
   `FileLock` log-slot logic (`FileChannel.tryLock` stalls on iOS's sandbox). Found by symbolicating
   the `.ips` (the win that unblocked everything). → `forge-gui/.../error/ExceptionHandler.java`: on
   iOS (`isLibgdxPort() && !isAndroid()`) use a plain log file, skip the FileLock slots. (Desktop +
   Android unchanged.)
7. **tinylog won't init** — `NoClassDefFoundError: java.lang.management.ManagementFactory` (MobiVM
   lacks it; `ProcessHandle` and `android.os.Process` too, so none of tinylog's 3 dialects work). →
   minimal **`ManagementFactory`/`RuntimeMXBean` stub** on the boot classpath
   (`forge-gui-ios/stubs/java/lang/management/`, `libs/management-stub.jar`).
8. **Native GL** — `UnsatisfiedLinkError: IOSGLES20.glTexImage2DJNI`. The hand-committed `libs/*.a`
   were stale 2014 binaries. → depend on the matching **libGDX 1.13.5 `natives-ios`** xcframeworks
   (`gdx-platform` [gdx+ObjectAL], `gdx-freetype-platform`, `gdx-box2d-platform`,
   classifier `natives-ios`) in `forge-gui-ios/pom.xml`; removed the old `<libs>` from `robovm.xml`.
   RoboVM auto-links the xcframeworks from `META-INF/robovm/ios/libs`.
9. **`java.nio.file` absent** — `NoClassDefFoundError: java/nio/file/Paths` (MobiVM has **zero**
   `java.nio.file.*`; Forge uses NIO.2 in ~18 files). → **File-backed shim**
   (`forge-gui-ios/stubs/java/nio/file/*`, `libs/nio-file-stub.jar`, on boot classpath) covering
   exactly the calls Forge makes (`Paths.get`, `Files.exists/size/newInputStream/newOutputStream/
   createDirectories/walk/walkFileTree/move/copy/newBufferedReader|Writer/createTempDirectory`,
   `Path`, `SimpleFileVisitor`, options enums). **Plus** `java.io.File.toPath()` — absent from
   MobiVM, and because **RoboVM prepends `robovm-rt.jar` ahead of the boot classpath** (Config.java
   `realBootclasspath.add(0, home.rtPath)`), a boot-classpath `File` can't win → the build script's
   `FilePatcher` (ASM) **appends `toPath()` to `File.class` inside the SDK `robovm-rt.jar`**
   idempotently. **Pattern to remember: missing class → boot-classpath shim; missing member on an
   existing rt class → patch the rt jar.**
10. **Tree-shaking stripped reflection metadata** (first hypothesis for the enum bug). → set
    `<treeShaker>none</treeShaker>` in `robovm.xml` (Jetty is already excluded via the pom, so
    shaking is no longer needed). This did NOT fix the enum bug (see §7) but is correct: Forge's
    reflection means everything must be kept.

---

## 7. CURRENT BLOCKER — `EnumMap` / enum reflection

**Symptom** (`forge.log`):
```
java.lang.AssertionError
  at java.lang.Enum$1.create(Enum.java:52)
  at libcore.util.BasicLruCache.get(BasicLruCache.java:54)
  at java.lang.Enum.getSharedConstants(Enum.java:216)
  at java.util.EnumMap.<init>(EnumMap.java:404)
  at forge.localinstance.properties.PreferencesStore.<init>(PreferencesStore.java:43)   // new EnumMap<>(FPref.class)
  at forge.Forge.create(Forge.java:204)
```

**Root cause (decompiled from MobiVM's rt):** `EnumMap.<init>` → `Enum.getSharedConstants(Class)` →
`Enum$1.create(Class)` does `enumType.getDeclaredMethod("values").setAccessible(true).invoke(null)`
and throws `AssertionError` from the catch when that reflection fails. So **reflectively invoking an
enum's compiler-generated `values()` fails under RoboVM AOT.** `Class.getEnumConstants()` goes
through the same path (no native shortcut in MobiVM's `Class`). Confirmed it is NOT tree-shaking
(still fails with shaking off).

**Why it's important:** `EnumMap` is used widely in Forge, and this is the canonical "RoboVM can't
reflect" signal. Expect the same class of failure later in **XStream** (save/load of quests,
gauntlets, tournaments, decks — heavy reflection) and possibly **card-script/ability loading**.

**Hypotheses & next steps (in priority order):**
1. **EnumMap normally works on RoboVM** (every libGDX game uses enums) — so determine what's different
   here. Bisect: build a trivial test that does `new EnumMap<>(SomeEnum.class)` early in
   `forge.ios.Main` and see if it also fails. If a *fresh* enum fails too → it's a global
   reflection-config problem, not FPref-specific.
2. **Find the precise failing call.** Patch `Enum$1.create` (rt-patch, like `File`) to log/rethrow the
   caught exception with a message, OR add `-ea`-style instrumentation, to learn whether
   `getDeclaredMethod("values")` throws `NoSuchMethodException` (method not retained for reflection)
   vs `invoke()` throws (can't reflectively call). The fix differs:
   - If `getDeclaredMethod` fails → RoboVM isn't keeping enum `values()` reflection metadata. Look at
     RoboVM `<forceLinkMethods>` (Config supports it), reflection-retention config, and whether the
     release pipeline drops reflection info. Possibly force-retain `*.values()` for enums.
   - If `invoke()` fails → reflective invoke isn't wired; investigate RoboVM's reflection/marshaling
     config and known MobiVM issues.
3. **Patch the rt to bypass reflection for enums.** If RoboVM exposes any AOT-native way to read enum
   constants (investigate `java.lang.Class`/VM intrinsics in robovm-rt and `org.robovm.rt.*`),
   rewrite `Enum.getSharedConstants` to use it instead of `getDeclaredMethod("values")`. This is the
   `File` pattern applied to `Enum`. Reproducible via `build-ios.sh` (extend the rt-patch step).
4. **Check MobiVM issues/releases** (github.com/MobiVM/robovm) for enum/EnumMap/reflection;
   2.3.24/2.3.25 changelogs were skimmed (no obvious mention) but search the issue tracker.

Whatever the fix, fold it into `scripts/build-ios.sh` (if it's an rt-patch) or `robovm.xml` so it is
**reproducible**, and keep Forge source unchanged if at all possible.

---

## 8. Remaining plan after the reflection wall

1. **Clear reflection issues** until `Forge.create()` returns and the **main menu renders** (verify
   on-device; take a screenshot via the device or have the owner look). Expect: EnumMap → XStream →
   possibly card/ability loading. Treat XStream as a likely large item — if it's intractable, consider
   whether the failing save/load paths are reachable at startup (they may be deferrable).
2. **Functional pass on iPad:** start a Constructed game vs AI, open the deck editor, confirm fonts
   (gdx-freetype) and art load, settings persist across relaunch (writable Documents path).
3. **Card-load speed:** bundle `res/cardsfolder/cardsfolder.zip` (one file vs 33k loose `.txt`) into
   the app — proven manually during the port; fold into `build-ios.sh` (zip `forge-gui/res/cardsfolder`,
   exclude the loose dir, bundle the zip). Not on the startup critical path, but do it.
4. **iPhone:** universal already (`UIDeviceFamily` 1,2) and previously installed/launched; re-verify
   small-screen UI once it runs.
5. **TestFlight:** §5 (distribution cert/profile + App Store Connect + `create-ipa` + upload). Add
   the owner + friends as testers. Document the exact commands.

---

## 9. Critical files

- `scripts/build-ios.sh` — the entire build/patch/deploy pipeline. **Start here.**
- `forge-gui-ios/pom.xml` — deps (Jetty exclusion, 1.13.5 `natives-ios`, stub), and the
  `ios-derecord` / `ios-sim` / `ios-device` profiles (MobiVM plugin bindings).
- `forge-gui-ios/robovm.xml` — `<bootclasspath>` (the stubs), `<frameworks>`, `<forceLinkClasses>`,
  `<treeShaker>none</treeShaker>`, `<resources>` (bundles `../forge-gui/res`).
- `forge-gui-ios/robovm.properties` — bundle id / version / main class.
- `forge-gui-ios/Info.plist.xml` — arm64, min OS, launch screen, device family, orientations.
- `forge-gui-ios/src/forge/ios/Main.java` — launcher: assetsDir, writable paths, iPad/orientation.
- `forge-gui-ios/stubs/` — boot-classpath shims (`java/lang/management/*`, `java/nio/file/*`),
  compiled with `--patch-module java.base` into `forge-gui-ios/libs/*-stub.jar`.
- `forge-gui-ios/tools/RecordDesugar.java` (de-record), `forge-gui-ios/tools/FilePatcher.java`
  (ASM rt-patch template — reuse for any rt-class patch, e.g. `Enum`).
- Forge source touched (kept minimal, all guarded/opt-in): `ExceptionHandler.java` (iOS log),
  `ForgeProfileProperties.java` (writable dirs). Startup path: `forge.Forge.create()`
  (`forge-gui-mobile/src/forge/Forge.java`), `PreferencesStore`/`ForgePreferences`/`FModel`.
- Reference for what a mobile launcher must do: `forge-gui-android/src/forge/app/Main.java`.

---

## 10. Operating principles for the agent

- **Prefer source-free fixes** (boot-classpath shims, rt-jar patches, build config) over editing
  upstream Forge source, so `git merge upstream/master` stays clean. When you must touch Forge source,
  guard it for iOS only (`isLibgdxPort() && !isAndroid()`) and keep it tiny.
- **Make every fix reproducible** in `scripts/build-ios.sh` / `robovm.xml` / `pom.xml`. Anything you
  do by hand to `.m2`/the SDK/the keychain must be re-applied by the script (the `File`/rt-patch is
  the model).
- **One blocker at a time, commit each** with a message explaining symptom→cause→fix (match the
  existing history on `ios-port-wip`). Push so progress is durable.
- **Use the §4 loop relentlessly** — `forge.log` + symbolicated `.ips` tell you the exact failure.
- **Build cycles are ~15–20 min.** Batch investigation (decompile rt, read source, form a hypothesis)
  before spending a build. Don't guess-and-rebuild.
- **Keep the owner's devices usable:** they must be unlocked + plugged in to install/launch; don't
  assume they stay connected across a long build.
- **Definition of done:** Forge reaches the main menu on the iPad, plays a game vs AI, persists
  settings; then iPhone; then a TestFlight build the owner + a friend can install.
- **Honest signposting:** if reflection (XStream/card scripts) proves to be a hard wall, say so
  clearly with evidence rather than grinding indefinitely — the macOS apps on `master` are the
  already-shipped fallback.
