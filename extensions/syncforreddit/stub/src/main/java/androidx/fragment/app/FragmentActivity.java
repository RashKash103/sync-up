package androidx.fragment.app;

/**
 * Only named so that calls taking one can be written, and so that an activity reached through a
 * view's context can be recognised as one.
 *
 * <p>Compile only: the app carries the real one.
 */
public class FragmentActivity extends android.app.Activity {
    public FragmentManager getSupportFragmentManager() {
        throw new UnsupportedOperationException("Stub");
    }
}
