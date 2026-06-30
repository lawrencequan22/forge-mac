// forge-mac: java.util.StringJoiner is absent from MobiVM's iOS runtime, but Forge uses it (e.g.
// ForgePreferences.FPref.<clinit>), so the enum's class init failed with NoClassDefFoundError — which
// Enum.getSharedConstants then masked as a bare AssertionError, surfacing as the "EnumMap" crash.
// Faithful StringBuilder-backed reimplementation, on the boot classpath (ios-stubs.jar).
package java.util;

public final class StringJoiner {
    private final String prefix;
    private final String delimiter;
    private final String suffix;
    private StringBuilder value;
    private String emptyValue;

    public StringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public StringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (delimiter == null || prefix == null || suffix == null) throw new NullPointerException();
        this.delimiter = delimiter.toString();
        this.prefix = prefix.toString();
        this.suffix = suffix.toString();
        this.emptyValue = this.prefix + this.suffix;
    }

    public StringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public StringJoiner add(CharSequence newElement) {
        if (value == null) {
            value = new StringBuilder().append(prefix);
        } else {
            value.append(delimiter);
        }
        value.append(newElement);
        return this;
    }

    public StringJoiner merge(StringJoiner other) {
        if (other.value != null) {
            int len = other.value.length();
            add(other.value.substring(other.prefix.length(), len));
        }
        return this;
    }

    public int length() {
        return value != null ? value.length() + suffix.length() : emptyValue.length();
    }

    @Override
    public String toString() {
        if (value == null) return emptyValue;
        if (suffix.isEmpty()) return value.toString();
        int initialLength = value.length();
        String result = value.append(suffix).toString();
        value.setLength(initialLength);
        return result;
    }
}
