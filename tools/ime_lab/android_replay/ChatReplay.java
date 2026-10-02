package com.cyime.audit;

import com.kingzcheung.xime.rime.RimeEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Standalone JVM/app_process replay, with fresh bundled resources and no access to app data. */
public final class ChatReplay {
    private static final String[] FIELDS = {
        "text", "comment", "type", "quality", "index", "start", "end", "weight", "code", "components"
    };
    private final RimeEngine engine = new RimeEngine();
    private BufferedWriter output;
    private int keyDelayMs;
    private int rowLimit;
    private int boundaryLimit;
    private int settleTimeoutMs;
    private String variant;
    private long rowCount;

    public static void main(String[] args) throws Exception {
        new ChatReplay().run(args);
    }

    private void run(String[] args) throws Exception {
        Map<String, String> flags = flags(args);
        boolean host = flags.containsKey("host");
        File root = auditRoot(flags);
        File apk = new File(required(flags, flags.containsKey("resources") ? "resources" : "apk")).getCanonicalFile();
        File libDir = new File(required(flags, "lib-dir")).getCanonicalFile();
        File corpusFile = new File(required(flags, "corpus")).getCanonicalFile();
        File outputFile = new File(required(flags, "output")).getCanonicalFile();
        if (outputFile.exists()) throw new IOException("Refusing to overwrite output: " + outputFile);
        keyDelayMs = integer(flags, "key-delay-ms", 35, 0, 1000);
        rowLimit = integer(flags, "limit", 20, 1, 500);
        boundaryLimit = integer(flags, "boundary-limit", 100, rowLimit, 500);
        settleTimeoutMs = integer(flags, "settle-timeout-ms", 5000, 1, 60000);
        variant = flags.containsKey("variant") ? flags.get("variant") : "digits";
        if (!Arrays.asList("digits", "separated", "locked").contains(variant)) {
            throw new IllegalArgumentException("Unknown --variant " + variant);
        }
        String[] modes = flags.getOrDefault("modes", "continuous,paused").split(",", -1);
        Set<String> uniqueModes = new HashSet<>();
        for (String mode : modes) {
            if (!Arrays.asList("continuous", "paused", "endpoints").contains(mode)) {
                throw new IllegalArgumentException("Unknown mode " + mode);
            }
            if (!uniqueModes.add(mode)) throw new IllegalArgumentException("Duplicate mode " + mode);
        }
        String ids = flags.getOrDefault("only-ids", flags.getOrDefault("case-ids", ""));
        Set<String> onlyIds = new HashSet<>();
        if (!ids.isEmpty()) {
            for (String id : ids.split(",", -1)) {
                if (id.trim().isEmpty() || !onlyIds.add(id)) {
                    throw new IllegalArgumentException("Empty or duplicate selected case id: " + id);
                }
            }
        }
        JSONTokener parser = new JSONTokener(read(corpusFile));
        Object corpus = parser.nextValue();
        if (parser.nextClean() != 0) throw new IllegalArgumentException("Trailing content after corpus JSON");
        JSONArray cases;
        if (corpus instanceof JSONArray) {
            cases = (JSONArray) corpus;
        } else if (corpus instanceof JSONObject) {
            JSONObject object = (JSONObject) corpus;
            cases = object.optJSONArray("cases");
            if (cases == null) cases = object.getJSONArray("clauses");
        } else throw new IllegalArgumentException("Corpus must be an array or object with cases/clauses");
        validateCases(cases, onlyIds);
        String apkHash = sha256(apk);
        String corpusHash = sha256(corpusFile);
        JSONObject libraryHashes = libraryHashes(libDir);
        createAuditRoot(root, host);
        File user = inside(root, "user");
        File marker = inside(root, "bundled-resources.json");
        JSONObject assets;
        if (flags.containsKey("reuse")) {
            assets = new JSONObject(read(marker));
            if (!apk.getCanonicalPath().equals(assets.getString("apk")) ||
                    apk.length() != assets.getLong("apk_bytes") || !apkHash.equals(assets.optString("apk_sha256")) ||
                    !sameHashes(libraryHashes, assets.optJSONObject("library_sha256")) ||
                    !new File(user, "t9_pinyin.schema.yaml").isFile()) {
                throw new IOException("Audit marker does not match APK/library hashes; choose a fresh root");
            }
        } else {
            if (user.exists() || marker.exists()) {
                throw new IOException("Audit user directory already exists; choose a fresh root or explicit --reuse");
            }
            mkdir(user);
            assets = extract(apk, user).put("apk_sha256", apkHash)
                .put("library_sha256", libraryHashes);
            write(marker, assets.toString(2));
        }
        mkdir(new File(user, "logs"));
        mkdir(outputFile.getCanonicalFile().getParentFile());
        if (!outputFile.createNewFile()) throw new IOException("Refusing to overwrite output: " + outputFile);
        output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8));
        boolean initialized = false;
        try {
            emit(new JSONObject().put("stage", "metadata").put("protocol", 1)
                .put("runtime", host ? "host" : "android")
                .put("os", System.getProperty("os.name")).put("architecture", System.getProperty("os.arch"))
                .put("resources", apk.getCanonicalPath()).put("resources_sha256", apkHash)
                .put("apk", apk.getCanonicalPath()).put("lib_dir", libDir.getCanonicalPath())
                .put("apk_sha256", apkHash).put("library_sha256", libraryHashes)
                .put("corpus_sha256", corpusHash).put("resource_reuse", flags.containsKey("reuse"))
                .put("selected_ids", new JSONArray(Arrays.asList(ids.isEmpty() ? new String[0] : ids.split(","))))
                .put("root", root.getPath()).put("assets", assets).put("variant", variant)
                .put("key_delay_ms", keyDelayMs).put("row_limit", rowLimit)
                .put("boundary_limit", boundaryLimit).put("settle_timeout_ms", settleTimeoutMs)
                .put("modes", new JSONArray(Arrays.asList(modes)))
                .put("scope", "Native engine with isolated audit resources; no app data imported, no UI, commit, or personalization"));
            output.flush();
            System.load(new File(libDir, "librime_jni.so").getAbsolutePath());
            engine.nativeInitialize(user.getPath(), user.getPath());
            initialized = true;
            engine.nativeEnsureT9SchemaPatches("t9_pinyin");
            if (!flags.containsKey("reuse")) {
                System.err.println("DEPLOY starting fresh bundled t9 resources");
                boolean started = engine.nativeStartMaintenance(true);
                long start = System.nanoTime();
                long lastProgress = 0;
                while (engine.nativeIsMaintaining()) {
                    long elapsed = millisSince(start);
                    if (elapsed > 600000) throw new IOException("Rime maintenance exceeded 10 minutes");
                    if (elapsed - lastProgress >= 5000) {
                        System.err.println("DEPLOY waiting " + elapsed + " ms");
                        lastProgress = elapsed;
                    }
                    Thread.sleep(50);
                }
                emit(new JSONObject().put("stage", "deployment").put("started", started)
                    .put("elapsed_ms", millisSince(start)));
            }
            if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("t9_pinyin")) {
                throw new IOException("Could not create t9_pinyin session; inspect audit user/logs");
            }
            engine.nativeSetOption("ascii_mode", false);
            engine.nativeSetOption("full_shape", false);
            JSONObject settings = new JSONObject();
            for (String key : Arrays.asList("t9/dictionary_beam", "t9/sentence_beam", "t9/result_budget",
                    "t9/word_penalty", "t9/spelling_penalty", "t9/sentence_model", "t9/sentence_weight",
                    "translator/contextual_suggestions")) {
                settings.put(key, engine.nativeGetSchemaString("t9_pinyin", key));
            }
            emit(new JSONObject().put("stage", "ready").put("schema", engine.nativeGetCurrentSchema())
                .put("scoring_status", engine.nativeT9ScoringStatus()).put("settings", settings)
                .put("source_schema_sha256", sha256(new File(user, "t9_pinyin.schema.yaml")))
                .put("deployed_schema_sha256", sha256(new File(user, "build/t9_pinyin.schema.yaml")))
                .put("default_custom_sha256", sha256(new File(user, "default.custom.yaml"))));
            output.flush();
            for (String mode : modes) {
                for (int i = 0; i < cases.length(); i++) {
                    JSONObject item = cases.getJSONObject(i);
                    String id = item.getString("id");
                    if (!ids.isEmpty() && !onlyIds.contains(id)) continue;
                    replay(item, id, mode);
                    output.flush();
                    System.err.println("REPLAY " + mode + " " + id + " " + (i + 1) + "/" + cases.length());
                }
            }
            emit(new JSONObject().put("stage", "complete").put("records_before_complete", rowCount)
                .put("scoring_status", engine.nativeT9ScoringStatus()));
        } finally {
            if (initialized) engine.nativeDestroy();
            output.close();
        }
    }

    private void replay(JSONObject item, String id, String mode) throws Exception {
        String text = item.getString("text");
        JSONArray pinyin = item.getJSONArray("pinyin");
        if (text.codePointCount(0, text.length()) != pinyin.length()) {
            throw new IllegalArgumentException(id + ": one pinyin item per target character required");
        }
        String[] syllables = new String[pinyin.length()];
        String[] digits = new String[pinyin.length()];
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < syllables.length; i++) {
            syllables[i] = normalizePinyin(pinyin.getString(i));
            digits[i] = digits(syllables[i]);
            all.append(digits[i]);
        }
        if (item.has("keys") && !all.toString().equals(item.getString("keys"))) {
            throw new IllegalArgumentException(id + ": keys disagree with pinyin");
        }
        engine.nativeT9ClearComposition(1);
        engine.nativeT9FlushRimeInput();
        StringBuilder typed = new StringBuilder();
        StringBuilder sentKeys = new StringBuilder();
        for (int syllable = 0; syllable < syllables.length; syllable++) {
            if (syllable > 0 && variant.equals("separated")) {
                engine.nativeProcessKey('\'', 0);
                engine.nativeT9FlushRimeInput();
                sentKeys.append('\'');
            }
            for (int pos = 0; pos < digits[syllable].length(); pos++) {
                char digit = digits[syllable].charAt(pos);
                long begin = System.nanoTime();
                boolean accepted = engine.nativeProcessKey(digit, 0);
                engine.nativeT9FlushRimeInput();
                long processMs = millisSince(begin);
                typed.append(digit);
                sentKeys.append(digit);
                boolean boundary = pos == digits[syllable].length() - 1;
                int completed = boundary ? syllable + 1 : syllable;
                boolean endpoint = boundary && syllable == syllables.length - 1;
                if (!mode.equals("endpoints") || endpoint) {
                    snapshot(id, mode, "key_immediate", text, typed.toString(), sentKeys.toString(),
                        completed, syllable + 1, boundary, endpoint, accepted, processMs, null);
                }
                if (keyDelayMs > 0) Thread.sleep(keyDelayMs);
            }
            if (variant.equals("locked")) {
                boolean locked = engine.nativeT9SelectPinyinDirect(syllables[syllable], digits[syllable].length());
                engine.nativeT9FlushRimeInput();
                snapshot(id, mode, "pinyin_locked", text, typed.toString(), sentKeys.toString(),
                    syllable + 1, syllable + 1, true, syllable == syllables.length - 1, locked, 0, null);
            }
            boolean endpoint = syllable == syllables.length - 1;
            if (mode.equals("paused") || endpoint) {
                JSONObject wait = settle();
                snapshot(id, mode, endpoint ? "clause_settled" : "syllable_settled", text,
                    typed.toString(), sentKeys.toString(), syllable + 1, syllable + 1,
                    true, endpoint, true, 0, wait);
            }
        }
    }

    private JSONObject settle() throws Exception {
        long start = System.nanoTime();
        int initial = engine.nativeT9RefinementState();
        int state = initial;
        while (state == 1 && millisSince(start) < settleTimeoutMs) {
            Thread.sleep(10);
            state = engine.nativeT9RefinementState();
        }
        boolean refined = state == 2 && engine.nativeRefineT9Sentences();
        return new JSONObject().put("initial_state", initial).put("ready_state", state)
            .put("refined", refined).put("timeout", state == 1)
            .put("wait_ms", millisSince(start)).put("final_state", engine.nativeT9RefinementState());
    }

    private void snapshot(String id, String mode, String stage, String target, String keys, String sentKeys,
                          int completed, int active, boolean boundary, boolean endpoint, boolean accepted,
                          long processMs, JSONObject wait) throws Exception {
        String prefix = target.substring(0, target.offsetByCodePoints(0, completed));
        int stateBeforeInspect = engine.nativeT9RefinementState();
        long inspectStart = System.nanoTime();
        String[][] candidates = engine.nativeInspectCandidates(boundary ? boundaryLimit : rowLimit);
        JSONArray rows = new JSONArray();
        int rank = -1;
        for (int i = 0; i < candidates.length; i++) {
            JSONObject row = new JSONObject();
            for (int j = 0; j < candidates[i].length; j++) {
                row.put(j < FIELDS.length ? FIELDS[j] : "field_" + j, candidates[i][j]);
            }
            if (rank < 0 && boundary && prefix.equals(candidates[i][0])) rank = i + 1;
            rows.put(row);
        }
        JSONObject record = new JSONObject().put("stage", stage).put("id", id).put("mode", mode)
            .put("variant", variant).put("target", target).put("keys", keys).put("sent_keys", sentKeys)
            .put("expected_prefix", prefix).put("char_count", completed).put("active_char", active)
            .put("syllable_boundary", boundary).put("clause_endpoint", endpoint).put("accepted", accepted)
            .put("target_prefix_rank", boundary ? rank : JSONObject.NULL)
            .put("top_exact", boundary && rank == 1).put("rows", rows)
            .put("process_ms", processMs).put("inspect_ms", millisSince(inspectStart))
            .put("state_before_inspect", stateBeforeInspect).put("state", engine.nativeT9RefinementState())
            .put("scoring_status", engine.nativeT9ScoringStatus())
            .put("input", engine.nativeGetInput()).put("remaining_digits", engine.nativeT9GetRemainingDigits())
            .put("wait_ms", wait == null ? 0 : wait.getLong("wait_ms"));
        if (wait != null) record.put("settle", wait);
        emit(record);
    }

    private void emit(JSONObject value) throws Exception {
        output.write(value.toString());
        output.newLine();
        rowCount++;
    }

    private static JSONObject extract(File apk, File user) throws Exception {
        JSONObject result = new JSONObject().put("apk", apk.getCanonicalPath()).put("apk_bytes", apk.length());
        JSONArray verified = new JSONArray();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith("assets/rime/")) {
                    extractEntry(zip, entry, inside(user, entry.getName().substring("assets/rime/".length())), null, -1);
                }
            }
            ZipEntry manifest = zip.getEntry("assets/rime-bundled-manifest.tsv");
            if (manifest == null) throw new IOException("APK has no bundled-resource manifest");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    String[] fields = line.split("\t");
                    if (fields.length != 4) throw new IOException("Malformed bundled-resource manifest");
                    ZipEntry entry = zip.getEntry("assets/" + fields[2]);
                    if (entry == null) throw new IOException("Manifest asset missing: " + fields[2]);
                    extractEntry(zip, entry, inside(user, fields[3]), fields[0], Long.parseLong(fields[1]));
                    verified.put(new JSONObject().put("source", fields[2]).put("destination", fields[3])
                        .put("bytes", Long.parseLong(fields[1])).put("sha256", fields[0]));
                }
            }
            ZipEntry appCustom = zip.getEntry("assets/default.custom.yaml");
            if (appCustom == null) throw new IOException("APK has no app default.custom.yaml");
            File custom = inside(user, "default.custom.yaml");
            extractEntry(zip, appCustom, custom, null, -1);
            String settings = read(custom);
            String adjusted = settings.replaceFirst("(?ms)^  schema_list:\\s*\\n.*?(?=^  [A-Za-z_]|\\z)",
                "  schema_list:\n    - schema: t9_pinyin\n\n");
            if (adjusted.equals(settings)) throw new IOException("Could not restrict audit deployment to t9_pinyin");
            write(custom, adjusted);
        }
        result.put("manifest_verified", verified).put("default_custom_change", "schema_list restricted to t9_pinyin only");
        System.err.println("EXTRACT verified " + verified.length() + " bundled resources");
        return result;
    }

    private static void extractEntry(ZipFile zip, ZipEntry entry, File destination, String hash, long bytes) throws Exception {
        mkdir(destination.getParentFile());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long written = 0;
        try (InputStream input = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                written += count;
            }
        }
        if (bytes >= 0 && written != bytes) throw new IOException("Asset size mismatch: " + entry.getName());
        if (hash != null) {
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (!hash.equals(actual.toString())) throw new IOException("Asset hash mismatch: " + entry.getName());
        }
    }

    private static String digits(String pinyin) {
        StringBuilder result = new StringBuilder();
        for (char letter : pinyin.toCharArray()) {
            if (letter < 'a' || letter > 'z') throw new IllegalArgumentException("Invalid pinyin: " + pinyin);
            result.append("22233344455566677778889999".charAt(letter - 'a'));
        }
        if (result.length() == 0) throw new IllegalArgumentException("Empty pinyin");
        return result.toString();
    }

    private static String normalizePinyin(String value) {
        String result = value.toLowerCase(Locale.ROOT).replace('ü', 'v');
        if (!result.matches("[a-z]+[1-5]?")) {
            throw new IllegalArgumentException("Expected pinyin letters with optional final tone digit: " + value);
        }
        return result.replaceFirst("[1-5]$", "");
    }

    /** Validate every case, including filtered-out cases, before touching the audit directory. */
    private static void validateCases(JSONArray cases, Set<String> selected) throws Exception {
        if (cases.length() == 0) throw new IllegalArgumentException("Corpus contains no cases");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject item = cases.getJSONObject(i);
            Object idValue = item.get("id");
            Object textValue = item.get("text");
            if (!(idValue instanceof String) || ((String) idValue).trim().isEmpty()) {
                throw new IllegalArgumentException("Case " + i + " requires a nonempty string id");
            }
            String id = (String) idValue;
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate case id: " + id);
            if (!(textValue instanceof String) || ((String) textValue).trim().isEmpty()) {
                throw new IllegalArgumentException(id + ": nonempty text required");
            }
            String text = (String) textValue;
            JSONArray pinyin = item.getJSONArray("pinyin");
            if (text.codePointCount(0, text.length()) != pinyin.length()) {
                throw new IllegalArgumentException(id + ": one pinyin item per target character required");
            }
            StringBuilder keys = new StringBuilder();
            for (int j = 0; j < pinyin.length(); j++) {
                Object value = pinyin.get(j);
                if (!(value instanceof String)) throw new IllegalArgumentException(id + ": pinyin must contain strings");
                keys.append(digits(normalizePinyin((String) value)));
            }
            if (item.has("keys") && (!(item.get("keys") instanceof String) || !keys.toString().equals(item.getString("keys")))) {
                throw new IllegalArgumentException(id + ": keys disagree with pinyin");
            }
        }
        for (String id : selected) {
            if (!ids.contains(id)) throw new IllegalArgumentException("Unknown selected case id: " + id);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static JSONObject libraryHashes(File directory) throws Exception {
        if (!new File(directory, "librime_jni.so").isFile()) {
            throw new IOException("Native library directory has no librime_jni.so: " + directory);
        }
        String[] names = directory.list((dir, name) -> name.endsWith(".so"));
        if (names == null) throw new IOException("Could not list native library directory: " + directory);
        Arrays.sort(names);
        JSONObject hashes = new JSONObject();
        for (String name : names) hashes.put(name, sha256(new File(directory, name)));
        return hashes;
    }

    private static boolean sameHashes(JSONObject expected, JSONObject actual) throws Exception {
        if (actual == null || actual.length() != expected.length()) return false;
        java.util.Iterator<String> names = expected.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!expected.getString(name).equals(actual.optString(name))) return false;
        }
        return true;
    }

    private static File inside(File root, String relative) throws IOException {
        File result = new File(root, relative).getCanonicalFile();
        if (!result.getPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("Path escapes audit directory: " + relative);
        }
        return result;
    }

    private static File auditRoot(Map<String, String> flags) throws IOException {
        File root = new File(required(flags, "root")).getCanonicalFile();
        if (flags.containsKey("host")) {
            if (flags.containsKey("reuse")) {
                throw new IllegalArgumentException("--host requires fresh resources; --reuse is not supported");
            }
            if (root.exists()) {
                throw new IOException("Host audit root must be a new nonexistent directory: " + root);
            }
        } else if (!root.getPath().startsWith("/data/local/tmp/cyime-chat-audit-")) {
            throw new IllegalArgumentException("--root must be a dedicated /data/local/tmp/cyime-chat-audit-* directory");
        }
        return root;
    }

    private static void createAuditRoot(File root, boolean host) throws IOException {
        if (host) {
            mkdir(root.getParentFile());
            // A concurrent creator must not turn a checked fresh root into shared state.
            if (!root.mkdir()) throw new IOException("Host audit root already exists or cannot be created: " + root);
        } else mkdir(root);
    }

    private static Map<String, String> flags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        Set<String> allowed = new HashSet<>(Arrays.asList("root", "apk", "resources", "host", "lib-dir", "corpus", "output",
            "key-delay-ms", "limit", "boundary-limit", "settle-timeout-ms", "variant", "modes",
            "only-ids", "case-ids", "reuse"));
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected --flag: " + args[i]);
            String key = args[i].substring(2);
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown flag --" + key);
            if (flags.containsKey(key)) throw new IllegalArgumentException("Duplicate flag --" + key);
            if (key.equals("reuse") || key.equals("host")) flags.put(key, "true");
            else {
                if (++i >= args.length || args[i].isEmpty() || args[i].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + key);
                }
                flags.put(key, args[i]);
            }
        }
        if (flags.containsKey("only-ids") && flags.containsKey("case-ids")) {
            throw new IllegalArgumentException("Use only one of --only-ids and --case-ids");
        }
        if (flags.containsKey("resources") && !flags.containsKey("host")) {
            throw new IllegalArgumentException("--resources requires --host");
        }
        if (flags.containsKey("resources") && flags.containsKey("apk")) {
            throw new IllegalArgumentException("Use only one of --resources and --apk");
        }
        return flags;
    }

    private static String required(Map<String, String> flags, String name) {
        String result = flags.get(name);
        if (result == null || result.isEmpty()) throw new IllegalArgumentException("Missing --" + name);
        return result;
    }

    private static int integer(Map<String, String> flags, String name, int fallback, int min, int max) {
        int value = flags.containsKey(name) ? Integer.parseInt(flags.get(name)) : fallback;
        if (value < min || value > max) throw new IllegalArgumentException("Invalid --" + name);
        return value;
    }

    private static long millisSince(long start) { return (System.nanoTime() - start) / 1000000; }

    private static void mkdir(File file) throws IOException {
        if (!file.isDirectory() && !file.mkdirs()) throw new IOException("Could not create " + file);
    }

    private static String read(File file) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStreamReader input = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int count;
            while ((count = input.read(buffer)) != -1) result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static void write(File file, String value) throws IOException {
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            out.write(value);
        }
    }
}
