package com.kingzcheung.xime.ocr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Table structure from recognized text boxes. Plain Java without OpenCV: JVM tests and the desktop
 * OCR check run this exact code.
 *
 * Columns come from ruled vertical lines when there are any, otherwise from the vertical bands that no
 * row's text crosses: the projection-profile idea applied to text boxes, so borderless tables work too.
 * Rows come from text lines; only a fully ruled grid uses its horizontal lines, so a wrapped cell stays
 * one cell there.
 */
public final class OcrTableLayout {
    private OcrTableLayout() {}

    public enum Format {
        MARKDOWN, CSV, SPACES;

        /** Null for plain text, also for a name this build does not know. */
        public static Format parse(String name) {
            if (name == null) return null;
            for (Format format : values()) if (format.name().equals(name)) return format;
            return null;
        }
    }

    /** One recognized box; corners clockwise from top-left, as the detector orders them. */
    public static final class Box {
        final double[] x, y;
        final String text;

        public Box(double[] x, double[] y, String text) {
            if (x.length != 4 || y.length != 4) throw new IllegalArgumentException("Expected four corners");
            this.x = x.clone(); this.y = y.clone(); this.text = text;
        }

        public static Box rect(double left, double top, double right, double bottom, String text) {
            return new Box(new double[]{left, right, right, left}, new double[]{top, top, bottom, bottom}, text);
        }

        double left() { return Math.min(Math.min(x[0], x[1]), Math.min(x[2], x[3])); }
        double right() { return Math.max(Math.max(x[0], x[1]), Math.max(x[2], x[3])); }
        double top() { return Math.min(Math.min(y[0], y[1]), Math.min(y[2], y[3])); }
        double bottom() { return Math.max(Math.max(y[0], y[1]), Math.max(y[2], y[3])); }
        double centerX() { return (x[0] + x[1] + x[2] + x[3]) / 4; }
        double centerY() { return (y[0] + y[1] + y[2] + y[3]) / 4; }
        double height() { return bottom() - top(); }

        /** Maps the corners with a 2x3 affine matrix {a, b, c, d, e, f}: (ax + by + c, dx + ey + f). */
        public Box transformed(double[] m) {
            double[] nx = new double[4], ny = new double[4];
            for (int i = 0; i < 4; i++) {
                nx[i] = m[0] * x[i] + m[1] * y[i] + m[2];
                ny[i] = m[3] * x[i] + m[4] * y[i] + m[5];
            }
            return new Box(nx, ny, text);
        }
    }

    /** Median slope of the long text boxes in degrees; 0 when level, unknown or too steep to be a table. */
    public static double skewDegrees(List<Box> boxes) {
        List<Double> angles = new ArrayList<>();
        for (Box b : boxes) {
            double w = Math.hypot(b.x[1] - b.x[0], b.y[1] - b.y[0]);
            double h = Math.hypot(b.x[3] - b.x[0], b.y[3] - b.y[0]);
            if (w > 0 && w >= 2 * h) angles.add(Math.toDegrees(Math.atan2(b.y[1] - b.y[0], b.x[1] - b.x[0])));
        }
        if (angles.isEmpty()) return 0;
        angles.sort(null);
        double median = angles.get(angles.size() / 2);
        return Math.abs(median) < .3 || Math.abs(median) > 12 ? 0 : median;
    }

    /** Rotation about the image center that levels lines sloping by {@code degrees}. */
    public static double[] straighten(double degrees, double width, double height) {
        double t = Math.toRadians(degrees), c = Math.cos(t), s = Math.sin(t), cx = width / 2, cy = height / 2;
        return new double[]{c, s, cx - c * cx - s * cy, -s, c, cy + s * cx - c * cy};
    }

