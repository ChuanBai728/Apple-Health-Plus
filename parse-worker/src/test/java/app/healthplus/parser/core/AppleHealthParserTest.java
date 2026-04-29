package app.healthplus.parser.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppleHealthParserTest {

    @Test
    void shouldParseRecordAndWorkoutElements() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <HealthData>
                  <Record type="HKQuantityTypeIdentifierHeartRate"
                          sourceName="Apple Watch"
                          value="62"
                          unit="count/min"
                          startDate="2026-04-26T10:00:00Z"
                          endDate="2026-04-26T10:00:00Z" />
                  <Workout workoutActivityType="HKWorkoutActivityTypeRunning"
                           sourceName="Apple Watch"
                           duration="42"
                           durationUnit="min"
                           startDate="2026-04-26T11:00:00Z"
                           endDate="2026-04-26T11:42:00Z" />
                </HealthData>
                """;

        AppleHealthParser parser = new AppleHealthParser();
        List<ParsedHealthRecord> records = parser.parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(2, records.size());
        assertEquals("heart_rate", records.getFirst().metricKey());
        assertEquals("heart_cardio", records.getFirst().categoryKey());
        assertEquals(62.0d, records.getFirst().valueNumeric());

        assertEquals("workout", records.get(1).metricKey());
        assertEquals("workouts_training", records.get(1).categoryKey());
        assertNotNull(records.get(1).rawPayloadJson());
        assertFalse(records.get(1).rawPayloadJson().isBlank());
    }
}
