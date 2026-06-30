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
    private static void keepEnumReflection(String docs) {
        Class<?>[] enums = {
            forge.localinstance.properties.ForgePreferences.FPref.class,
            forge.localinstance.properties.ForgeNetPreferences.FNetPref.class,
            forge.gamemodes.planarconquest.ConquestPreferences.CQPref.class,
            forge.gamemodes.quest.data.QuestPreferences.QPref.class,
            forge.trackable.TrackableProperty.class,
        };
        java.io.File log = new java.io.File(docs, "enumfix.log");
        try (java.io.PrintWriter w = new java.io.PrintWriter(log)) {
            for (Class<?> c : enums) {
                // getEnumConstants() goes through Enum.getSharedConstants and populates its
                // per-class BasicLruCache with a traceable class literal -> the later generic
                // new EnumMap<>(clasz) reuses the cache instead of re-reflecting.
                try { Object[] ec = c.getEnumConstants();
                      w.println(c.getName() + " getEnumConstants: " + (ec == null ? "null" : "OK " + ec.length)); }
                catch (Throwable t) { w.println(c.getName() + " getEnumConstants FAILED: " + t); }
                // also exercise the exact failing op with a literal, to compare vs the generic site
                try { new java.util.EnumMap(c); w.println(c.getName() + " EnumMap(literal): OK"); }
                catch (Throwable t) { w.println(c.getName() + " EnumMap(literal) FAILED: " + t); }
            }
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

        keepEnumReflection(docs); // forge-mac: retain reflective enum values() for generic-Class EnumMap sites

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