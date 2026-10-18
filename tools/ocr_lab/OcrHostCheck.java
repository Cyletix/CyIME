import com.kingzcheung.xime.ocr.DocumentOcrPipeline;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/** Runs production OCR code; arguments: model directory, fixture TSV, output directory. */
public final class OcrHostCheck {
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Core.setNumThreads(1);
        Path models = Paths.get(args[0]), out = Paths.get(args[2]);
        Files.createDirectories(out);
        List<String> report = new ArrayList<>();
        int failures = 0;
        try (DocumentOcrPipeline pipeline = new DocumentOcrPipeline(
                models.resolve("ch_PP-OCRv5_det_mobile.onnx").toString(),
                models.resolve("ch_PP-OCRv5_rec_mobile.onnx").toString(),
                models.resolve("ppocrv5-characters.txt").toString())) {
            for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
                String[] fields = line.split("\t", -1);
                Path path = Paths.get(fields[0]);
                Mat input = Imgcodecs.imread(path.toString());
                try {
                    long start = System.nanoTime();
                    List<DocumentOcrPipeline.TextRegion> regions = pipeline.recognize(input);
                    String text = DocumentOcrPipeline.assemble(regions);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    String normalized = text.replaceAll("\\s+", "");
                    boolean passed = normalized.equals(fields[1].replaceAll("\\s+", ""));
                    if (!passed) failures++;
                    String row = "{\"image\":" + quote(path.getFileName().toString()) + ",\"text\":" + quote(text) +
                            ",\"expected\":" + quote(fields[1]) + ",\"passed\":" + passed + ",\"ms\":" + ms + ",\"regions\":[";
                    List<String> boxes = new ArrayList<>();
                    for (DocumentOcrPipeline.TextRegion r : regions) {
                        boxes.add("{\"text\":" + quote(r.text) + ",\"score\":" + r.confidence + "}");
                        MatOfPoint polygon = new MatOfPoint(r.points);
                        try { Imgproc.polylines(input, Collections.singletonList(polygon), true, new Scalar(60, 200, 60), 1); }
                        finally { polygon.release(); }
                        if (r.text.matches("[A-Za-z0-9 ]+")) Imgproc.putText(input, r.text, r.points[3], Imgproc.FONT_HERSHEY_SIMPLEX, .45, new Scalar(20, 30, 220), 1);
                    }
                    row += String.join(",", boxes) + "]}";
                    report.add(row); System.out.println(row);
                    Imgcodecs.imwrite(out.resolve(path.getFileName()).toString(), input);
                } finally { input.release(); }
            }
        }
        Files.write(out.resolve("results.json"), ("[\n" + String.join(",\n", report) + "\n]").getBytes(StandardCharsets.UTF_8));
        if (failures > 0) throw new AssertionError(failures + " OCR fixture(s) failed");
    }
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
