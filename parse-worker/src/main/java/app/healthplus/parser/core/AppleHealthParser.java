package app.healthplus.parser.core;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

@Component
public class AppleHealthParser {

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

    /** Stream-parse XML directly to a consumer — avoids building 2M Java objects in memory. */
    public long parseXmlStreaming(InputStream inputStream, Consumer<ParsedHealthRecord> consumer)
            throws XMLStreamException, IOException {
        XMLInputFactory factory = new com.fasterxml.aalto.stax.InputFactoryImpl();
        XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
        long count = 0;
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                String localName = reader.getLocalName();
                if (!"Record".equals(localName) && !"Workout".equals(localName)) continue;

                String metricKey = normalizeMetricKey(attribute(reader, "type"), localName);
                String categoryKey = categoryKey(metricKey, localName);
                String sourceName = attribute(reader, "sourceName");
                String value = "Workout".equals(localName) ? attribute(reader, "duration") : attribute(reader, "value");
                Double valueNumeric = normalizeValue(metricKey, parseDouble(value));
                String unit = "Workout".equals(localName) ? attribute(reader, "durationUnit") : normalizeUnit(metricKey, attribute(reader, "unit"));
                Instant startAt = parseInstant(attribute(reader, "startDate"));
                Instant endAt = parseInstant(attribute(reader, "endDate"));
                String rawPayloadJson = "{\"sourceName\":\"" + escapeJson(sourceName) + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

                // Convert sleep analysis categorical records to numeric sleep_duration
                String type = attribute(reader, "type");
                if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
                    metricKey = "sleep_duration";
                    valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
                    unit = "h";
                }

                consumer.accept(new ParsedHealthRecord(
                        localName.toLowerCase(Locale.ROOT), metricKey, categoryKey,
                        sourceName, valueNumeric, value, unit, startAt, endAt, rawPayloadJson));
                count++;
            }
        } finally {
            reader.close();
        }
        return count;
    }

    public List<ParsedHealthRecord> parseXml(InputStream inputStream) throws XMLStreamException, IOException {
        XMLInputFactory factory = new com.fasterxml.aalto.stax.InputFactoryImpl();
        XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
        List<ParsedHealthRecord> results = new ArrayList<>();

        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }

                String localName = reader.getLocalName();
                if (!"Record".equals(localName) && !"Workout".equals(localName)) {
                    continue;
                }

                String metricKey = normalizeMetricKey(attribute(reader, "type"), localName);
                String categoryKey = categoryKey(metricKey, localName);
                String sourceName = attribute(reader, "sourceName");
                String value = "Workout".equals(localName) ? attribute(reader, "duration") : attribute(reader, "value");
                Double valueNumeric = normalizeValue(metricKey, parseDouble(value));
                String unit = "Workout".equals(localName) ? attribute(reader, "durationUnit") : normalizeUnit(metricKey, attribute(reader, "unit"));
                Instant startAt = parseInstant(attribute(reader, "startDate"));
                Instant endAt = parseInstant(attribute(reader, "endDate"));
                String rawPayloadJson = "{\"sourceName\":\"" + escapeJson(sourceName) + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

                // Convert sleep analysis categorical records to numeric sleep_duration
                String type = attribute(reader, "type");
                if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
                    metricKey = "sleep_duration";
                    valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
                    unit = "h";
                }

                results.add(new ParsedHealthRecord(
                        localName.toLowerCase(Locale.ROOT),
                        metricKey,
                        categoryKey,
                        sourceName,
                        valueNumeric,
                        value,
                        unit,
                        startAt,
                        endAt,
                        rawPayloadJson
                ));
            }
        } finally {
            reader.close();
        }

        return results;
    }

    private String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private String normalizeMetricKey(String type, String localName) {
        if (type == null || type.isBlank()) {
            return localName.toLowerCase(Locale.ROOT);
        }
        return type.replace("HKQuantityTypeIdentifier", "")
                .replace("HKCategoryTypeIdentifier", "")
                .replace("HKWorkoutActivityType", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    private String categoryKey(String metricKey, String localName) {
        if ("Workout".equals(localName)) {
            return "workouts_training";
        }
        return CATEGORY_MAP.getOrDefault(metricKey, "other");
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH;
        // Handle ISO format directly: "2026-04-26T10:00:00Z"
        if (value.contains("T")) {
            try { return Instant.parse(value.trim()); }
            catch (Exception e) { return Instant.EPOCH; }
        }
        // Handle Apple Health real export format: "2024-01-15 08:30:00 +0800"
        try {
            String normalized = value.trim()
                    .replaceFirst(" ([+-])(\\d{2})(\\d{2})$", "$1$2:$3") // " +0800" → "+08:00"
                    .replace(" ", "T")   // date-time space → T
                    .replace(" ", "");   // time-offset space → remove
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Apple Health stores some metrics as 0-1 fractions that should be 0-100 percentages.
     */
    private Double normalizeValue(String metricKey, Double value) {
        if (value == null) return null;
        return switch (metricKey) {
            case "body_fat_percentage",
                 "oxygen_saturation",
                 "apple_walking_steadiness",
                 "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> value * 100.0;
            case "body_mass" -> value;  // stored in kg, keep as-is
            default -> value;
        };
    }

    private String normalizeUnit(String metricKey, String unit) {
        if (unit == null) return "";
        return switch (metricKey) {
            case "body_fat_percentage",
                 "oxygen_saturation",
                 "apple_walking_steadiness",
                 "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> "%";
            case "body_mass" -> "kg";
            default -> unit;
        };
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
