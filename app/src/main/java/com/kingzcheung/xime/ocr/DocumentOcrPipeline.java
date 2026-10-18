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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
    }

    /** Input is the already-cropped, fixed camera/gallery frame in BGR order. */
    public List<TextRegion> recognize(Mat bgr) throws OrtException {
        return recognize(bgr, true);
    }

    private List<TextRegion> recognize(Mat bgr, boolean refineTiltedRegion) throws OrtException {
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
        for (Point[] box : boxes.subList(0, Math.min(64, boxes.size()))) {
            Mat crop = crop(bgr, box);
            try {
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
                String result = value.toString().trim();
                if (Boolean.getBoolean("cyime.ocr.diagnostics")) System.err.println(Arrays.toString(box) + " => " + result + " " + confidence / Math.max(1, count));
                if (count > 0 && confidence / count >= .65f && !result.isEmpty()) {
                    text.add(new TextRegion(box, result, confidence / count));
                } else if (refineTiltedRegion && !refined && crop.cols() > crop.rows() * 3 &&
                        Math.abs(box[1].y - box[0].y) > distance(box[0], box[1]) * .04) {
                    // A sloping background can merge separated text into one large detection.
                    // Retry detection once inside that rectified region, never scan outside the frame.
                    refined = true;
                    List<TextRegion> parts = recognize(crop, false);
                    if (!parts.isEmpty()) {
                        float score = (float)parts.stream().mapToDouble(p -> p.confidence).average().orElse(0);
                        text.add(new TextRegion(box, assemble(parts), score));
                    }
                }
            } finally { crop.release(); }
        }
        return readingOrder(text);
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
