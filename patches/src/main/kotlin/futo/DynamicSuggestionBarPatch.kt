package futo
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import futo.shared.*
val dynamicSuggestionBarPatch=patch(
    name="Dynamic suggestion bar",
    description="Use FUTO's original action row for actions when idle and suggestions while composing. Configurable at runtime.",
    default=false
) {
    compatibleWith(target)
    dependsOn(extensionPatch)
    execute {
        mutableClassDefBy("Lfuto/enhancements/Config;").methods.single { it.name=="dynamicInstalled" }.returnEarly(true)
        Fingerprints.field("Lorg/futo/inputmethod/latin/WordComposer;","mIsBatchMode","Z")
        Fingerprints.field("Lorg/futo/inputmethod/latin/SuggestedWords;","mInputStyle","I")
        Fingerprints.field("Lorg/futo/inputmethod/latin/SuggestedWords;","mSuggestedWordInfoList","Ljava/util/ArrayList;")
        val start=Fingerprints.method("Lorg/futo/inputmethod/latin/LatinIME;->onStartInputView(Landroid/view/inputmethod/EditorInfo;Z)V")
        start.beforeReturns("invoke-static/range {p0 .. p0}, Lfuto/enhancements/Dynamic;->start(Landroid/content/Context;)V")
        val suggestions=Fingerprints.method("Lorg/futo/inputmethod/latin/LatinIME;->setSuggestions(Lorg/futo/inputmethod/latin/SuggestedWords;Lorg/futo/inputmethod/engine/ExpandableSuggestionBarConfiguration;)V")
        suggestions.beforeReturns("invoke-static/range {p0 .. p0}, Lfuto/enhancements/Dynamic;->update(Ljava/lang/Object;)V")
        val toggle=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/UixManager;->toggleActionsExpanded()V")
        toggle.requireLocal()
        toggle.addInstructions(0,"""
            invoke-static/range {p0 .. p0}, Lfuto/enhancements/Dynamic;->toggle(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            nop
        """)
        Fingerprints.method("Lorg/futo/inputmethod/latin/uix/UixManager;->onActionActivated(Lorg/futo/inputmethod/latin/uix/Action;)V")
            .addInstructions(0,"invoke-static/range {p0 .. p0}, Lfuto/enhancements/Dynamic;->action(Ljava/lang/Object;)V")
        val bar=mutableClassDefBy("Lorg/futo/inputmethod/latin/uix/ActionBarKt;").methods.single { it.name=="ActionBar" }
        val instructions=bar.implementation!!.instructions
        val anchors=instructions.indices.filter {
            (instructions[it] as? ReferenceInstruction)?.reference.toString().contains("->OldStyleActionsBar:")
        }
        if(anchors.size!=1) throw PatchException("Missing unique action bar setting")
        val anchor=anchors.single()
        val calls=(anchor+1..minOf(anchor+4,instructions.lastIndex)).filter {
            (instructions[it] as? ReferenceInstruction)?.reference.toString().contains("->useDataStore(")
        }
        if(calls.size!=1) throw PatchException("Unsupported action bar setting flow")
        val resultIndex=calls.single()+1
        if(instructions.getOrNull(resultIndex)?.opcode!=com.android.tools.smali.dexlib2.Opcode.MOVE_RESULT_OBJECT)
            throw PatchException("Missing action bar DataStore result")
        val reg=(instructions[resultIndex] as com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction).registerA
        bar.addInstructions(resultIndex+1,"""
            invoke-static/range {v$reg .. v$reg}, Lfuto/enhancements/Dynamic;->oldStyleItem(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v$reg
            check-cast v$reg, Lorg/futo/inputmethod/latin/uix/settings/DataStoreItem;
        """)
        // The existing sizing calculator reads the same setting through this accessor.
        val setting=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/SettingsKt;->getSetting(Landroid/content/Context;Lorg/futo/inputmethod/latin/uix/SettingsKey;)Ljava/lang/Object;")
        setting.requireLocal()
        setting.addInstructions(0,"""
            invoke-static {p1}, Lfuto/enhancements/Dynamic;->overrideSetting(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :original
            sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
            return-object v0
            :original
            nop
        """)
    }
}
