package com.cyime.audit;

import com.kingzcheung.xime.rime.RimeEngine;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Left-context check for T9 through the host JNI (cases from chunk_cases.py):
 *  - whole clause typed with no context vs. at a clause start (empty left context);
 *  - second chunk typed with no context vs. after the first chunk as committed text.
 * Usage: ChunkReplay LIB_DIR USER_DIR OUTPUT.jsonl CASES.json
 */
public final class ChunkReplay {
    private static int rank(RimeEngine engine, String keys, String target) {
        engine.nativeSetInput(keys);
        String[][] candidates = engine.nativeInspectCandidates(20);
        engine.nativeSetInput("");
        for (int k = 0; k < candidates.length; k++) if (target.equals(candidates[k][0])) return k + 1;
        return -1;
    }

    public static void main(String[] args) throws Exception {
        System.load(new File(args[0], "librime_jni.so").getAbsolutePath());
        String user = new File(args[1]).getCanonicalPath();
        RimeEngine engine = new RimeEngine();
        engine.nativeInitialize(user, user);
        if (engine.nativeStartMaintenance(true)) {
            while (engine.nativeIsMaintaining()) Thread.sleep(200);
        }
        if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema("t9_pinyin")) {
            throw new IllegalStateException("cannot select t9_pinyin");
        }
        JSONArray cases = new JSONObject(new String(Files.readAllBytes(Paths.get(args[3])), StandardCharsets.UTF_8))
            .getJSONArray("cases");
        String[] modes = {"whole_none", "whole_start", "right_none", "right_context"};
        int[] top1 = new int[modes.length];
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[2]), StandardCharsets.UTF_8))) {
            for (int i = 0; i < cases.length(); i++) {
                JSONObject c = cases.getJSONObject(i);
                int[] ranks = new int[modes.length];
                engine.nativeSetT9LeftContext(null);
                ranks[0] = rank(engine, c.getString("keys"), c.getString("text"));
                engine.nativeSetT9LeftContext("");
                ranks[1] = rank(engine, c.getString("keys"), c.getString("text"));
                engine.nativeSetT9LeftContext(null);
                ranks[2] = rank(engine, c.getString("right_keys"), c.getString("right"));
                engine.nativeSetT9LeftContext(c.getString("left"));
                ranks[3] = rank(engine, c.getString("right_keys"), c.getString("right"));
                engine.nativeSetT9LeftContext(null);
                JSONObject row = new JSONObject().put("fixture", c.getString("fixture")).put("id", c.getString("id"))
                    .put("text", c.getString("text")).put("left", c.getString("left"));
                for (int m = 0; m < modes.length; m++) {
                    row.put(modes[m], ranks[m]);
                    if (ranks[m] == 1) top1[m]++;
                }
                out.println(row);
            }
        }
        JSONObject summary = new JSONObject().put("cases", cases.length());
        for (int m = 0; m < modes.length; m++) summary.put(modes[m], top1[m]);
        System.out.println(summary);
        engine.nativeDestroy();
    }
}
