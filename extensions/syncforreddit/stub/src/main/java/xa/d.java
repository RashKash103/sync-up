package xa;

/**
 * The base of Sync's post and comment model. A class rather than an interface, so its methods
 * take invoke-virtual.
 *
 * <p>Compile only, and named as the app names it. Only the id is declared, which the model's
 * constructor requires to be non null.
 */
public abstract class d {
    /** The Reddit id of this comment or post, without its type prefix. */
    public String U() {
        throw new UnsupportedOperationException("Stub");
    }
}
