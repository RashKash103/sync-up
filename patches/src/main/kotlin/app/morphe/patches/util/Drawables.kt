package app.morphe.patches.util

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext

/**
 * Checks that a drawable is one a row can actually be given.
 *
 * <p>Most of the icons named in the app's resources are not there any more. R8's resource
 * shrinker leaves the name and the id behind and replaces the drawing with `<x/>`, a stub whose
 * element names a class that was shrunk away too, so asking for one throws
 * `InflateException: Class not found x` the moment the screen holding it is drawn. Only the
 * drawables the app still uses itself survived as `<vector>`.
 *
 * <p>Nothing says which is which from the name, and a row pointed at a stub patches perfectly
 * and takes the screen down on a device. So the check is done here instead, where it is a patch
 * that fails rather than a settings screen that will not open.
 */
internal fun ResourcePatchContext.drawableNamed(name: String): String {
    val root = try {
        document("res/drawable/$name.xml").use { it.documentElement.tagName }
    } catch (ex: Exception) {
        throw PatchException("The app has no drawable called $name: $ex")
    }

    if (root != "vector") {
        throw PatchException(
            "The drawable $name is a <$root>, not a vector: the resource shrinker took it out " +
                    "and a row given it would bring the screen down. Name one the app still uses."
        )
    }

    return "@drawable/$name"
}
