package futo
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import futo.shared.*
import app.morphe.patcher.util.returnEarly
val spacebarLongPressPatch=patch(
    name="Spacebar long press duration",
    description="Configure the spacebar delay at runtime: original FUTO, keyboard duration, or a custom duration.",
    default=false
) {
    compatibleWith(*targets)
    dependsOn(extensionPatch)
    execute {
        mutableClassDefBy("Lfuto/enhancements/Config;").methods.single { it.name=="spacebarInstalled" }.returnEarly(true)
        Fingerprints.field("Lorg/futo/inputmethod/keyboard/PointerTracker;","mCurrentKey","Lorg/futo/inputmethod/keyboard/Key;")
        Fingerprints.field("Lorg/futo/inputmethod/keyboard/Key;","code","I")
        Fingerprints.field("Lorg/futo/inputmethod/latin/settings/Settings;","sInstance","Lorg/futo/inputmethod/latin/settings/Settings;")
        Fingerprints.field("Lorg/futo/inputmethod/latin/settings/Settings;","mSettingsValues","Lorg/futo/inputmethod/latin/settings/SettingsValues;")
        Fingerprints.field("Lorg/futo/inputmethod/latin/settings/SettingsValues;","mKeyLongpressTimeout","I")
        val method=Fingerprints.method("Lorg/futo/inputmethod/keyboard/PointerTracker;->startLongPressTimer(Lorg/futo/inputmethod/keyboard/Key;)V")
        val code=method.implementation!!.instructions
        val sends=code.indices.filter { (code[it] as? ReferenceInstruction)?.reference.toString()=="Landroid/os/Handler;->sendMessageDelayed(Landroid/os/Message;J)Z" }
        if(sends.size!=1) throw PatchException("Expected one long press timer scheduler")
        val anchor=sends.single()-1
        val conversion=code.getOrNull(anchor) as? TwoRegisterInstruction
        if(code.getOrNull(anchor)?.opcode!=Opcode.INT_TO_LONG || conversion==null)
            throw PatchException("Unsupported long press timer delay flow")
        val self=method.implementation!!.registerCount-2
        val delay=conversion.registerB
        if(self>15 || delay>15 || self==delay) throw PatchException("Unsupported long press timer registers")
        method.addInstructions(anchor,"""
            invoke-static {v$self,v$delay}, Lfuto/enhancements/SpacebarDuration;->timeout(Ljava/lang/Object;I)I
            move-result v$delay
        """)
    }
}
