package futo.shared
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import org.w3c.dom.Element
const val androidNs = "http://schemas.android.com/apk/res/android"
fun imeMetadata(edit: (Element) -> Unit) {
    verifyTarget(packageMetadata)
    document("AndroidManifest.xml").use { manifest ->
        val services=manifest.getElementsByTagName("service")
        val service=(0 until services.length).map { services.item(it) as Element }.singleOrNull {
            it.getAttributeNS(androidNs,"name")=="org.futo.inputmethod.latin.LatinIME"
        } ?: throw PatchException("Expected exactly one FUTO IME service")
        if(service.getAttributeNS(androidNs,"permission")!="android.permission.BIND_INPUT_METHOD") throw PatchException("IME permission mismatch")
        val metadata=service.getElementsByTagName("meta-data")
        val entry=(0 until metadata.length).map { metadata.item(it) as Element }.singleOrNull { it.getAttributeNS(androidNs,"name")=="android.view.im" }
            ?: throw PatchException("Missing IME metadata")
        if(entry.getAttributeNS(androidNs,"resource")!="@xml/method") throw PatchException("Unexpected IME metadata resource")
    }
    document("res/xml/method.xml").use { doc ->
        val root=doc.documentElement
        if(root.tagName!="input-method" || root.getElementsByTagName("subtype").length==0) throw PatchException("Unexpected IME XML structure")
        edit(root)
    }
}

fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.beforeReturns(code: String) {
    val returns=implementation!!.instructions.indices.filter { implementation!!.instructions[it].opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID }
    if(returns.isEmpty()) throw PatchException("No return instruction in $name")
    for(index in returns.reversed()) addInstructionsAtControlFlowLabel(index,code)
}

fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.requireLocal() {
    val parameters=parameterTypes.sumOf { if(it=="J" || it=="D") 2 else 1 } + if(accessFlags and 8==0) 1 else 0
    if((implementation?.registerCount ?: 0)<=parameters)
        throw PatchException("Missing scratch register in $definingClass->$name")
}
