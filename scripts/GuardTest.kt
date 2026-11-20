import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import futo.shared.preflight
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import java.io.File
fun main(args: Array<String>) = runBlocking {
    Patcher(PatcherConfig(File(args[0]), temporaryFilesPath=File("build/guard-scratch"))).use { patcher ->
        patcher += setOf(preflight)
        val results=patcher().toList()
        check(results.any { it.exception?.toString()?.contains("Unsupported target") == true }) { "Target guard did not reject the unsupported APK: $results" }
        println("Passed direct unsupported-target preflight")
    }
}
