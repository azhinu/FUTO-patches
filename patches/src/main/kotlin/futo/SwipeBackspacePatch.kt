package futo
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import futo.shared.*
val swipeBackspacePatch=patch(
    name="Character backspace after swipe",
    description="Optional character deletion after swipe typing; holding backspace deletes the edited word.",
    default=false
) {
    compatibleWith(target)
    dependsOn(extensionPatch)
    execute {
        mutableClassDefBy("Lfuto/enhancements/Config;").methods.single { it.name=="swipeBackspaceInstalled" }.returnEarly(true)
        val type="Lorg/futo/inputmethod/latin/inputlogic/InputLogic;"
        Fingerprints.field(type,"mWordComposer","Lorg/futo/inputmethod/latin/WordComposer;")
        Fingerprints.field(type,"mConnection","Lorg/futo/inputmethod/latin/RichInputConnection;")
        Fingerprints.field(type,"mImeHelper","Lorg/futo/inputmethod/engine/IMEHelper;")
        Fingerprints.field("Lorg/futo/inputmethod/latin/WordComposer;","mIsBatchMode","Z")
        Fingerprints.field("Lorg/futo/inputmethod/latin/WordComposer;","mTypedWordCache","Ljava/lang/CharSequence;")
        Fingerprints.method("Lorg/futo/inputmethod/latin/WordComposer;->setComposingWord([I[I)Z")
        Fingerprints.method("Lorg/futo/inputmethod/engine/IMEHelper;->getCodepointCoordinates([I)[I")
        val method=Fingerprints.method("$type->onCodeInput(Lorg/futo/inputmethod/latin/settings/SettingsValues;Lorg/futo/inputmethod/event/Event;II)Lorg/futo/inputmethod/event/InputTransaction;")
        val code=method.implementation!!.instructions
        val anchors=code.indices.filter { (code[it] as? ReferenceInstruction)?.reference.toString()=="Lorg/futo/inputmethod/latin/settings/SettingsValues;->mBackspaceModeHold:I" }
        if(anchors.size!=1)throw PatchException("Missing unique backspace hold mode read")
        val index=anchors.single()
        if(code[index].opcode!=Opcode.IGET)throw PatchException("Unsupported backspace hold mode flow")
        val reg=(code[index] as TwoRegisterInstruction).registerA
        method.replaceInstruction(index,"invoke-static/range {p0 .. p1}, Lfuto/enhancements/SwipeBackspace;->holdMode(Ljava/lang/Object;Ljava/lang/Object;)I")
        method.addInstructions(index+1,"move-result v$reg")
        method.addInstructions(0,"invoke-static/range {p0 .. p2}, Lfuto/enhancements/SwipeBackspace;->beforeInput(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")
    }
}
