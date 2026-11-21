package futo
import app.morphe.patcher.patch.*
import futo.shared.*
val systemSpellCheckerPatch = patch(
    name = "System spell checker",
    description = "Allow Android's selected SpellCheckerService while FUTO is active.",
    default = false
) {
    compatibleWith(*targets)
    execute {
        imeMetadata { root ->
            val value=root.getAttributeNS(androidNs,"suppressesSpellChecker")
            if(value!="true" && value!="false") throw PatchException("Missing or invalid spell checker suppression flag")
            root.setAttributeNS(androidNs,"android:suppressesSpellChecker","false")
        }
    }
}
