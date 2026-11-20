package futo.enhancements;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputMethodSubtype;
/** Morphe inserts this direct superclass; all original FUTO service methods remain intact. */
public class BridgeIme extends InputMethodService {
    @Override public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        super.onCurrentInputMethodSubtypeChanged(subtype);
        SubtypeSync.changed(this,subtype);
    }
}
