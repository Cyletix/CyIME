package com.cyime.audit;

import com.kingzcheung.xime.rime.RimeEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.BufferedWriter;
import java.util.Iterator;

/** Endpoint-only, zero-delay configuration diagnostic; not the full replay protocol. */
public final class WeightExperiment {
    private static final String[] FIELDS = {"text", "comment", "type", "quality", "index", "start", "end", "weight", "code", "components"};
    private final RimeEngine engine = new RimeEngine();
    private BufferedWriter out;
    private static double ms(long start) { return (System.nanoTime() - start) / 1000000.0; }
    private void emit(JSONObject value) throws Exception { out.write(value.toString()); out.newLine(); out.flush(); }
    public static void main(String[] args) throws Exception { new WeightExperiment().run(args); }
    private void run(String[] args) throws Exception {
        if (args.length != 5 && args.length != 6)
            throw new IllegalArgumentException("lib-dir user-dir cases.json output.jsonl expected-settings.json [nine-key-schema]");
        String schema = args.length == 6 ? args[5] : "t9_pinyin";
        if (Files.exists(Paths.get(args[3]))) throw new IllegalArgumentException("Output already exists");
        JSONObject expected = new JSONObject(new String(Files.readAllBytes(Paths.get(args[4])), StandardCharsets.UTF_8));
        JSONArray cases = new JSONArray(new String(Files.readAllBytes(Paths.get(args[2])), StandardCharsets.UTF_8));
        out = Files.newBufferedWriter(Paths.get(args[3]), StandardCharsets.UTF_8);
        boolean initialized = false;
        try {
            System.load(Paths.get(args[0], "librime_jni.so").toAbsolutePath().toString());
            engine.nativeInitialize(args[1], args[1]); initialized = true;
            if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema(schema)) throw new IllegalStateException("No session for " + schema);
            engine.nativeSetOption("ascii_mode", false); engine.nativeSetOption("full_shape", false);
            boolean idleSpelling = engine.nativeGetSchemaString(schema, "cyime_edit_schema") != null;
            JSONObject actual = new JSONObject();
            for (Iterator<String> keys = expected.keys(); keys.hasNext();) {
                String key = keys.next();
                String value = engine.nativeGetSchemaString(schema, "t9/" + key);
                actual.put(key, value);
                if (Math.abs(Double.parseDouble(value) - expected.getDouble(key)) > 1e-9) throw new IllegalStateException("Setting not active: " + key + "=" + value);
            }
            emit(new JSONObject().put("stage", "ready").put("schema", schema).put("settings", actual).put("scoring_status", engine.nativeT9ScoringStatus())
                .put("scope", "zero-delay endpoint-only diagnostic; fresh user; no commit or learning; no redeploy"));
            int checked = 0;
            JSONArray failures = new JSONArray();
            for (int i = 0; i < cases.length(); ++i) {
                JSONObject item = cases.getJSONObject(i);
                if (!schema.equals(item.optString("schema", "t9_pinyin"))) continue;
                ++checked;
                String keys = item.getString("keys"), target = item.getString("text");
                engine.nativeT9ClearComposition(1); engine.nativeT9FlushRimeInput();
                JSONArray processMs = new JSONArray();
                for (int n = 0; n < keys.length(); ++n) {
                    long keyStart = System.nanoTime();
                    boolean accepted = engine.nativeProcessKey(keys.charAt(n), 0); engine.nativeT9FlushRimeInput();
                    processMs.put(ms(keyStart));
                    if (!accepted) throw new IllegalStateException(item.getString("id") + ": key rejected");
                }
                String input = engine.nativeGetInput(), remaining = engine.nativeT9GetRemainingDigits();
                if (!keys.equals(input) || !keys.equals(remaining)) throw new IllegalStateException("Input mismatch: " + item.getString("id"));
                JSONObject immediate = snapshot(target);
                long waitStart = System.nanoTime();
                int initial = engine.nativeT9RefinementState(), state = initial;
                while (state == 1 && ms(waitStart) < 5000) { Thread.sleep(1); state = engine.nativeT9RefinementState(); }
                double readyWaitMs = ms(waitStart);
                long refineStart = System.nanoTime();
                boolean refined = state == 2 && engine.nativeRefineT9Sentences();
                boolean spelling = idleSpelling && state != 1 && engine.nativeRefineOmission();
                double refineMs = ms(refineStart);
                JSONObject settled = snapshot(target);
                if (item.has("top_k")) {
                    int limit = item.getInt("top_k");
                    int rank = settled.getInt("rank");
                    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid top_k for " + item.getString("id"));
                    if (state == 1 || rank < 1 || rank > limit) failures.put(item.getString("id"));
                }
                emit(new JSONObject().put("stage", "case").put("id", item.getString("id")).put("group", item.getString("group"))
                    .put("target", target).put("keys", keys).put("input", input).put("remaining_digits", remaining)
                    .put("process_ms", processMs).put("immediate", immediate).put("settled", settled)
                    .put("initial_state", initial).put("ready_state", state).put("timeout", state == 1)
                    .put("refined", refined).put("spelling_refined", spelling).put("ready_wait_ms", readyWaitMs).put("refine_ms", refineMs)
                    .put("scoring_status", engine.nativeT9ScoringStatus()));
            }
            if (checked == 0) throw new IllegalArgumentException("No cases for " + schema);
            emit(new JSONObject().put("stage", "complete").put("cases", checked).put("failed", failures));
            if (failures.length() != 0) throw new AssertionError("Failed candidate expectations: " + failures);
        } finally { if (initialized) engine.nativeDestroy(); out.close(); }
    }
    private JSONObject snapshot(String target) throws org.json.JSONException {
        long start = System.nanoTime(); String[][] candidates = engine.nativeInspectCandidates(100); double elapsed = ms(start);
        JSONArray rows = new JSONArray(); int rank = -1;
        for (int i = 0; i < candidates.length; ++i) {
            JSONObject row = new JSONObject();
            for (int j = 0; j < candidates[i].length; ++j) row.put(j < FIELDS.length ? FIELDS[j] : "field_" + j, candidates[i][j]);
            if (rank < 0 && target.equals(candidates[i][0])) rank = i + 1;
            rows.put(row);
        }
        return new JSONObject().put("rank", rank).put("first", candidates.length == 0 ? "" : candidates[0][0])
            .put("rows", rows).put("inspect_ms", elapsed).put("state", engine.nativeT9RefinementState());
    }
}