    public static double medianHeight(List<Box> boxes) {
        if (boxes.isEmpty()) return 0;
        double[] heights = new double[boxes.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = boxes.get(i).height();
        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    /**
     * Ruled lines in a projection profile (line pixels summed per row or per column, after text has been
     * removed): runs at least {@code minLength} and half the strongest line, merged when closer than
     * {@code minGap} so a thick or doubled border counts once. Returns the line centers.
     */
    public static double[] lines(int[] profile, int minLength, double minGap) {
        int strongest = 0;
        for (int value : profile) strongest = Math.max(strongest, value);
        if (strongest < Math.max(1, minLength)) return new double[0];
        int threshold = Math.max(minLength, (strongest + 1) / 2);
        List<double[]> runs = new ArrayList<>();
        for (int i = 0; i < profile.length; i++) {
            if (profile[i] < threshold) continue;
            int start = i;
            while (i + 1 < profile.length && profile[i + 1] >= threshold) i++;
            double[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (last != null && start - last[1] < minGap) last[1] = i; else runs.add(new double[]{start, i});
        }
        double[] centers = new double[runs.size()];
        for (int i = 0; i < centers.length; i++) centers[i] = (runs.get(i)[0] + runs.get(i)[1]) / 2;
        return centers;
    }

    /**
     * Blank runs strictly inside a profile, as {start, end} index pairs (end exclusive): at most
     * {@code maxInk} per entry for at least {@code minRun} entries. Runs touching either end are margins.
     */
    public static int[][] blankRuns(int[] profile, int maxInk, int minRun) {
        List<int[]> runs = new ArrayList<>();
        int first = 0, last = profile.length - 1;
        while (first <= last && profile[first] <= maxInk) first++;
        while (last >= first && profile[last] <= maxInk) last--;
        for (int i = first; i <= last; i++) {
            if (profile[i] > maxInk) continue;
            int start = i;
            while (i + 1 <= last && profile[i + 1] <= maxInk) i++;
            if (i - start + 1 >= Math.max(1, minRun)) runs.add(new int[]{start, i + 1});
        }
        return runs.toArray(new int[0][]);
    }

    /**
     * Cells by row and column. Boxes and lines must already be level. Fewer than three vertical lines
     * (none, or only an outer frame) means the columns come from text alignment.
     */
    public static List<List<String>> grid(List<Box> boxes, double[] rowLines, double[] columnLines) {
        List<List<String>> result = new ArrayList<>();
        if (boxes.isEmpty()) return result;
        double height = Math.max(1, medianHeight(boxes));
        boolean ruledColumns = columnLines.length >= 3;
        boolean ruledRows = ruledColumns && rowLines.length >= 2;

        List<List<Box>> rows = new ArrayList<>();
        if (ruledRows) {
            // Inside the ruling one band is one row; text above or below it (a title, a note) keeps its lines.
            List<Box> above = new ArrayList<>(), below = new ArrayList<>();
            List<List<Box>> bands = new ArrayList<>();
            for (int i = 1; i < rowLines.length; i++) bands.add(new ArrayList<>());
            for (Box b : boxes) {
                double cy = b.centerY();
                if (cy < rowLines[0]) above.add(b);
                else if (cy >= rowLines[rowLines.length - 1]) below.add(b);
                else bands.get(interval(rowLines, cy) - 1).add(b);
            }
            rows.addAll(textRows(above));
            for (List<Box> band : bands) if (!band.isEmpty()) rows.add(band);
            rows.addAll(textRows(below));
        } else rows = textRows(boxes);

        double[] cuts = ruledColumns ? columnLines : alignedCuts(rows, height);
        int columns = cuts.length + 1;
        List<List<List<Box>>> cells = new ArrayList<>();
        for (List<Box> row : rows) {
            List<List<Box>> line = new ArrayList<>();
            for (int c = 0; c < columns; c++) line.add(new ArrayList<>());
            // A ruled cell owns the text whose center it holds; an aligned column starts where its text
            // starts, so left-aligned, centered and right-aligned columns all keep their text.
            for (Box b : row) line.get(interval(cuts, ruledColumns ? b.centerX() : b.left() + height * .1)).add(b);
            cells.add(line);
        }
        // Outer frames and doubled borders leave columns that hold nothing in any row.
        boolean[] used = new boolean[columns];
        for (List<List<Box>> line : cells) for (int c = 0; c < columns; c++) used[c] |= !line.get(c).isEmpty();
        for (List<List<Box>> line : cells) {
            List<String> texts = new ArrayList<>();
            for (int c = 0; c < columns; c++) if (used[c]) texts.add(cellText(line.get(c)));
            result.add(texts);
        }
        return result;
    }

    public static String format(List<List<String>> grid, Format format) {
        int columns = 0;
        for (List<String> row : grid) columns = Math.max(columns, row.size());
        if (columns == 0) return "";
        StringBuilder out = new StringBuilder();
        // A Markdown table needs its first row as the header, so a title or note line (one filled cell)
        // before or after the table stays a plain line instead of becoming the header.
        int first = 0, last = grid.size();
        if (format == Format.MARKDOWN && columns >= 2) {
            while (first < last - 1 && filled(grid.get(first)) <= 1) first++;
            while (last - 1 > first && filled(grid.get(last - 1)) <= 1) last--;
            if (last - first < 2) { first = 0; last = grid.size(); }
        }
        for (int r = 0; r < first; r++) out.append(String.join(" ", grid.get(r)).trim()).append('\n');
        if (first > 0) out.append('\n');
        for (int r = first; r < last; r++) {
            List<String> row = grid.get(r);
            if (r > first) out.append('\n');
            if (format == Format.MARKDOWN) {
                out.append('|');
                for (int c = 0; c < columns; c++)
                    out.append(' ').append(c < row.size() ? row.get(c).replace("|", "\\|") : "").append(" |");
                if (r == first) {
                    out.append("\n|");
                    for (int c = 0; c < columns; c++) out.append(" --- |");
                }
            } else if (format == Format.CSV) {
                for (int c = 0; c < columns; c++) {
                    if (c > 0) out.append(',');
                    out.append(csv(c < row.size() ? row.get(c) : ""));
                }
            } else {
                boolean space = false;
                for (String cell : row) {
                    if (cell.isEmpty()) continue;
                    if (space) out.append(' ');
                    out.append(cell);
                    space = true;
                }
            }
        }
        if (last < grid.size()) out.append('\n');
        for (int r = last; r < grid.size(); r++) out.append('\n').append(String.join(" ", grid.get(r)).trim());
        return out.toString();
    }

    private static int filled(List<String> row) {
        int count = 0;
        for (String cell : row) if (!cell.isEmpty()) count++;
        return count;
    }

    /** Index of the band between sorted cut positions: 0 before the first cut, cuts.length after the last. */
    private static int interval(double[] cuts, double value) {
        int index = 0;
        while (index < cuts.length && value >= cuts[index]) index++;
        return index;
    }

    /** Lines of text, top to bottom; the same "same line" test as the plain-text assembly. */
    static List<List<Box>> textRows(List<Box> boxes) {
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(Box::centerY));
        List<List<Box>> rows = new ArrayList<>();
        List<Box> row = null;
        double center = 0, height = 0;
        for (Box b : sorted) {
            if (row != null && Math.abs(b.centerY() - center) < Math.max(height, b.height()) * .65) {
                row.add(b);
                center += (b.centerY() - center) / row.size();
                height = Math.max(height, b.height());
            } else {
                row = new ArrayList<>(); row.add(b); rows.add(row);
                center = b.centerY(); height = b.height();
            }
        }
        for (List<Box> line : rows) {
            line.sort(Comparator.comparingDouble(Box::left));
            // The detector sometimes reads one glyph twice, in two overlapping boxes ("11.25" and "5促销"
            // after its split). Text on one line never overlaps itself, so the narrower copy goes.
            line.removeIf(b -> {
                for (Box other : line) {
                    double overlap = Math.min(b.right(), other.right()) - Math.max(b.left(), other.left());
                    double width = b.right() - b.left(), otherWidth = other.right() - other.left();
                    if (other != b && otherWidth > width && overlap >= width * .6) return true;
                }
                return false;
            });
        }
        return rows;
    }

    /**
     * Column cuts for a table without vertical lines: the middles of the x bands that no row's text
     * covers. Single-box rows (a title, a footnote) do not vote once there are two rows with several
     * boxes, and about one row in seven may cross a gap (a merged or overflowing cell).
     */
    static double[] alignedCuts(List<List<Box>> rows, double height) {
        List<List<Box>> voting = new ArrayList<>();
        for (List<Box> row : rows) if (row.size() >= 2) voting.add(row);
        if (voting.size() < 2) voting = rows;
        int allowed = voting.size() / 7;
        List<Double> edges = new ArrayList<>();
        for (List<Box> row : voting) for (Box b : row) { edges.add(b.left()); edges.add(b.right()); }
        edges.sort(null);
        List<Double> cuts = new ArrayList<>();
        double gapStart = Double.NaN;
        for (int i = 0; i + 1 < edges.size(); i++) {
            double from = edges.get(i), to = edges.get(i + 1);
            if (to <= from) continue;
            double middle = (from + to) / 2;
            int covering = 0;
            for (List<Box> row : voting) {
                for (Box b : row) if (b.left() <= middle && middle <= b.right()) { covering++; break; }
            }
            boolean open = covering <= allowed;
            if (open && Double.isNaN(gapStart)) gapStart = from;
            if (!open && !Double.isNaN(gapStart)) {
                // Boxes are padded, so any gap is real space; a hairline gap is not a column.
                if (from - gapStart >= Math.max(1, height * .15)) cuts.add((gapStart + from) / 2);
                gapStart = Double.NaN;
            }
        }
        double[] result = new double[cuts.size()];
        for (int i = 0; i < result.length; i++) result[i] = cuts.get(i);
        return result;
    }

    private static String cellText(List<Box> boxes) {
        StringBuilder text = new StringBuilder();
        for (List<Box> line : textRows(boxes)) for (Box b : line) join(text, b.text.trim());
        return text.toString();
    }

    /** Chinese pieces join directly, as one wrapped phrase; other scripts keep a word space. */
    private static void join(StringBuilder text, String next) {
        if (next.isEmpty()) return;
        if (text.length() > 0 && !(wide(text.codePointBefore(text.length())) && wide(next.codePointAt(0)))) text.append(' ');
        text.append(next);
    }

    private static boolean wide(int c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA ||
                script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) return true;
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private static String csv(String cell) {
        boolean quote = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r") ||
                (!cell.isEmpty() && (Character.isWhitespace(cell.charAt(0)) || Character.isWhitespace(cell.charAt(cell.length() - 1))));
        return quote ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }
}
