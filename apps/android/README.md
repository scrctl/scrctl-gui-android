# scrctl GUI for Android

VNC and Mac Screen Sharing client with an optional iPhone Wi-Fi mirror. This Gradle project uses the Rust core
pinned at `vendor/scrctl` and has app ID `org.scrctl.android`. The existing
iPhone mirroring project remains at the repository root. Both builds share an
app ID, so only one can be installed at a time. Verify signing and version code
before replacing an installed build.

From the repository root, with Rust, cargo-ndk and Android NDK installed:

```sh
git submodule update --init vendor/scrctl
./scripts/build-android-core.sh
cd apps/android
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## iPhone mirroring

The XDB build uses the repository's pinned embedded XDeviceBridge runtime and
generates Kotlin bindings from the feature-enabled Rust library. It targets
ARM64 Android devices and requires NDK 29.0.14206865, Python 3, CMake and Ninja.

```sh
git submodule update --init vendor/scrctl xdb
./scripts/build-android-core.sh --xdb
cd apps/android
./gradlew -Pscrctl.xdb=true :app:assembleDebug :app:testDebugUnitTest
```

Open **iPhone mirroring** from the library toolbar to discover or pair an iPhone
over Wi-Fi. If necessary, prepare the Developer Disk Image before opening the
mirror. Tap or drag inside the image to control the iPhone. Android Back closes
the session. The XDB debug build uses `org.scrctl.android.xdb.debug`, with its
own pairing records, and can coexist with the existing installation.

The GUI source snapshot was imported from the OpenScreens project. See
[LICENSE-AGPL](../../LICENSE-AGPL) for its license.
