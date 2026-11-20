package futo.shared
import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
val settingsResources=patch {
    execute {
        verifyTarget(packageMetadata)
        document("res/values/strings.xml").use { doc ->
            for((name,text) in listOf("futo_dynamic_bar" to "Dynamic Suggestion Bar","futo_speech_backend" to "Speech Recognition Backend","futo_swipe_backspace" to "Character Backspace After Swipe","futo_spacebar_duration" to "Spacebar Long Press Duration")) {
                val existing=doc.getElementsByTagName("string")
                if((0 until existing.length).any { (existing.item(it) as org.w3c.dom.Element).getAttribute("name")==name })
                    throw PatchException("Enhancement resource already exists: $name")
                val entry=doc.createElement("string")
                entry.setAttribute("name",name); entry.textContent=text; doc.documentElement.appendChild(entry)
            }
        }
    }
}
val runtimePatch=patch {
    dependsOn(preflight)
    extendWith("enhancements.mpe")
    execute {
        val onCreate=Fingerprints.method("Lorg/futo/inputmethod/latin/LatinIME;->onCreate()V")
        onCreate.addInstructions(0,"invoke-static/range {p0 .. p0}, Lfuto/enhancements/Config;->init(Landroid/content/Context;)V")
    }
}
val extensionPatch=patch {
    dependsOn(runtimePatch,settingsResources)
    execute {
        val create=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/settings/SettingsActivity;->onCreate(Landroid/os/Bundle;)V")
        create.addInstructions(0,"invoke-static/range {p0 .. p0}, Lfuto/enhancements/SettingsEntry;->install(Landroid/app/Activity;)V")
        val resume=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/settings/SettingsActivity;->onResume()V")
        resume.beforeReturns("invoke-static/range {p0 .. p0}, Lfuto/enhancements/SettingsEntry;->install(Landroid/app/Activity;)V")
        val menu=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/settings/UserSettingsMenu;-><init>(ILjava/lang/Integer;Lkotlin/jvm/functions/Function2;Ljava/lang/String;ZLjava/util/List;)V")
        if(menu.implementation!!.registerCount > 16) throw PatchException("Unsupported settings constructor registers")
        menu.addInstructions(0,"""
            invoke-static {p4,p6}, Lfuto/enhancements/SettingsEntry;->menuItems(Ljava/lang/String;Ljava/util/List;)Ljava/util/List;
            move-result-object p6
        """)
        val navigator=Fingerprints.method("Lorg/futo/inputmethod/latin/uix/settings/SettingsNavigatorKt;-><clinit>()V")
        Fingerprints.field("Lorg/futo/inputmethod/latin/uix/settings/SettingsNavigatorKt;","SettingsMenus","Ljava/util/ArrayList;")
        navigator.requireLocal()
        navigator.beforeReturns("""
            sget-object v0, Lorg/futo/inputmethod/latin/uix/settings/SettingsNavigatorKt;->SettingsMenus:Ljava/util/ArrayList;
            invoke-static {v0}, Lfuto/enhancements/SettingsEntry;->extendMenus(Ljava/util/ArrayList;)V
        """)
        // The runtime UI uses these original components rather than shipping another Compose version.
        for(signature in Fingerprints.methods.take(5)) Fingerprints.method(signature)
    }
}
