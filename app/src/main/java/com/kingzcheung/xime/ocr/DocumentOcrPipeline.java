/*
 * DB detection, perspective crops and CTC decoding adapted from PaddleOCR / RapidOCR.
 * Copyright (c) 2020-2026 PaddlePaddle Authors. Licensed under Apache-2.0.
 * CyIME changes: bounded strip resolution, single-crop inference, shared Android/desktop
 * implementation and spatial text assembly. See assets/licenses/ocr/PP-OCR-NOTICE.txt.
 */
package com.kingzcheung.xime.ocr;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** No Android dependencies: the desktop regression executes this exact production pipeline. */
public final class DocumentOcrPipeline implements AutoCloseable {
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession detection;
    private final OrtSession recognition;
    private final String[] alphabet;

    public DocumentOcrPipeline(String detPath, String recPath, String alphabetPath) throws OrtException, IOException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            options.addConfigEntry("session.intra_op.allow_spinning", "0");
            options.addConfigEntry("session.inter_op.allow_spinning", "0");
            options.setMemoryPatternOptimization(false);
            detection = environment.createSession(detPath, options);
            OrtSession rec = null;
            try {
                rec = environment.createSession(recPath, options);
                List<String> chars = new ArrayList<>();
                chars.add(""); // CTC blank
                // Read UTF-8 directly: ORT's JNI metadata string conversion truncates this
                // model's supplementary-plane characters and shifts all subsequent CTC IDs.
                chars.addAll(Files.readAllLines(Paths.get(alphabetPath), StandardCharsets.UTF_8));
                chars.add(" ");
                alphabet = chars.toArray(new String[0]);
                recognition = rec;
            } catch (Exception error) {
                if (rec != null) rec.close();
                detection.close();
                throw error;
            }
        }
    }

    public static final class TextRegion {
        public final Point[] points;
        public final String text;
        public final float confidence;
        TextRegion(Point[] points, String text, float confidence) {
            this.points = points; this.text = text; this.confidence = confidence;
        }
        double centerY() { return Arrays.stream(points).mapToDouble(p -> p.y).average().orElse(0); }
        double height() { return Math.max(distance(points[0], points[3]), distance(points[1], points[2])); }
        OcrTableLayout.Box box() {
            double[] x = new double[4], y = new double[4];
            for (int i = 0; i < 4; i++) { x[i] = points[i].x; y[i] = points[i].y; }
            return new OcrTableLayout.Box(x, y, text);
        }
    }

    /** Input is the already-cropped, fixed camera/gallery frame in BGR order. */
    public List<TextRegion> recognize(Mat bgr) throws OrtException {
        return recognize(bgr, true, 64);
    }

    /**
     * The same recognition laid out as a table. Every cell is its own box, so more boxes are read than
     * for a text strip; ruled lines are found by projection after the text has been leveled.
     */
    public String recognizeTable(Mat bgr, OcrTableLayout.Format format) throws OrtException {
        List<TextRegion> regions = splitWideGaps(bgr, recognize(bgr, true, 160));
        if (regions.isEmpty()) return "";
        List<OcrTableLayout.Box> boxes = new ArrayList<>();
        for (TextRegion region : regions) boxes.add(region.box());
        double[] level = OcrTableLayout.straighten(OcrTableLayout.skewDegrees(boxes), bgr.cols(), bgr.rows());
        List<OcrTableLayout.Box> leveled = new ArrayList<>();
        for (OcrTableLayout.Box box : boxes) leveled.add(box.transformed(level));
        double text = Math.max(8, OcrTableLayout.medianHeight(leveled));
        // Opening with a run longer than any glyph stroke keeps only ruling: three text heights across,
        // two and a half down (a one-row table's vertical lines are barely taller than its text).
        int across = (int)Math.round(text * 3), down = (int)Math.round(text * 2.5);
        int[][] profiles = ruleProfiles(bgr, level, across, down);
        double[] rows = OcrTableLayout.lines(profiles[0], across, text * .5);
        double[] columns = OcrTableLayout.lines(profiles[1], down, text * .5);
        if (Boolean.getBoolean("cyime.ocr.diagnostics"))
            System.err.println("table rows=" + Arrays.toString(rows) + " columns=" + Arrays.toString(columns));
        return OcrTableLayout.format(OcrTableLayout.grid(leveled, rows, columns), format);
    }

    /**
     * The detector joins neighbouring cells less than about a character apart into one box ("单价备注").
     * Inside a long box, a blank vertical band wider than half the box height is a cell gap, never a
     * word space: split there and read each side again. A box keeps its reading unless every side reads.
     */
    private List<TextRegion> splitWideGaps(Mat bgr, List<TextRegion> regions) throws OrtException {
        List<TextRegion> result = new ArrayList<>();
        int budget = 48; // extra recognizer passes for one frame
        for (TextRegion region : regions) {
            Point[] p = region.points;
            if (budget <= 0 || Math.max(distance(p[0], p[1]), distance(p[3], p[2])) < region.height() * 2.5) {
                result.add(region); continue;
            }
            Mat crop = crop(bgr, p), gray = new Mat(), ink = new Mat(), sums = new Mat();
            try {
                Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY);
                boolean light = Core.mean(gray).val[0] >= 128;
                Imgproc.threshold(gray, ink, 0, 255, (light ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY) | Imgproc.THRESH_OTSU);
                Core.reduce(ink, sums, 0, Core.REDUCE_SUM, CvType.CV_32S);
                int[] columns = new int[(int)sums.total()];
                sums.get(0, 0, columns);
                for (int i = 0; i < columns.length; i++) columns[i] /= 255;
                int[][] gaps = OcrTableLayout.blankRuns(columns, Math.max(1, crop.rows() / 25), (int)Math.round(crop.rows() * .5));
                if (Boolean.getBoolean("cyime.ocr.diagnostics") && gaps.length > 0)
                    System.err.println("split " + region.text + " at " + Arrays.deepToString(gaps) + " of " + crop.cols());
                // Each side keeps a little of the gap as margin for the recognizer; the rest stays a gap
                // between the new boxes, so the columns can still be found from them.
                int margin = Math.max(1, crop.rows() / 10);
                List<TextRegion> parts = new ArrayList<>();
                for (int i = 0; i <= gaps.length && gaps.length > 0; i++) {
                    int left = i == 0 ? 0 : gaps[i - 1][1] - margin;
                    int right = i == gaps.length ? crop.cols() : gaps[i][0] + margin;
                    Mat piece = crop.submat(0, crop.rows(), left, Math.max(left + 1, right));
                    Line line;
                    try { line = read(piece); } finally { piece.release(); }
                    budget--;
                    if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println("  piece " + line.text + " " + line.confidence);
                    // Edge clutter beside a word reads as a lone bracket or dash: a symbol-only side must be sure.
                    if (!line.accepted() || (line.confidence < .9 && line.text.codePoints().noneMatch(Character::isLetterOrDigit))) {
                        parts.clear(); break;
                    }
                    double from = (double)left / crop.cols(), to = (double)right / crop.cols();
                    parts.add(new TextRegion(new Point[]{along(p[0], p[1], from), along(p[0], p[1], to),
                            along(p[3], p[2], to), along(p[3], p[2], from)}, line.text, line.confidence));
                }
                if (parts.size() >= 2) result.addAll(parts); else result.add(region);
            } finally { sums.release(); ink.release(); gray.release(); crop.release(); }
        }
        return result;
    }

    private static Point along(Point a, Point b, double t) { return new Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t); }

    /**
     * Horizontal and vertical line pixels per image row and per image column. Strokes darker than a
     * light page (lighter than a dark one) are thresholded locally, so faint spreadsheet gridlines count.
     */
    private static int[][] ruleProfiles(Mat bgr, double[] level, int across, int down) {
        Mat source = new Mat(), gray = new Mat(), binary = new Mat(), lines = new Mat(), sums = new Mat();
        Mat affine = new Mat(2, 3, CvType.CV_64F);
        try {
            Imgproc.cvtColor(bgr, source, Imgproc.COLOR_BGR2GRAY);
            if (level[1] == 0) source.copyTo(gray);
            else {
                affine.put(0, 0, level);
                Imgproc.warpAffine(source, gray, affine, source.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE);
            }
            boolean lightSurface = Core.mean(gray).val[0] >= 128;
            int block = Math.max(15, Math.min(gray.rows(), gray.cols()) / 20) | 1;
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    lightSurface ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY, block, 10);
            int[][] profiles = new int[2][];
            for (int axis = 0; axis < 2; axis++) {
                Size run = axis == 0 ? new Size(Math.max(3, across), 1) : new Size(1, Math.max(3, down));
                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, run);
                try { Imgproc.morphologyEx(binary, lines, Imgproc.MORPH_OPEN, kernel); } finally { kernel.release(); }
                Core.reduce(lines, sums, axis == 0 ? 1 : 0, Core.REDUCE_SUM, CvType.CV_32S);
                int[] profile = new int[(int)sums.total()];
                sums.get(0, 0, profile);
                for (int i = 0; i < profile.length; i++) profile[i] /= 255;
                profiles[axis] = profile;
            }
            return profiles;
        } finally { affine.release(); sums.release(); lines.release(); binary.release(); gray.release(); source.release(); }
    }

    private List<TextRegion> recognize(Mat bgr, boolean refineTiltedRegion, int maxBoxes) throws OrtException {
        if (bgr.empty() || bgr.type() != CvType.CV_8UC3) throw new IllegalArgumentException("Expected BGR image");
        // A rotated crop has a taller background without larger glyphs. Preserve a useful
        // long-side resolution too; sizing only by strip height loses those small letters.
        double scale = Math.max(1, Math.max(320.0 / Math.min(bgr.rows(), bgr.cols()),
                2048.0 / Math.max(bgr.rows(), bgr.cols())));
        scale = Math.min(scale, 2560.0 / Math.max(bgr.rows(), bgr.cols()));
        scale = Math.min(scale, Math.sqrt(1_048_576.0 / ((double)bgr.rows() * bgr.cols())));
        int height = multiple32(bgr.rows() * scale), width = multiple32(bgr.cols() * scale);
        TensorOutput prediction = run(detection, normalize(bgr, width, height, width, true),
                new long[]{1, 3, height, width});
        List<Point[]> boxes = detect(prediction, bgr.cols(), bgr.rows());
        if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println("boxes=" + boxes.size());
        List<TextRegion> text = new ArrayList<>();
        boolean refined = false;
        // Bound work in one frame; this process never queues OCR alongside IME key events.
        for (Point[] box : boxes.subList(0, Math.min(maxBoxes, boxes.size()))) {
            Mat crop = crop(bgr, box);
            try {
                Line line = read(crop);
                String result = line.text;
                if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println(Arrays.toString(box) + " => " + result + " " + line.confidence);
                if (line.accepted()) {
                    text.add(new TextRegion(box, result, line.confidence));
                } else if (refineTiltedRegion && !refined && crop.cols() > crop.rows() * 3 &&
                        Math.abs(box[1].y - box[0].y) > distance(box[0], box[1]) * .04) {
                    // A sloping background can merge separated text into one large detection.
                    // Retry detection once inside that rectified region, never scan outside the frame.
                    refined = true;
                    List<TextRegion> parts = recognize(crop, false, maxBoxes);
                    if (!parts.isEmpty()) {
                        float score = (float)parts.stream().mapToDouble(p -> p.confidence).average().orElse(0);
                        text.add(new TextRegion(box, assemble(parts), score));
                    }
                }
            } finally { crop.release(); }
        }
        if (refineTiltedRegion) {
            List<Point[]> read = new ArrayList<>();
            for (TextRegion region : text) read.add(region.points);
            recoverIsolatedMarks(bgr, read, text);
        }
        return readingOrder(text);
    }

    private static final class Line {
        final String text; final float confidence;
        Line(String text, float confidence) { this.text = text; this.confidence = confidence; }
        boolean accepted() { return !text.isEmpty() && confidence >= .65f; }
    }

    private Line read(Mat crop) throws OrtException {
        int contentWidth = Math.max(1, Math.min(1600, (int)Math.ceil(48.0 * crop.cols() / crop.rows())));
        int tensorWidth = Math.max(320, contentWidth);
        TensorOutput output = run(recognition, normalize(crop, contentWidth, 48, tensorWidth, false),
                new long[]{1, 3, 48, tensorWidth});
        if (output.shape.length != 3 || output.shape[2] != alphabet.length) {
            throw new IllegalArgumentException("OCR alphabet " + alphabet.length + " does not match output " + Arrays.toString(output.shape));
        }
        StringBuilder value = new StringBuilder();
        float confidence = 0;
        int count = 0, previous = -1, classes = alphabet.length;
        for (int t = 0; t < output.shape[1]; t++) {
            int best = 0, offset = t * classes;
            for (int c = 1; c < classes; c++) if (output.data[offset + c] > output.data[offset + best]) best = c;
            if (best != 0 && best != previous) {
                value.append(alphabet[best]); confidence += output.data[offset + best]; count++;
            }
            previous = best;
        }
        return new Line(value.toString().trim(), count > 0 ? confidence / count : 0);
    }

    /**
     * PP-OCR's DB detector is trained on text lines and gives no box to lone marks such as the
     * "<", "," legends on keycaps, although the recognizer reads them inside a line. Find small
     * high-contrast marks that no accepted text covers and add the symbols read for them.
     */
    private void recoverIsolatedMarks(Mat bgr, List<Point[]> boxes, List<TextRegion> text) throws OrtException {
        int rows = bgr.rows(), cols = bgr.cols();
        List<Rect> marks = new ArrayList<>();
        int k = Math.max(9, (int)(Math.min(rows, cols) * .3)) | 1;
        Mat gray = new Mat(), map = new Mat(), otsu = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(k, k));
        Mat labels = new Mat(), stats = new Mat(), centroids = new Mat();
        try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            // Thin strokes darker than a light surface (or lighter than a dark one). Only one
            // polarity: the other turns whole key faces around a legend into false marks.
            MatOfDouble mean = new MatOfDouble(), deviation = new MatOfDouble();
            try { Core.meanStdDev(gray, mean, deviation); } finally { deviation.release(); }
            boolean lightSurface = mean.get(0, 0)[0] >= 128;
            mean.release();
            Imgproc.morphologyEx(gray, map, lightSurface ? Imgproc.MORPH_BLACKHAT : Imgproc.MORPH_TOPHAT, kernel);
            double level = Imgproc.threshold(map, otsu, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            Imgproc.threshold(map, map, Math.max(48, level), 255, Imgproc.THRESH_BINARY);
            int count = Imgproc.connectedComponentsWithStats(map, labels, stats, centroids, 8, CvType.CV_32S);
            for (int i = 1; i < Math.min(count, 400); i++) {
                int x = (int)stats.get(i, Imgproc.CC_STAT_LEFT)[0], y = (int)stats.get(i, Imgproc.CC_STAT_TOP)[0];
                int w = (int)stats.get(i, Imgproc.CC_STAT_WIDTH)[0], h = (int)stats.get(i, Imgproc.CC_STAT_HEIGHT)[0];
                // Glyph-sized only: keycap edges are thin slivers and sensor specks a few pixels,
                // and each surviving row costs one more recognizer pass on the device.
                if (h < Math.max(5, rows * .05) || h > rows * .45 || w > cols * .25 || w > h * 4 || h > w * 8) continue;
                if (x == 0 || y == 0 || x + w >= cols || y + h >= rows) continue;
                Point center = new Point(x + w / 2.0, y + h / 2.0);
                boolean covered = false;
                for (Point[] box : boxes) {
                    MatOfPoint2f polygon = new MatOfPoint2f(box);
                    try { covered = Imgproc.pointPolygonTest(polygon, center, false) >= 0; } finally { polygon.release(); }
                    if (covered) break;
                }
                if (!covered) marks.add(new Rect(x, y, w, h));
            }
        } finally {
            kernel.release(); centroids.release(); stats.release(); labels.release();
            otsu.release(); map.release(); gray.release();
        }
        if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println("isolated marks=" + marks);
        // A lone glyph has no line context ("<" reads as "く", ">" as "V"). Read each row of marks
        // together with the detected words beside it, and keep only the symbols the detector
        // missed: detected text is never re-read, replaced or duplicated.
        marks.sort(Comparator.comparingDouble(r -> r.y + r.height / 2.0));
        int rowsRead = 0;
        while (!marks.isEmpty() && rowsRead++ < 2) {
            Rect seed = marks.remove(0);
            List<Rect> row = new ArrayList<>(); row.add(seed);
            int top = seed.y, bottom = seed.y + seed.height;
            for (int i = 0; i < marks.size();) {
                Rect r = marks.get(i);
                double center = r.y + r.height / 2.0, slack = (bottom - top) * .5;
                if (center >= top - slack && center <= bottom + slack) {
                    row.add(r); marks.remove(i);
                    top = Math.min(top, r.y); bottom = Math.max(bottom, r.y + r.height);
                } else i++;
            }
            row.sort(Comparator.comparingInt(r -> r.x));
            int left = row.get(0).x, right = row.get(row.size() - 1).x + row.get(row.size() - 1).width;
            double slack = (bottom - top) * .5;
            StringBuilder known = new StringBuilder();
            for (TextRegion region : text) {
                double center = region.centerY();
                if (center < top - slack || center > bottom + slack) continue;
                known.append(region.text);
                // Words widen the line; its height stays the marks' own, since detected boxes
                // are padded and can be tilted or merged with a keycap edge.
                for (Point p : region.points) {
                    left = Math.min(left, (int)p.x); right = Math.max(right, (int)Math.ceil(p.x));
                }
            }
            int pad = Math.max(2, (int)Math.round((bottom - top) * .6));
            Rect band = new Rect(new Point(Math.max(0, left - pad), Math.max(0, top - pad)),
                    new Point(Math.min(cols, right + pad), Math.min(rows, bottom + pad)));
            if (band.width < 4 || band.height < 4) continue;
            Mat crop = bgr.submat(band);
            Line line;
            try { line = read(crop); } finally { crop.release(); }
            if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println("row " + band + " => " + line.text + " " + line.confidence);
            if (!line.accepted()) continue;
            String missed = missedSymbols(line.text, known.toString());
            if (missed.isEmpty()) continue;
            // One region per mark when the counts agree, so reading order follows the image.
            int[] symbols = missed.codePoints().toArray();
            if (symbols.length == row.size()) {
                for (int i = 0; i < symbols.length; i++) text.add(markRegion(row.get(i), new String(symbols, i, 1), line.confidence));
            } else {
                Rect all = new Rect(new Point(row.get(0).x, Math.min(row.get(0).y, row.get(row.size() - 1).y)),
                        new Point(right, Math.max(row.get(0).y + row.get(0).height, row.get(row.size() - 1).y + row.get(row.size() - 1).height)));
                text.add(markRegion(all, missed, line.confidence));
            }
        }
    }

    /** Symbols in the row reading that the detected words of that row do not already contain. */
    static String missedSymbols(String reading, String known) {
        // "？" in the row reading is the same mark as a detected "?".
        Map<String, Integer> have = new HashMap<>();
        known.codePoints().filter(DocumentOcrPipeline::isSymbol).forEach(c -> have.merge(width(c), 1, Integer::sum));
        StringBuilder missed = new StringBuilder();
        reading.codePoints().filter(DocumentOcrPipeline::isSymbol).forEach(c -> {
            if (have.getOrDefault(width(c), 0) > 0) have.merge(width(c), -1, Integer::sum);
            else missed.appendCodePoint(c);
        });
        return missed.toString();
    }

    private static String width(int c) { return Normalizer.normalize(new String(Character.toChars(c)), Normalizer.Form.NFKC); }

    private static boolean isSymbol(int c) { return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c); }

    private static TextRegion markRegion(Rect r, String text, float confidence) {
        return new TextRegion(new Point[]{new Point(r.x, r.y), new Point(r.x + r.width, r.y),
                new Point(r.x + r.width, r.y + r.height), new Point(r.x, r.y + r.height)}, text, confidence);
    }

    public static String assemble(List<TextRegion> regions) {
        StringBuilder result = new StringBuilder();
        TextRegion previous = null;
        for (TextRegion region : regions) {
            if (previous != null) result.append(sameLine(previous, region) ? ' ' : '\n');
            result.append(region.text);
            previous = region;
        }
        return result.toString();
    }

    private static List<TextRegion> readingOrder(List<TextRegion> regions) {
        regions.sort(Comparator.comparingDouble(TextRegion::centerY));
        List<TextRegion> ordered = new ArrayList<>();
        while (!regions.isEmpty()) {
            TextRegion anchor = regions.remove(0);
            List<TextRegion> line = new ArrayList<>(); line.add(anchor);
            for (int i = 0; i < regions.size();) {
                if (sameLine(anchor, regions.get(i))) line.add(regions.remove(i)); else i++;
            }
            line.sort(Comparator.comparingDouble(r -> r.points[0].x));
            ordered.addAll(line);
        }
        return ordered;
    }

    private static boolean sameLine(TextRegion a, TextRegion b) {
        return Math.abs(a.centerY() - b.centerY()) < Math.max(a.height(), b.height()) * .65;
    }

    private static int multiple32(double size) { return Math.max(32, (int)Math.rint(size / 32) * 32); }
    private static double distance(Point a, Point b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    private static float[] normalize(Mat src, int width, int height, int paddedWidth, boolean detection) {
        Mat resized = new Mat();
        try {
            Imgproc.resize(src, resized, new Size(width, height), 0, 0, Imgproc.INTER_LINEAR);
            byte[] pixels = new byte[width * height * 3]; resized.get(0, 0, pixels);
            float[] tensor = new float[3 * height * paddedWidth];
            float[] mean = {.485f, .456f, .406f}, std = {.229f, .224f, .225f};
            for (int c = 0; c < 3; c++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                float p = (pixels[(y * width + x) * 3 + c] & 255) / 255f;
                tensor[(c * height + y) * paddedWidth + x] = detection ? (p - mean[c]) / std[c] : (p - .5f) / .5f;
            }
            return tensor;
        } finally { resized.release(); }
    }

    private static final class TensorOutput {
        final float[] data; final long[] shape;
        TensorOutput(float[] data, long[] shape) { this.data = data; this.shape = shape; }
    }

    private TensorOutput run(OrtSession session, float[] input, long[] shape) throws OrtException {
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), tensor))) {
            OnnxTensor output = (OnnxTensor) result.get(0);
            FloatBuffer buffer = output.getFloatBuffer();
            float[] values = new float[buffer.remaining()]; buffer.get(values);
            return new TensorOutput(values, output.getInfo().getShape());
        }
    }

    private static List<Point[]> detect(TensorOutput output, int originalWidth, int originalHeight) {
        int height = (int)output.shape[2], width = (int)output.shape[3];
        Mat probability = new Mat(height, width, CvType.CV_32FC1);
        Mat binary = new Mat(), mask = new Mat(), hierarchy = new Mat();
        Mat kernel = Mat.ones(2, 2, CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<>();
        List<Point[]> boxes = new ArrayList<>();
        try {
            probability.put(0, 0, output.data);
            Imgproc.threshold(probability, binary, .3, 255, Imgproc.THRESH_BINARY);
            binary.convertTo(mask, CvType.CV_8UC1);
            Imgproc.dilate(mask, mask, kernel);
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            for (int i = 0; i < Math.min(1000, contours.size()); i++) {
                MatOfPoint2f contour = new MatOfPoint2f(contours.get(i).toArray());
                RotatedRect rect;
                try { rect = Imgproc.minAreaRect(contour); } finally { contour.release(); }
                if (Math.min(rect.size.width, rect.size.height) < 3) continue;
                Point[] points = new Point[4]; rect.points(points);
                if (boxScore(probability, points) < .5) continue;
                // The DB polygon is a rectangle. Its rounded polygon offset has the same
                // minimum-area rectangle as expanding both sides by the offset distance.
                double offset = rect.size.area() * 1.4 / (2 * (rect.size.width + rect.size.height));
                rect.size.width += 2 * offset; rect.size.height += 2 * offset;
                if (Math.min(rect.size.width, rect.size.height) < 5) continue;
                rect.points(points); points = orderedQuad(points);
                for (Point point : points) {
                    point.x = Math.max(0, Math.min(originalWidth - 1, Math.rint(point.x * originalWidth / width)));
                    point.y = Math.max(0, Math.min(originalHeight - 1, Math.rint(point.y * originalHeight / height)));
                }
                if (distance(points[0], points[1]) > 3 && distance(points[0], points[3]) > 3) boxes.add(points);
            }
        } finally {
            for (Mat contour : contours) contour.release();
            kernel.release(); hierarchy.release(); mask.release(); binary.release(); probability.release();
        }
        boxes.sort(Comparator.<Point[]>comparingDouble(b -> b[0].y).thenComparingDouble(b -> b[0].x));
        return boxes;
    }

    private static double boxScore(Mat probability, Point[] points) {
        int left = probability.cols() - 1, top = probability.rows() - 1, right = 0, bottom = 0;
        for (Point p : points) {
            left = Math.min(left, Math.max(0, (int)Math.floor(p.x)));
            top = Math.min(top, Math.max(0, (int)Math.floor(p.y)));
            right = Math.max(right, Math.min(probability.cols() - 1, (int)Math.ceil(p.x)));
            bottom = Math.max(bottom, Math.min(probability.rows() - 1, (int)Math.ceil(p.y)));
        }
        Mat mask = Mat.zeros(bottom - top + 1, right - left + 1, CvType.CV_8UC1);
        MatOfPoint polygon = new MatOfPoint();
        Mat roi = probability.submat(top, bottom + 1, left, right + 1);
        try {
            Point[] local = new Point[4];
            for (int i = 0; i < 4; i++) local[i] = new Point((int)(points[i].x - left), (int)(points[i].y - top));
            polygon.fromArray(local);
            Imgproc.fillPoly(mask, Collections.singletonList(polygon), new Scalar(1));
            return Core.mean(roi, mask).val[0];
        } finally { roi.release(); polygon.release(); mask.release(); }
    }

    private static Point[] orderedQuad(Point[] points) {
        Arrays.sort(points, Comparator.comparingDouble(p -> p.x));
        Point a = points[0], b = points[1], c = points[2], d = points[3];
        return new Point[]{a.y < b.y ? a : b, c.y < d.y ? c : d, c.y < d.y ? d : c, a.y < b.y ? b : a};
    }

    private static Mat crop(Mat source, Point[] points) {
        int width = Math.max(1, (int)Math.max(distance(points[0], points[1]), distance(points[2], points[3])));
        int height = Math.max(1, (int)Math.max(distance(points[0], points[3]), distance(points[1], points[2])));
        MatOfPoint2f from = new MatOfPoint2f(points);
        MatOfPoint2f to = new MatOfPoint2f(new Point(0, 0), new Point(width, 0), new Point(width, height), new Point(0, height));
        Mat transform = Imgproc.getPerspectiveTransform(from, to), output = new Mat();
        try {
            Imgproc.warpPerspective(source, output, transform, new Size(width, height), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
            if ((double)height / width >= 1.5) Core.rotate(output, output, Core.ROTATE_90_COUNTERCLOCKWISE);
            return output;
        } catch (RuntimeException error) { output.release(); throw error; }
        finally { transform.release(); from.release(); to.release(); }
    }

    @Override public void close() throws OrtException {
        try { recognition.close(); } finally { detection.close(); }
        // OrtEnvironment is process-shared; closing one OCR session must not invalidate another.
    }
}
