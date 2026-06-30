// forge-mac: minimal stub of java.lang.management.ManagementFactory for the MobiVM/iOS build.
package java.lang.management;
public class ManagementFactory {
    public static RuntimeMXBean getRuntimeMXBean() {
        return new RuntimeMXBean() {
            public String getName() { return "0@localhost"; }
            public long getStartTime() { return 0L; }
        };
    }
}
