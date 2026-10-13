import java.io.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.dexbacked.*;

/** Bounded offline DEX instruction dump. String contents and array payloads excluded. */
class GateDex {
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef cls : container.getEntry(entry).getDexFile().getClasses()) {
                if (!cls.getType().equals(args[1])) continue;
                for (Method method : cls.getMethods()) {
                    if (!method.getName().equals(args[2])) continue;
                    var impl = method.getImplementation();
                    if (impl == null) continue;
                    var offsetField = DexBackedMethodImplementation.class.getDeclaredField("codeOffset");
                    offsetField.setAccessible(true);
                    int codeOffset = offsetField.getInt(impl);
                    System.out.println("DEX " + entry + " method " + method + " code_item_offset=" + codeOffset + " registers=" + impl.getRegisterCount());
                    int units = 0;
                    for (Instruction ins : impl.getInstructions()) {
                        StringBuilder line = new StringBuilder(String.format("%04x dex@%08x %-24s ", units, codeOffset + 16 + units * 2, ins.getOpcode().name));
                        if (ins instanceof FiveRegisterInstruction r) {
                            int[] regs = {r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG()};
                            for (int i = 0; i < r.getRegisterCount(); i++) line.append("v").append(regs[i]).append(" ");
                        } else if (ins instanceof RegisterRangeInstruction r) {
                            line.append("v").append(r.getStartRegister()).append("..").append(r.getStartRegister() + r.getRegisterCount() - 1).append(" ");
                        } else if (ins instanceof ThreeRegisterInstruction r) {
                            line.append("v").append(r.getRegisterA()).append(" v").append(r.getRegisterB()).append(" v").append(r.getRegisterC()).append(" ");
                        } else if (ins instanceof TwoRegisterInstruction r) {
                            line.append("v").append(r.getRegisterA()).append(" v").append(r.getRegisterB()).append(" ");
                        } else if (ins instanceof OneRegisterInstruction r) {
                            line.append("v").append(r.getRegisterA()).append(" ");
                        }
                        if (ins instanceof WideLiteralInstruction literal) line.append("#").append(literal.getWideLiteral()).append(" ");
                        if (ins instanceof ReferenceInstruction ref) {
                            if (ref.getReference() instanceof StringReference) line.append("<string-content-excluded>");
                            else line.append(ref.getReference());
                        }
                        if (ins instanceof OffsetInstruction branch) line.append(String.format(" -> %04x", units + branch.getCodeOffset()));
                        if (ins.getOpcode().name.contains("payload")) line.append(" <payload-content-excluded>");
                        System.out.println(line);
                        units += ins.getCodeUnits();
                    }
                }
            }
        }
    }
}
