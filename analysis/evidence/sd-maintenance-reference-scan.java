import java.io.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Offline structural references only; no string values, byte arrays or vendor execution. */
class StorageRefs {
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        long count = 0;
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef cls : container.getEntry(entry).getDexFile().getClasses()) {
                for (Method method : cls.getMethods()) {
                    var impl = method.getImplementation();
                    if (impl == null) continue;
                    int offset = 0;
                    for (Instruction instruction : impl.getInstructions()) {
                        count++;
                        String ref = instruction instanceof ReferenceInstruction r &&
                            !(r.getReference() instanceof StringReference) ? r.getReference().toString() : "";
                        String lower = ref.toLowerCase(Locale.ROOT);
                        boolean format = lower.contains("sd_format") || lower.contains("sdcardformat") ||
                            lower.contains("formatstate") || lower.contains("formatstatus") || lower.contains("formatsd");
                        boolean delete = (lower.contains("delete") || lower.contains("remove")) &&
                            (lower.contains("record") || lower.contains("recfile") || lower.contains("tfcard") ||
                             lower.contains("sdcard") || lower.contains("filelistctrl"));
                        boolean literal = instruction instanceof WideLiteralInstruction n &&
                            (n.getWideLiteral() == 131330 || n.getWideLiteral() == 131584);
                        if (format || delete || literal || lower.contains("filelistctrl"))
                            System.out.println(entry + " " + method + " code-unit=" + offset + " " +
                                instruction.getOpcode() + " " + (literal ? "selected-storage-literal" : ref));
                        offset += instruction.getCodeUnits();
                    }
                }
            }
        }
        System.out.println("instructions_scanned=" + count);
    }
}
