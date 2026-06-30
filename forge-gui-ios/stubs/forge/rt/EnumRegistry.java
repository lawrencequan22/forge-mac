// forge-mac: MobiVM 2.3.23 fails reflective values() on very large enums (200+ constants), so EnumMap
// / getEnumConstants throw a bare AssertionError for them (FPref=285, TrackableProperty=216). Normal
// reflection works; it's purely a size limit.
//
// The build rt-patches java.lang.Enum.getSharedConstants to consult this registry first. Because the
// patched code lives in java.lang.Enum (loaded by the BOOTSTRAP class loader), this class MUST also be
// on the boot classpath — a bootstrap class can't reference an application class. So it ships in
// ios-stubs.jar (robovm.xml <bootclasspath>), in its own package to avoid split-packaging the app.
// The iOS launcher registers the oversized enums REFLECTIVELY (Class.forName) so it needs no
// compile-time dependency on this boot-classpath class.
package forge.rt;

import java.util.HashMap;
import java.util.Map;

public final class EnumRegistry {
    private static final Map<Class<?>, Object[]> CONSTANTS = new HashMap<Class<?>, Object[]>();

    private EnumRegistry() {}

    public static void register(Class<?> enumType, Object[] values) {
        CONSTANTS.put(enumType, values);
    }

    public static Object[] get(Class<?> enumType) {
        return CONSTANTS.get(enumType);
    }
}
