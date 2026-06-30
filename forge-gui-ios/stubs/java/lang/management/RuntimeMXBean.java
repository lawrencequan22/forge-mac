// forge-mac: minimal stub of java.lang.management.RuntimeMXBean — MobiVM's iOS runtime has no
// java.lang.management package, and tinylog (Forge's logger) calls getName()/getStartTime() to
// derive a process id. Only these two methods are referenced.
package java.lang.management;
public interface RuntimeMXBean {
    String getName();
    long getStartTime();
}
