package androidx.preference;

/**
 * A row of a settings screen, as the preference library the app carries names its members.
 *
 * <p>Compile only. The library is obfuscated in the app, so the names here are the ones it
 * actually has rather than the ones it was written with; what each one is was established from
 * how the app itself uses it.
 */
public class Preference {
    /** setSummary: the line under the title. */
    public void D0(CharSequence summary) {
        throw new UnsupportedOperationException("Stub");
    }

    /**
     * setEnabled: whether the row can be used. A row that cannot is drawn faded and does not
     * answer a tap, which is how a setting that does not apply is shown.
     */
    public void q0(boolean enabled) {
        throw new UnsupportedOperationException("Stub");
    }

    /** notifyChanged: draw the row again, after something it shows has changed. */
    public void M() {
        throw new UnsupportedOperationException("Stub");
    }

    /** getKey. */
    public String r() {
        throw new UnsupportedOperationException("Stub");
    }
}
