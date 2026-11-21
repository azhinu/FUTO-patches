package futo.shared
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.patch.*
val targets = arrayOf(
    Compatibility(packageName = "org.futo.inputmethod.latin.playstore", name = "FUTO Keyboard"),
    Compatibility(packageName = "org.futo.inputmethod.latin.unstable", name = "RUTO Keyboard")
)
fun verifyTarget(metadata: PackageMetadata) {
    if (targets.none { it.packageName == metadata.packageName })
        throw PatchException("Unsupported target: ${metadata.packageName}")
}
