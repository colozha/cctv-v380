package dev.cctv.v380.oracle;

import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Synthetic data only. Runs in its own UID with zero manifest permissions. No vendor Java/JNI startup. */
public final class OracleRunner extends Instrumentation {
    private static native String vectors();

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            PackageInfo pkg = getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 4096);
            if (pkg.requestedPermissions != null && pkg.requestedPermissions.length != 0)
                throw new IllegalStateException("Oracle must have zero permissions");
            System.loadLibrary("talkback_oracle");
            String json = vectors();
            File destination = new File(getTargetContext().getFilesDir(), "vectors.json");
            try (FileOutputStream out = new FileOutputStream(destination)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            result.putString("oracle", "synthetic vectors completed");
            finish(-1, result);
        } catch (Throwable failure) {
            // Inputs are entirely synthetic; no credentials, user audio or camera sessions exist here.
            result.putString("failure", failure.toString());
            finish(0, result);
        }
    }
}
