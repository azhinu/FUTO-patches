package futo.shared
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method
object Fingerprints {
    fun signature(m: Method) = "${m.definingClass}->${m.name}(${m.parameterTypes.joinToString("")})${m.returnType}"
    val methods = listOf(
        "Lorg/futo/inputmethod/latin/uix/settings/SettingsActivity;->onCreate(Landroid/os/Bundle;)V",
        "Lorg/futo/inputmethod/latin/uix/settings/UserSettingsMenu;-><init>(ILjava/lang/Integer;Lkotlin/jvm/functions/Function2;Ljava/lang/String;ZLjava/util/List;)V",
        "Lorg/futo/inputmethod/latin/uix/settings/UserSetting;-><init>(ILjava/lang/Integer;Ljava/lang/Integer;Ljava/util/List;Lkotlin/jvm/functions/Function2;ZZLkotlin/jvm/functions/Function2;)V",
        "Lorg/futo/inputmethod/latin/uix/settings/DataStoreItem;-><init>(Ljava/lang/Object;Lkotlin/jvm/functions/Function1;)V",
        "Lorg/futo/inputmethod/latin/uix/settings/ComponentsKt;->NavigationItem(Ljava/lang/String;Lorg/futo/inputmethod/latin/uix/settings/NavigationItemStyle;Lkotlin/jvm/functions/Function0;Landroidx/compose/ui/graphics/painter/Painter;Ljava/lang/String;ZLandroidx/compose/runtime/GapComposer;II)V",
        "Lorg/futo/inputmethod/latin/uix/settings/pages/DevLayoutEditorKt;->getCustomLayout(Lorg/futo/inputmethod/latin/uix/settings/pages/CustomLayout\$Companion;Landroid/content/Context;Ljava/lang/String;)Lorg/futo/inputmethod/v2keyboard/Keyboard;",

    )
    fun field(type:String,name:String,fieldType:String) {
        if(classDefBy(type).fields.count { it.name==name && it.type==fieldType }!=1)
            throw PatchException("Target field missing or ambiguous: $type->$name:$fieldType")
    }
    fun method(signature: String): MutableMethod {
        val type=signature.substringBefore("->")
        return mutableClassDefBy(type).methods.singleOrNull { signature(it)==signature }
            ?: throw PatchException("Target method missing or ambiguous: $signature")
    }
}
// Compatibility is structural, not a whitelist of release numbers or instruction hashes.
val preflight = patch {
    execute { verifyTarget(packageMetadata) }
}
