package futo
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.returnEarly
import futo.shared.*
val imeSubtypesPatch=patch(
    name="Android IME layout subtypes",
    description="Register enabled FUTO layouts with stable identities and synchronize Android and FUTO selections.",
    default=false
) {
    compatibleWith(target)
    dependsOn(runtimePatch)
    execute {
        mutableClassDefBy("Lfuto/enhancements/Config;").methods.single { it.name=="subtypesInstalled" }.returnEarly(true)
        Fingerprints.method(Fingerprints.methods[5])
        val ime=mutableClassDefBy("Lorg/futo/inputmethod/latin/LatinIME;")
        if(ime.superclass!="Landroid/inputmethodservice/InputMethodService;") throw PatchException("Unexpected IME superclass")
        ime.setSuperClass("Lfuto/enhancements/BridgeIme;")
        // Constructor must invoke its new direct superclass.
        val ctor=ime.methods.single { it.name=="<init>" }
        val superCalls=ctor.implementation!!.instructions.indices.filter {
            (ctor.implementation!!.instructions[it] as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference.toString()=="Landroid/inputmethodservice/InputMethodService;-><init>()V"
        }
        if(superCalls.size!=1) throw PatchException("Missing unique IME superclass constructor")
        ctor.replaceInstruction(superCalls.single(),"invoke-direct {p0}, Lfuto/enhancements/BridgeIme;-><init>()V")
        val start=Fingerprints.method("Lorg/futo/inputmethod/latin/LatinIME;->onStartInputView(Landroid/view/inputmethod/EditorInfo;Z)V")
        start.beforeReturns("invoke-static/range {p0 .. p0}, Lfuto/enhancements/SubtypeSync;->start(Landroid/inputmethodservice/InputMethodService;)V")
        val change=Fingerprints.method("Lorg/futo/inputmethod/latin/LatinIME;->changeSubtype(Ljava/lang/String;)V")
        change.addInstructions(0,"invoke-static/range {p0 .. p1}, Lfuto/enhancements/SubtypeSync;->internal(Landroid/inputmethodservice/InputMethodService;Ljava/lang/String;)V")
        imeMetadata { root ->
            val subtypes=root.getElementsByTagName("subtype")
            for(i in 0 until subtypes.length) (subtypes.item(i) as org.w3c.dom.Element).setAttributeNS(androidNs,"android:overridesImplicitlyEnabledSubtype","false")
        }
    }
}
