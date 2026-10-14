import java.io.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
class AlarmRefs {
 public static void main(String[] args) throws Exception {
  var c=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());long count=0;
  for(String e:c.getDexEntryNames()) for(ClassDef k:c.getEntry(e).getDexFile().getClasses()) for(Method m:k.getMethods()) {
   var impl=m.getImplementation();if(impl==null)continue; int offset=0;
   for(Instruction i:impl.getInstructions()) {
    count++;
    String ref=i instanceof ReferenceInstruction r ? r.getReference().toString():"";
    boolean field=ref.contains("->DPID_ALARM_MT_SENSITIVITY:");
    boolean direct=i instanceof WideLiteralInstruction l && l.getWideLiteral()==655619;
    if(field||direct)System.out.println(e+" "+m+" code-unit="+offset+" "+i.getOpcode()+" "+(field?ref:"literal655619"));
    offset+=i.getCodeUnits();
   }
  }
  System.out.println("instructions_scanned="+count);
 }
}
