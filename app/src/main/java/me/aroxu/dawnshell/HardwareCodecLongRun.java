package me.aroxu.dawnshell;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Controls the fixed, allow-listed codec soak-test unit inside Debian PID 1. */
final class HardwareCodecLongRun {

    enum Operation { START, STOP, STATUS }

    private static final String STATUS_FILE = "hardware-codec-long-run.status";
    private static final int MAX_STATUS_BYTES = 8 * 1024;

    private HardwareCodecLongRun() {}

    static BfuSu.Result run(Context context, BfuRuntime.Layout layout,
                            Operation operation) throws IOException, InterruptedException {
        String command = namespaceCommand(layout,
                operation.name().toLowerCase(Locale.US));
        BfuSu.Result result = BfuSu.run(command, 25_000L);
        String summary = operation.name()
                + " exit=" + result.exitCode
                + " timeout=" + result.timedOut
                + " output=" + BfuSu.sanitize(result.output);
        writeStatus(context, (result.exitedSuccessfully() ? "SUCCEEDED " : "FAILED ")
                + summary);
        HardwareCodecProbe.recordRuntimeEvent(context,
                "LONG_RUN_CONTROL " + summary);
        return result;
    }

    static String readStatus(Context context) throws IOException {
        return StatusStore.readText(new File(
                BfuPreferences.deviceProtectedContext(context).getFilesDir(), STATUS_FILE));
    }

    static String readLiveReport(Context context) throws IOException {
        final BfuRuntime.Layout layout;
        try {
            layout = BfuRuntime.layout(context);
        } catch (IllegalStateException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (!layout.namespaceProbeBinary.isFile()
                || !layout.namespaceProbeBinary.canExecute()) {
            return "DawnShell namespace launcher is not provisioned.";
        }
        try {
            BfuSu.Result result = BfuSu.runRaw(namespaceCommand(layout, "report"),
                    25_000L);
            String header = "exit=" + result.exitCode + " timeout=" + result.timedOut;
            if (result.output.isEmpty()) return header;
            return header + "\n\n" + result.output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading codec long-run journal", e);
        }
    }

    static void recordFailure(Context context, Operation operation, String reason) {
        String summary = operation.name() + " " + BfuSu.sanitize(reason);
        try {
            writeStatus(context, "FAILED " + summary);
        } catch (IOException ignored) {
            // The original failure remains the useful diagnostic.
        }
        HardwareCodecProbe.recordRuntimeEvent(context,
                "LONG_RUN_CONTROL_FAILED " + summary);
    }

    private static String namespaceCommand(BfuRuntime.Layout layout, String operation) {
        return BfuSu.shellQuote(layout.namespaceProbeBinary.getAbsolutePath())
                + " codec-long-run " + BfuSu.shellQuote(BfuRootfsProbe.ROOTFS_PATH)
                + " " + BfuSu.shellQuote(layout.run.getAbsolutePath())
                + " " + BfuSu.shellQuote(operation);
    }

    private static void writeStatus(Context context, String value) throws IOException {
        StatusStore.write(new File(BfuPreferences.deviceProtectedContext(context).getFilesDir(),
                STATUS_FILE), value);
    }

}
