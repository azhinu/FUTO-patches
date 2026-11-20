package futo
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import futo.shared.*
val remoteResources=patch {
    execute {
        verifyTarget(packageMetadata)
        document("AndroidManifest.xml").use { doc ->
            val root=doc.documentElement
            val permission=doc.createElement("uses-permission")
            permission.setAttributeNS(androidNs,"android:name","android.permission.INTERNET")
            root.appendChild(permission)
            val app=doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
            app.setAttributeNS(androidNs,"android:usesCleartextTraffic","true")
            app.setAttributeNS(androidNs,"android:fullBackupContent","@xml/futo_enhancements_backup")
            app.setAttributeNS(androidNs,"android:dataExtractionRules","@xml/futo_enhancements_extraction")
        }
        for(language in listOf("en","ru")) {
            val stream=Fingerprints::class.java.getResourceAsStream("/test-$language.wav")
                ?: throw PatchException("Missing synthetic speech test audio")
            stream.use { input -> resourceFile("assets/futo-enhancements-test-$language.wav").outputStream().use { input.copyTo(it) } }
        }
        resourceFile("res/xml/futo_enhancements_backup.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
            <full-backup-content><exclude domain="sharedpref" path="futo_enhancements.xml"/></full-backup-content>""")
        resourceFile("res/xml/futo_enhancements_extraction.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
            <data-extraction-rules><cloud-backup><exclude domain="sharedpref" path="futo_enhancements.xml"/></cloud-backup><device-transfer><exclude domain="sharedpref" path="futo_enhancements.xml"/></device-transfer></data-extraction-rules>""")
    }
}
val remoteWhisperPatch=patch(
    name="Remote speech backends",
    description="OpenRouter, Whisper ASR Webservice and OpenAI-compatible transcription through FUTO's original recorder and insertion flow. Local is the default.",
    default=false
) {
    compatibleWith(target)
    dependsOn(extensionPatch,remoteResources)
    execute {
        mutableClassDefBy("Lfuto/enhancements/Config;").methods.single { it.name=="remoteInstalled" }.returnEarly(true)
        val run=Fingerprints.method("Lorg/futo/voiceinput/shared/AudioRecognizer;->access\$runModel(Lorg/futo/voiceinput/shared/AudioRecognizer;Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;")
        run.requireLocal()
        run.addInstructions(0,"""
            invoke-static/range {p0 .. p1}, Lfuto/enhancements/Remote;->run(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :original
            sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
            return-object v0
            :original
            nop
        """)
        Fingerprints.method("Lorg/futo/voiceinput/shared/AudioRecognizer;->reset()V")
            .addInstructions(0,"invoke-static/range {p0 .. p0}, Lfuto/enhancements/Remote;->cancel(Ljava/lang/Object;)V")
        val ctor=Fingerprints.method("Lorg/futo/voiceinput/shared/AudioRecognizer;-><init>(Lorg/futo/inputmethod/latin/LatinIME;Landroidx/lifecycle/LifecycleCoroutineScopeImpl;Lkotlinx/coroutines/flow/SharingConfig;Lcom/charleskorn/kaml/YamlParser;Lorg/futo/voiceinput/shared/AudioRecognizerSettings;)V")
        val code=ctor.implementation!!.instructions
        fun fieldIndex(name:String)=code.indices.singleOrNull {
            (code[it] as? ReferenceInstruction)?.reference.toString().contains("AudioRecognizer;->$name:")
        } ?: throw PatchException("Missing or ambiguous recorder field: $name")
        val begin=fieldIndex("floatSamples")+1
        val callback=fieldIndex("runnerCallback")-3
        if(code.getOrNull(begin)?.opcode!=com.android.tools.smali.dexlib2.Opcode.NEW_INSTANCE ||
           (code[begin] as? ReferenceInstruction)?.reference.toString()!="Ljava/util/ArrayList;" ||
           code.getOrNull(callback)?.opcode!=com.android.tools.smali.dexlib2.Opcode.NEW_INSTANCE ||
           callback<=begin || ctor.implementation!!.registerCount-ctor.parameterTypes.size-1!=1)
            throw PatchException("Unsupported recorder initialization flow")
        // v0 is the constructor's sole local; parameters and callback setup remain intact.
        ctor.addInstructionsWithLabels(begin,"""
            invoke-static {}, Lfuto/enhancements/Remote;->skipLocalLoading()Z
            move-result v0
            if-nez v0, :callback
        """,ExternalLabel("callback",code[callback]))
        val recording=Fingerprints.method("Lorg/futo/voiceinput/shared/AudioRecognizer\$startRecording\$1\$1;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;")
        recording.requireLocal()
        recording.addInstructions(0,"""
            invoke-static {}, Lfuto/enhancements/Remote;->skipLocalLoading()Z
            move-result v0
            if-eqz v0, :original
            sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
            return-object v0
            :original
            nop
        """)
    }
}
