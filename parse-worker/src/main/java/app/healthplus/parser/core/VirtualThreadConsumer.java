package app.healthplus.parser.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual-thread-based consumer that processes raw XML records into
 * fully-parsed {@link PgCopyBatchInserter.ParsedRecord} objects and
 * feeds them into the {@link PgCopyBatchInserter}.
 * <p>
 * Uses {@link Executors#newVirtualThreadPerTaskExecutor()} — each record
 * processing runs on a lightweight virtual thread, enabling massive
 * concurrency without OS thread exhaustion.
 */
public class VirtualThreadConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConsumer.class);

    private static final Map<String, String> CATEGORY_MAP = Map.ofEntries(
            Map.entry("heart_rate", "heart_cardio"),
            Map.entry("resting_heart_rate", "heart_cardio"),
            Map.entry("heart_rate_variability_sdnn", "heart_cardio"),
            Map.entry("body_mass", "body_metrics"),
            Map.entry("body_mass_index", "body_metrics"),
            Map.entry("step_count", "daily_activity"),
            Map.entry("active_energy_burned", "daily_activity"),
            Map.entry("basal_energy_burned", "daily_activity"),
            Map.entry("distance_walking_running", "daily_activity"),
            Map.entry("sleep_analysis", "sleep_recovery"),
            Map.entry("oxygen_saturation", "vitals_respiratory"),
            Map.entry("respiratory_rate", "vitals_respiratory"),
            Map.entry("mindful_session", "mindfulness_mental"),
            Map.entry("workout", "workouts_training")
    );

    private final RecordRingBuffer buffer;
    private final PgCopyBatchInserter inserter;
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    public VirtualThreadConsumer(RecordRingBuffer buffer, PgCopyBatchInserter inserter) {
        this.buffer = buffer;
        this.inserter = inserter;
    }

    @Override
    public void run() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (running.get()) {
                RecordRingBuffer.RawRecord raw = buffer.poll(100);
                if (raw == null) {
                    if (buffer.isDrained()) break;
                    continue;
                }

                // Submit to virtual thread pool for CPU-bound processing
                executor.submit(() -> {
                    try {
                        PgCopyBatchInserter.ParsedRecord parsed = processRecord(raw);
                        inserter.append(parsed);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Failed to process record: {}", raw.type(), e);
                    }
                });
            }

            // Wait for all in-flight tasks to complete
            executor.close();
            executor.awaitTermination(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Consumer interrupted");
        } catch (Exception e) {
            log.error("Consumer error", e);
        } finally {
            // Final safety flush
            inserter.flushRemaining();
            log.info("Consumer finished: {} records processed", processedCount.get());
        }
    }

    public void shutdown() {
        running.set(false);
    }

    public long processedCount() {
        return processedCount.get();
    }

    // ── Heavyweight record processing ──

    static PgCopyBatchInserter.ParsedRecord processRecord(RecordRingBuffer.RawRecord raw) {
        String localName = "Record";
        boolean isWorkout = raw.type().startsWith("HKWorkout");

        String metricKey = normalizeMetricKey(raw.type(), isWorkout ? "Workout" : "Record");
        String categoryKey = isWorkout ? "workouts_training"
                : CATEGORY_MAP.getOrDefault(metricKey, "other");

        String value = isWorkout ? raw.value() : raw.value(); // already set by producer
        Double valueNumeric = parseDouble(value);
        String unit = isWorkout ? raw.unit() : normalizeUnit(metricKey, raw.unit());
        Instant startAt = parseInstant(raw.startDate());
        Instant endAt = parseInstant(raw.endDate());

        // Sleep analysis: convert categorical to numeric duration
        if ("HKCategoryTypeIdentifierSleepAnalysis".equals(raw.type())) {
            metricKey = "sleep_duration";
            valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
            unit = "h";
        }

        valueNumeric = normalizeValue(metricKey, valueNumeric);

        String json = "{\"sourceName\":\"" + escapeJson(raw.sourceName())
                + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

        return new PgCopyBatchInserter.ParsedRecord(
                isWorkout ? "workout" : "record",
                metricKey,
                categoryKey,
                raw.sourceName(),
                valueNumeric,
                value,
                unit,
                startAt,
                endAt,
                json
        );
    }

    // ── Mirror AppleHealthParser logic ──

    private static String normalizeMetricKey(String type, String localName) {
        if (type == null || type.isBlank()) return localName.toLowerCase(Locale.ROOT);
        return type.replace("HKQuantityTypeIdentifier", "")
                .replace("HKCategoryTypeIdentifier", "")
                .replace("HKWorkoutActivityType", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    private static Double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return null; }
    }

    private static Double normalizeValue(String metricKey, Double value) {
        if (value == null) return null;
        return switch (metricKey) {
            case "body_fat_percentage", "oxygen_saturation",
                 "apple_walking_steadiness", "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> value * 100.0;
            default -> value;
        };
    }

    private static String normalizeUnit(String metricKey, String unit) {
        if (unit == null) return "";
        return switch (metricKey) {
            case "body_fat_percentage", "oxygen_saturation",
                 "apple_walking_steadiness", "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> "%";
            case "body_mass" -> "kg";
            default -> unit;
        };
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH;
        if (value.contains("T")) {
            try { return Instant.parse(value.trim()); }
            catch (Exception e) { return Instant.EPOCH; }
        }
        try {
            String normalized = value.trim()
                    .replaceFirst(" ([+-])(\\d{2})(\\d{2})$", "$1$2:$3")
                    .replace(" ", "T")
                    .replace(" ", "");
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
