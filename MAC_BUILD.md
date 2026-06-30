# Native Apple Silicon build (forge-mac)

This fork adds self-contained, native **Apple Silicon** builds of Forge — both the classic
**desktop** app and **Forge Adventure** (the libGDX RPG). Each bundles its own arm64 Java
runtime, so you **don't need Java installed** to run them.

The two editions:

| Edition | Module | App produced | UI |
|---|---|---|---|
| `desktop`   | `forge-gui-desktop`    | `Forge.app`           | classic Swing (Constructed, Draft, Sealed, Quest…) |
| `adventure` | `forge-gui-mobile-dev` | `Forge Adventure.app` | libGDX / LWJGL3 RPG mode |

(Adventure lives in the `*-mobile-dev` module because it's built on the libGDX engine shared
with the Android/iOS ports — but it is a normal desktop app, exactly what the official
`forge-adventure.sh` launches. Its jar ships arm64 macOS LWJGL natives, so it runs natively.)

Upstream changes are three tiny, opt-in patches that honor a `forge.assetsDir` system property
(default behavior unchanged when the property is absent): `GuiDesktop.getAssetsDir()` (desktop)
and `GameLauncher` + `Config.resPath()` (adventure). Everything else lives in `scripts/` + this
file, so syncing with upstream stays essentially conflict-free.

## Prerequisites (one-time)

- **Apple Silicon Mac** (M-series).
- **JDK 17 (arm64)** — to build. Already covered by Homebrew: `brew install openjdk@17`.
  (The *built app* needs no Java; this is only for building.)
- **Maven** — to build the jar: `brew install maven`.
- **Xcode Command Line Tools** (for `codesign`): `xcode-select --install`.

## Build

```sh
./scripts/build-mac-app.sh             # desktop only (default)
./scripts/build-mac-app.sh adventure   # Forge Adventure only
./scripts/build-mac-app.sh both        # both apps
```

Outputs in `build/mac/out/` (per edition):

- **`<Name>-<version>.dmg`** — open it, drag the app onto the **Applications** shortcut.
- **`<Name>.app`** — the app itself; you can also drag it straight into `/Applications`.

Both apps coexist in `/Applications` and share the same game data, so cards/settings carry over.

First launch of an unsigned app: the script already ad-hoc signs it and clears the
quarantine flag, so it should open directly. If macOS still blocks it, right-click the app
→ **Open** once, or run `xattr -dr com.apple.quarantine "/Applications/Forge.app"`.

Your saves and settings live in `~/Library/Application Support/Forge` (and caches in
`~/Library/Caches/Forge`), so this app shares data with any existing Forge install.

## Update to a new upstream version

```sh
./scripts/update-from-upstream.sh
```

This fetches `Card-Forge/forge`, merges `upstream/master`, and rebuilds **both** apps. To pin a
specific upstream tag, or to rebuild only one edition:

```sh
./scripts/update-from-upstream.sh forge-2.0.15            # pin a tag, rebuild both
./scripts/update-from-upstream.sh upstream/master desktop # latest, desktop only
```

Then re-install the freshly built app/DMG from `build/mac/out/`.

## How it works (notes for future me)

- Builds the standard `<module>-<ver>-jar-with-dependencies.jar` for the requested edition(s)
  in one Maven pass (`mvn -pl forge-gui-desktop,forge-gui-mobile-dev -am clean package`).
  Main classes: `forge.view.Main` (desktop), `forge.app.Main` (adventure).
- `jlink --add-modules ALL-MODULE-PATH` produces one complete arm64 runtime, shared by both
  apps (Forge reaches many JDK modules via reflection/SQL/scripting, so we keep them all).
- `jpackage` wraps each jar + `forge-gui/res` + runtime into a `.app`, then into a DMG.
- Packaged Forge normally resolves `res/` relative to the process working directory (that's why
  `forge.sh`/`forge-adventure.sh` do `cd`). A native `.app` launches with `cwd=/`, and
  `-Duser.dir` does NOT change the real cwd that `File.exists()` / `Gdx.files.absolute()` use —
  so instead we pass `-Dforge.assetsDir=$APPDIR` (jpackage substitutes the bundle's absolute
  `Contents/app` path at runtime). The opt-in patches turn that into an absolute assets root, so
  every resource path is absolute and cwd no longer matters. The `--add-opens` list is read
  straight from `forge-gui-desktop/pom.xml` so it tracks upstream automatically.
- Desktop: we omit `-Dapple.laf.useScreenMenuBar=true` (it NPEs in Apple's Aqua menu painter on
  macOS 26; Forge renders its menu in-window instead). Adventure (libGDX) uses `glfw_async`, set
  by `GameLauncher`, so it needs no `-XstartOnFirstThread` flag.

Build artifacts (`build/`) are git-ignored.
