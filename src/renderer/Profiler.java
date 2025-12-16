package renderer;

import java.util.LinkedHashMap;
import java.util.Map;

public class Profiler {

    private static Profiler instance;
    private final Map<String, TimingSection> sections;
    private long frameNumber = 0;
    private final double[] frameTimeHistory;
    private int historyIndex = 0;
    private static final int HISTORY_SIZE = 60;
    private long frameStartTime;
    private String activeSection = null;
    private double averageFrameTime = 0;
    private double minFrameTime = Double.MAX_VALUE;
    private double maxFrameTime = 0;
    private double frameTimeVariance = 0;

    // Memory tracking
    private long lastUsedMemory = 0;
    private long peakMemory = 0;
    public static class TimingSection {
        public final String name;
        public final String category;

        // Current frame timing
        public long startTime;
        public double lastDurationMs;

        // Statistics
        public double averageDurationMs;
        public double minDurationMs = Double.MAX_VALUE;
        public double maxDurationMs = 0;
        public int callCount = 0;

        // Hierarchy
        public int depth = 0;
        private static final double EMA_ALPHA = 0.2;

        public TimingSection(String name, String category) {
            this.name = name;
            this.category = category;
        }

        public void start() {
            startTime = System.nanoTime();
            callCount++;
        }

        public void end() {
            long elapsed = System.nanoTime() - startTime;
            lastDurationMs = elapsed / 1_000_000.0;

            // Update statistics
            if (averageDurationMs == 0) {
                averageDurationMs = lastDurationMs;
            } else {
                averageDurationMs = averageDurationMs * (1 - EMA_ALPHA) + lastDurationMs * EMA_ALPHA;
            }

            minDurationMs = Math.min(minDurationMs, lastDurationMs);
            maxDurationMs = Math.max(maxDurationMs, lastDurationMs);
        }

        public void reset() {
            lastDurationMs = 0;
            callCount = 0;
        }
    }

    // Singleton

    private Profiler() {
        sections = new LinkedHashMap<>(); // Preserve insertion order
        frameTimeHistory = new double[HISTORY_SIZE];

        registerSection("Frame", "Frame");
        registerSection("Clear", "Render");
        registerSection("GeometryTransform", "Render");
        registerSection("Culling", "Render");
        registerSection("Projection", "Render");
        registerSection("Rasterization", "Render");
        registerSection("Shading", "Render");
        registerSection("PostProcess", "Render");
        registerSection("Present", "Render");
        registerSection("UI", "System");
    }

    public static Profiler getInstance() {
        if (instance == null) {
            instance = new Profiler();
        }
        return instance;
    }

    //Section Management
    public void registerSection(String name, String category) {
        if (!sections.containsKey(name)) {
            sections.put(name, new TimingSection(name, category));
        }
    }

    public void beginSection(String name) {
        TimingSection section = sections.get(name);
        if (section == null) {
            section = new TimingSection(name, "Custom");
            sections.put(name, section);
        }
        section.start();
        activeSection = name;
    }

    public void endSection(String name) {
        TimingSection section = sections.get(name);
        if (section != null) {
            section.end();
        }
        activeSection = null;
    }
    public AutoCloseable time(String name) {
        beginSection(name);
        return () -> endSection(name);
    }
    public void beginFrame() {
        frameNumber++;
        frameStartTime = System.nanoTime();
        for (TimingSection section : sections.values()) {
            section.reset();
        }

        beginSection("Frame");
    }
    public void endFrame() {
        endSection("Frame");
        double frameTime = (System.nanoTime() - frameStartTime) / 1_000_000.0;
        frameTimeHistory[historyIndex] = frameTime;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        updateStatistics();
        trackMemory();
    }

    private void updateStatistics() {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        int count = 0;

        for (double time : frameTimeHistory) {
            if (time > 0) {
                sum += time;
                min = Math.min(min, time);
                max = Math.max(max, time);
                count++;
            }
        }

        if (count > 0) {
            averageFrameTime = sum / count;
            minFrameTime = min;
            maxFrameTime = max;

            // Calculate variance
            double varianceSum = 0;
            for (double time : frameTimeHistory) {
                if (time > 0) {
                    double diff = time - averageFrameTime;
                    varianceSum += diff * diff;
                }
            }
            frameTimeVariance = Math.sqrt(varianceSum / count);
        }
    }

