package forge.ios;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.lang3.tuple.Pair;
import org.jupnp.UpnpServiceConfiguration;
import org.robovm.apple.coregraphics.CGRect;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.foundation.NSBundle;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIDevice;
import org.robovm.apple.uikit.UIPasteboard;
import org.robovm.apple.uikit.UIScreen;
import org.robovm.apple.uikit.UIUserInterfaceIdiom;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import com.badlogic.gdx.backends.iosrobovm.IOSFiles;

import forge.Forge;
import forge.interfaces.IDeviceAdapter;

public class Main extends IOSApplication.Delegate {

    // forge-mac: RoboVM only generates an enum's reflective values() stub when it can statically
    // see the concrete enum class at a reflective/EnumMap site. Forge builds EnumMaps from a generic
    // Class<T> (e.g. PreferencesStore<T> -> new EnumMap<>(clasz)), which is opaque to that analysis,
    // so getEnumConstants()/EnumMap fail at runtime with a bare AssertionError. Touching each enum's
    // values() reflectively HERE, with a class literal, forces RoboVM to keep the stub. Extend this
    // list whenever a new enum is reached via a generic-Class path.
    // forge-mac: MobiVM can't reflect values() on very large enums, which breaks EnumMap /
    // getEnumConstants for them. Register their constants here with DIRECT (non-reflective) values()
    // calls; the build rt-patches java.lang.Enum.getSharedConstants to return these. Add any enum that
    // crashes startup with a bare AssertionError under EnumMap/getSharedConstants (find big enums via
    // constant count). Currently: FPref (285) and TrackableProperty (216).
    private static void registerLargeEnums(String docs) {
        // forge.rt.EnumRegistry is on the boot classpath (so the rt-patched java.lang.Enum can see it)
        // and on the compile classpath (system-scope dep in pom). Register with a DIRECT call — a
        // reflective register/values() is itself unreliable under RoboVM AOT. Log each step.
        try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.File(docs, "enumfix.log"))) {
            Class<forge.localinstance.properties.ForgePreferences.FPref> fp =
                    forge.localinstance.properties.ForgePreferences.FPref.class;
            Class<forge.trackable.TrackableProperty> tp = forge.trackable.TrackableProperty.class;
            try {
                Object[] v = forge.localinstance.properties.ForgePreferences.FPref.values();  // DIRECT values()
                w.println("FPref.values() direct: OK len=" + v.length);
                forge.rt.EnumRegistry.register(fp, v);                                         // DIRECT register
                Object[] got = forge.rt.EnumRegistry.get(fp);
                w.println("EnumRegistry.get(FPref) after register: " + (got == null ? "NULL" : "len=" + got.length));
            } catch (Throwable t) { w.println("FPref register step FAILED: " + t); }
            try {
                Object[] v = forge.trackable.TrackableProperty.values();
                w.println("TrackableProperty.values() direct: OK len=" + v.length);
                forge.rt.EnumRegistry.register(tp, v);
            } catch (Throwable t) { w.println("TrackableProperty register step FAILED: " + t); }

            // The actual ops that used to crash:
            try { new java.util.EnumMap(fp); w.println("FPref EnumMap: OK"); }
            catch (Throwable t) { w.println("FPref EnumMap: FAILED " + t); }
            try { Object[] ec = tp.getEnumConstants();
                  w.println("TrackableProperty getEnumConstants: " + (ec == null ? "null" : "OK " + ec.length)); }
            catch (Throwable t) { w.println("TrackableProperty getEnumConstants: FAILED " + t); }
        } catch (Throwable ignored) {}
    }

    @Override
    protected IOSApplication createApplication() {
        // forge-mac: read-only game assets (res/) are bundled INSIDE the .app, so point assetsDir
        // at the real bundle path. The legacy "<storage>/../../forge.ios.Main.app/" is invalid on
        // iOS 8+ (app bundle and data live in separate containers). Writable game data/cache go to
        // the app's Documents container instead (see ForgeProfileProperties, which reads these props).
        final String assetsDir = NSBundle.getMainBundle().getBundlePath() + "/";
        final String docs = new IOSFiles().getLocalStoragePath() + "/";
        System.setProperty("forge.assetsDir", assetsDir);              // Adventure (Config.resPath)
        System.setProperty("forge.profile.userDir", docs + "data/");   // saves, decks, prefs (writable)
        System.setProperty("forge.profile.cacheDir", docs + "cache/"); // card images, music (writable)

        registerLargeEnums(docs); // forge-mac: non-reflective constants for enums MobiVM can't reflect (large)

        final boolean isTablet = UIDevice.getCurrentDevice().getUserInterfaceIdiom() == UIUserInterfaceIdiom.Pad;
        final CGRect bounds = UIScreen.getMainScreen().getBounds();
        final boolean isPortrait = bounds.getSize().getHeight() >= bounds.getSize().getWidth();

        final IOSApplicationConfiguration config = new IOSApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        final ApplicationListener app = Forge.getApp(null, new IOSClipboard(), new IOSAdapter(), assetsDir, isPortrait, isTablet, 0);
        final IOSApplication iosApp = new IOSApplication(app, config);
        return iosApp;
    }

    public static void main(String[] args) {
        final NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(args, null, Main.class);
        pool.close();
    }

    //special clipboard that works on iOS
    private static final class IOSClipboard implements com.badlogic.gdx.utils.Clipboard {
        @Override
        public boolean hasContents() {
            return UIPasteboard.getGeneralPasteboard().toString().length() > 0;
        }

        @Override
        public String getContents() {
            return UIPasteboard.getGeneralPasteboard().getString();
        }

        @Override
        public void setContents(final String contents0) {
            UIPasteboard.getGeneralPasteboard().setString(contents0);
        }
    }

    private static final class IOSAdapter implements IDeviceAdapter {
        @Override
        public boolean isConnectedToInternet() {
            return true;
        }

        @Override
        public boolean isConnectedToWifi() {
            return true;
        }

        @Override
        public String getDownloadsDir() {
            return new IOSFiles().getExternalStoragePath();
        }

        @Override
        public String getVersionString() {
            return "0.0";
        }

        @Override
        public String getLatestChanges(String commitsAtom, Date buildDateOriginal, Date maxDate) {
            return "";
        }

        @Override
        public String getReleaseTag(String releaseAtom) {
            return "";
        }

        @Override
        public boolean openFile(final String filename) {
            return new IOSFiles().local(filename).exists();
        }

        @Override
        public void setLandscapeMode(final boolean landscapeMode) {
            // TODO implement this
        }

        @Override
        public void preventSystemSleep(boolean preventSleep) {
            // TODO implement this
        }

        @Override
        public boolean isTablet() {
            return Gdx.graphics.getWidth() > Gdx.graphics.getHeight();
        }

        @Override
        public void restart() {
            // Not possible on iOS
        }

        @Override
        public void exit() {
            // Not possible on iOS
        }

        @Override
        public void closeSplashScreen() {
            //only for desktop mobile-dev
        }

        @Override
        public void convertToJPEG(InputStream input, OutputStream output) throws IOException {

        }

        @Override
        public void convertToPNG(InputStream input, OutputStream output) throws IOException {

        }

        @Override
        public Pair<Integer, Integer> getRealScreenSize(boolean real) {
            return Pair.of(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }

        @Override
        public ArrayList<String> getGamepads() {
            return new ArrayList<>();
        }

        @Override
        public UpnpServiceConfiguration getUpnpPlatformService() {
            // not used
            return null;
        }

        @Override
        public boolean needFileAccess() {
            return false;
        }

        @Override
        public void requestFileAcces() {

        }
    }
}