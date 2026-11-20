import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.*
import java.io.File
fun main(args:Array<String>) {
 val dex = DexFileFactory.loadDexContainer(File(args[0]), Opcodes.getDefault())
 for(entry in dex.dexEntryNames) for(c in dex.getEntry(entry)!!.dexFile.classes) {
  if(!args.drop(1).filter { it!="dump" }.any { c.type.contains(it) }) continue
  println("CLASS ${c.type} : ${c.superclass}")
  for(f in c.fields) println("FIELD ${f.name}:${f.type}")
  for(m in c.methods) {
   println("METHOD ${m.name}(${m.parameterTypes.joinToString("")})${m.returnType} registers=${m.implementation?.registerCount}")
   if(args.last()=="dump") m.implementation?.instructions?.forEachIndexed { i,it ->
    val regs= when(it) {
     is FiveRegisterInstruction -> listOf(it.registerC,it.registerD,it.registerE,it.registerF,it.registerG).take(it.registerCount).toString()
     is RegisterRangeInstruction -> "${it.startRegister}..${it.startRegister+it.registerCount-1}"
     is TwoRegisterInstruction -> "${it.registerA},${it.registerB}"
     is OneRegisterInstruction -> "${it.registerA}"
     else -> ""
    }
    val ref=if(it is ReferenceInstruction) it.reference.toString() else if(it is NarrowLiteralInstruction) it.narrowLiteral.toString() else ""
    println("  $i ${it.opcode} $regs $ref")
   }
  }
 }
}
