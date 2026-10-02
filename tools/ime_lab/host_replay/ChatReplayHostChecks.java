package com.cyime.audit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** JVM-only path/flag checks; does not load a native library or contact a device. */
public final class ChatReplayHostChecks {
    private static int checks;

    private static Object call(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = ChatReplay.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> flags(String... values) throws Exception {
        return (Map<String, String>) call("flags", new Class<?>[]{String[].class}, (Object) values);
    }

    private static File root(String... values) throws Exception {
        return (File) call("auditRoot", new Class<?>[]{Map.class}, flags(values));
    }

    private interface Check { void run() throws Exception; }

    private static void rejected(Class<? extends Exception> expected, String message, Check action) throws Exception {
        try {
            action.run();
        } catch (Exception error) {
            if (expected.isInstance(error) && error.getMessage().contains(message)) {
                checks++;
                return;
            }
            throw error;
        }
        throw new AssertionError("Expected rejection: " + message);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Host check failed");
        checks++;
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Supply a fresh test directory");
        File parent = new File(arguments[0]).getCanonicalFile();
        require(parent.mkdir());
        File fresh = new File(parent, "fresh");
        require(root("--host", "--root", fresh.getPath(), "--resources", "resources.zip").equals(fresh));
        require(!fresh.exists());
        require(flags("--host", "--root", fresh.getPath(), "--apk", "resources.zip").containsKey("host"));
        rejected(IllegalArgumentException.class, "--resources requires --host",
            () -> flags("--root", fresh.getPath(), "--resources", "resources.zip"));
        rejected(IllegalArgumentException.class, "only one of --resources and --apk",
            () -> flags("--host", "--resources", "one.zip", "--apk", "two.apk"));
        rejected(IllegalArgumentException.class, "--reuse is not supported",
            () -> root("--host", "--root", fresh.getPath(), "--reuse"));
        rejected(IllegalArgumentException.class, "Duplicate flag --host", () -> flags("--host", "--host"));
        rejected(IllegalArgumentException.class, "Missing value for resources", () -> flags("--host", "--resources"));
        rejected(IllegalArgumentException.class, "dedicated /data/local/tmp",
            () -> root("--root", fresh.getPath(), "--apk", "existing.apk"));
        File existing = new File(parent, "existing");
        require(existing.mkdir());
        File sentinel = new File(existing, "personal-data.txt");
        Files.write(sentinel.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        rejected(IOException.class, "new nonexistent directory", () -> root("--host", "--root", existing.getPath()));
        require(new String(Files.readAllBytes(sentinel.toPath()), StandardCharsets.UTF_8).equals("keep"));
        rejected(IOException.class, "new nonexistent directory", () -> root("--host", "--root", sentinel.getPath()));
        call("createAuditRoot", new Class<?>[]{File.class, boolean.class}, fresh, true);
        require(fresh.isDirectory());
        rejected(IOException.class, "already exists or cannot be created",
            () -> call("createAuditRoot", new Class<?>[]{File.class, boolean.class}, fresh, true));
        require(fresh.list().length == 0);
        System.out.println("Host Java path/flag checks passed: " + checks);
    }
}
