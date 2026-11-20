package futo.shared
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.patch.*
val target = Compatibility(packageName = "org.futo.inputmethod.latin.playstore", name = "FUTO Keyboard")
fun verifyTarget(metadata: PackageMetadata) {
    if (metadata.packageName != "org.futo.inputmethod.latin.playstore")
        throw PatchException("Unsupported target: ${metadata.packageName}")
}
