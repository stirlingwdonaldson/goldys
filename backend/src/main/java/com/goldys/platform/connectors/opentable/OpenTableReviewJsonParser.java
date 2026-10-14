package com.goldys.platform.connectors.opentable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.ReviewInput;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps one GuestCenter reviews response ({@code reviews/details}) to canonical review inputs. The
 * response pages reviews 25 at a time under a {@code data} array; only the fields {@code
 * canonical_review} models are read here, and the rest stay in the raw ledger.
 *
 * <p>GuestCenter returns ratings as whole floats (e.g. {@code 5.0}) and uses {@code 0.0} for a
 * sub-rating the diner did not give; both normalise to the canonical 1-5 integer with {@code 0} →
 * {@code null}. {@code noiseText} is the free-text noise label (the numeric {@code noiseRating} is
 * deliberately not modelled — see the review analytics design spec). {@code id} and {@code
 * otReviewId} are identical; {@code id} is the review identity.
 */
@Component
public class OpenTableReviewJsonParser {
  static final String SOURCE_SYSTEM = "OPENTABLE";

  private final ObjectMapper json;

  public OpenTableReviewJsonParser(ObjectMapper json) {
    this.json = json;
  }

  /** Parses the {@code data} array; throws {@code CONNECTOR_SCHEMA_MISMATCH} on a shape change. */
  public List<ReviewInput> parse(byte[] body, UUID rawRecordId) {
    JsonNode data = readTree(body).get("data");
    if (data == null || !data.isArray()) {
      throw schema("OpenTable reviews response has no data array");
    }
    List<ReviewInput> out = new ArrayList<>(data.size());
    for (JsonNode r : data) {
      out.add(toInput(r, rawRecordId));
    }
    return out;
  }

  private static ReviewInput toInput(JsonNode r, UUID rawRecordId) {
    return new ReviewInput(
        SOURCE_SYSTEM,
        requireText(r, "id"),
        requireText(r, "source"),
        instant(r, "reviewDate", true),
        instant(r, "visitDate", false),
        rating(r, "overallRating"),
        rating(r, "foodRating"),
        rating(r, "serviceRating"),
        rating(r, "ambianceRating"),
        rating(r, "valueRating"),
        text(r, "noiseText"),
        boolOrNull(r, "recommends"),
        text(r, "comment"),
        text(r, "privateNotes"),
        stringList(r, "tags"),
        stringList(r, "keywords"),
        text(r, "guestId"),
        text(r, "guestName"),
        rawRecordId);
  }

  /** Whole-float rating ({@code 0.0}–{@code 5.0}) → integer; {@code 0} or absent → null. */
  private static Integer rating(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.isNumber()) {
      return null;
    }
    int stars = value.intValue();
    return stars == 0 ? null : stars;
  }

  /** ISO-8601 instant ({@code …Z} or an explicit offset); null only when optional and absent. */
  private static Instant instant(JsonNode r, String field, boolean required) {
    String value = text(r, field);
    if (value == null) {
      if (required) {
        throw schema("OpenTable review is missing '" + field + "'");
      }
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw schema("OpenTable review has an unparseable '" + field + "'");
    }
  }

  private static Boolean boolOrNull(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.isBoolean()) {
      return null;
    }
    return value.asBoolean();
  }

  /** Array of non-blank strings; absent or non-array → empty list (matches the CSV parser). */
  private static List<String> stringList(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull() || !value.isArray()) {
      return List.of();
    }
    List<String> out = new ArrayList<>(value.size());
    for (JsonNode item : value) {
      if (item.isTextual() && !item.asText().isBlank()) {
        out.add(item.asText());
      }
    }
    return out;
  }

  private static String requireText(JsonNode r, String field) {
    String value = text(r, field);
    if (value == null) {
      throw schema("OpenTable review is missing '" + field + "'");
    }
    return value;
  }

  private static String text(JsonNode r, String field) {
    JsonNode value = r.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String s = value.asText().trim();
    return s.isEmpty() ? null : s;
  }

  private JsonNode readTree(byte[] body) {
    try {
      JsonNode root = json.readTree(body);
      if (root == null || !root.isObject()) {
        throw schema("OpenTable reviews response is not a JSON object");
      }
      return root;
    } catch (IOException e) {
      throw schema("OpenTable reviews response is not valid JSON");
    }
  }

  private static ConnectorFetchException schema(String message) {
    return new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", message);
  }
}