    private void trackMemory() {
        Runtime rt = Runtime.getRuntime();
        lastUsedMemory = rt.totalMemory() - rt.freeMemory();
        peakMemory = Math.max(peakMemory, lastUsedMemory);
    }

    //Reporting
    public String getReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("========================================================\n");
        sb.append("                  PERFORMANCE PROFILER                   \n");
        sb.append("========================================================\n\n");

        // Frame statistics
        sb.append("FRAME STATISTICS\n");
        sb.append("--------------------------------------------------------\n");
        sb.append(String.format("  Frame:        %d\n", frameNumber));
        sb.append(String.format("  Frame Time:   %.2f ms (%.1f FPS)\n",
            averageFrameTime, 1000.0 / Math.max(0.001, averageFrameTime)));
        sb.append(String.format("  Min/Max:      %.2f / %.2f ms\n", minFrameTime, maxFrameTime));
        sb.append(String.format("  Variance:     %.2f ms (%.1f%%)\n",
            frameTimeVariance, (frameTimeVariance / averageFrameTime) * 100));
        sb.append("\n");

        // Section breakdown
        sb.append("SECTION BREAKDOWN\n");
        sb.append("--------------------------------------------------------\n");
        sb.append(String.format("  %-20s %8s %8s %8s %6s\n",
            "Section", "Last", "Avg", "Max", "Calls"));
        sb.append("  ------------------------------------------------------\n");

        for (TimingSection section : sections.values()) {
            if (section.callCount > 0 || section.averageDurationMs > 0) {
                double percentage = (section.averageDurationMs / averageFrameTime) * 100;
                sb.append(String.format("  %-20s %7.2fms %7.2fms %7.2fms %5d  [%s]\n",
                    section.name,
                    section.lastDurationMs,
                    section.averageDurationMs,
                    section.maxDurationMs,
                    section.callCount,
                    generateBar(percentage, 10)
                ));
            }
        }
        sb.append("\n");

        // Memory statistics
        sb.append("MEMORY\n");
        sb.append("--------------------------------------------------------\n");
        sb.append(String.format("  Used:    %s\n", formatBytes(lastUsedMemory)));
        sb.append(String.format("  Peak:    %s\n", formatBytes(peakMemory)));
        sb.append(String.format("  Max:     %s\n", formatBytes(Runtime.getRuntime().maxMemory())));

        return sb.toString();
    }

    public String getCompactSummary() {
        double fps = 1000.0 / Math.max(0.001, averageFrameTime);
        return String.format("%.1f FPS (%.2fms) | Mem: %s",
            fps, averageFrameTime, formatBytes(lastUsedMemory));
    }

    public double getSectionPercentage(String name) {
        TimingSection section = sections.get(name);
        if (section == null || averageFrameTime <= 0) return 0;
        return (section.averageDurationMs / averageFrameTime) * 100;
    }

    // Accessors

    public long getFrameNumber() { return frameNumber; }
    public double getAverageFrameTime() { return averageFrameTime; }
    public double getAverageFPS() { return 1000.0 / Math.max(0.001, averageFrameTime); }
    public double getFrameTimeVariance() { return frameTimeVariance; }
    public long getUsedMemory() { return lastUsedMemory; }
    public long getPeakMemory() { return peakMemory; }

    public Map<String, TimingSection> getSections() {
        return sections;
    }

    public TimingSection getSection(String name) {
        return sections.get(name);
    }

    // Utilities

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String generateBar(double percentage, int width) {
        int filled = (int)((percentage / 100.0) * width);
        filled = Math.min(width, Math.max(0, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "#" : "-");
        }
        return bar.toString();
    }

    public void reset() {
        frameNumber = 0;
        historyIndex = 0;
        java.util.Arrays.fill(frameTimeHistory, 0);

        for (TimingSection section : sections.values()) {
            section.averageDurationMs = 0;
            section.minDurationMs = Double.MAX_VALUE;
            section.maxDurationMs = 0;
            section.callCount = 0;
        }

        minFrameTime = Double.MAX_VALUE;
        maxFrameTime = 0;
        averageFrameTime = 0;
        peakMemory = 0;
    }
}

