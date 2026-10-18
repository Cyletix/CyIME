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
 * Full-pinyin (26-key) first-candidate check through the host JNI, for comparison with T9.
 * Each fixture clause is typed as its whole pinyin without separators, as people type it.
 * Usage: PinyinReplay LIB_DIR USER_DIR SCHEMA OUTPUT.jsonl FIXTURE.json [FIXTURE.json ...]
 */
public final class PinyinReplay {
    public static void main(String[] args) throws Exception {
        File libDir = new File(args[0]);
        String user = new File(args[1]).getCanonicalPath();
        String schema = args[2];
        System.load(new File(libDir, "librime_jni.so").getAbsolutePath());
        RimeEngine engine = new RimeEngine();
        engine.nativeInitialize(user, user);
        if (engine.nativeStartMaintenance(true)) {
            while (engine.nativeIsMaintaining()) Thread.sleep(200);
        }
        if (!engine.nativeCreateSession() || !engine.nativeSwitchSchema(schema)) {
            throw new IllegalStateException("cannot select schema " + schema);
        }
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[3]), StandardCharsets.UTF_8))) {
            for (int f = 4; f < args.length; f++) {
                JSONObject fixture = new JSONObject(new String(Files.readAllBytes(Paths.get(args[f])), StandardCharsets.UTF_8));
                JSONArray cases = fixture.getJSONArray("cases");
                int top1 = 0, top5 = 0;
                for (int i = 0; i < cases.length(); i++) {
                    JSONObject c = cases.getJSONObject(i);
                    String text = c.getString("text");
                    JSONArray pinyin = c.getJSONArray("pinyin");
                    StringBuilder letters = new StringBuilder();
                    for (int k = 0; k < pinyin.length(); k++) letters.append(pinyin.getString(k).replace("ü", "v"));
                    engine.nativeSetInput(letters.toString());
                    String[][] candidates = engine.nativeInspectCandidates(20);
                    JSONArray texts = new JSONArray();
                    int rank = -1;
                    for (int k = 0; k < candidates.length; k++) {
                        texts.put(candidates[k][0]);
                        if (rank < 0 && text.equals(candidates[k][0])) rank = k + 1;
                    }
                    if (rank == 1) top1++;
                    if (rank >= 1 && rank <= 5) top5++;
                    out.println(new JSONObject().put("fixture", args[f]).put("id", c.getString("id")).put("text", text)
                        .put("input", letters.toString()).put("rank", rank).put("candidates", texts));
                    engine.nativeSetInput("");
                }
                System.out.println(new JSONObject().put("fixture", args[f]).put("schema", schema)
                    .put("clauses", cases.length()).put("top1", top1).put("top5", top5));
            }
        }
        engine.nativeDestroy();
    }
}
