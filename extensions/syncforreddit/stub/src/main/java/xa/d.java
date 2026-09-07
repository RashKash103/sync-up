package xa;

/**
 * The base of Sync's post and comment model. A class rather than an interface, so its methods
 * take invoke-virtual.
 *
 * <p>Compile only, and named as the app names it. Only what is read is declared.
 */
public abstract class d {
    /** The Reddit id of this comment or post, without its type prefix. */
    public String U() {
        throw new UnsupportedOperationException("Stub");
    }

    /** What kind of thing this is. A comment answers {@code 11}. */
    public int Y0() {
        throw new UnsupportedOperationException("Stub");
    }

    /** A comment's text, as it was written. */
    public String n() {
        throw new UnsupportedOperationException("Stub");
    }

    /** A post's text, as it was written. */
    public String N0() {
        throw new UnsupportedOperationException("Stub");
    }
}
