package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of {@code tests/utils/scripts/converter.ts} from the {@code
 * bulk-submission-and-fee-scheme-tests-} project. Converts a comma-separated bulk-submission file
 * (OFFICE/SCHEDULE/OUTCOME triples) to the LSC SMS bulk-load XML schema accepted by the
 * laa-data-claims-api.
 */
final class LegalHelpCsvToXmlConverter {

  private LegalHelpCsvToXmlConverter() {}

  private static final String NS_DECL =
      "<submission xmlns=\"http://www.legalservices.gov.uk/sms/ActivityManagement/XMLSchema/\""
          + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
          + " xsi:schemaLocation=\"http://www.legalservices.gov.uk/sms/ActivityManagement/XMLSchema/"
          + "LSCSMSBulkLoadSchemaV3.xsd\">";

  static void convert(Path input, Path output) throws IOException {
    List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);

    List<Office> offices = new ArrayList<>();
    Office currentOffice = null;
    Schedule currentSchedule = null;

    for (String raw : lines) {
      String line = raw.trim();
      if (line.isEmpty()) {
        continue;
      }

      String[] tokens = line.split(",");
      String section = tokens[0].trim().toUpperCase(Locale.ROOT);
      Map<String, String> attrs = new LinkedHashMap<>();
      for (int i = 1; i < tokens.length; i++) {
        String token = tokens[i].trim();
        if (token.isEmpty()) {
          continue;
        }
        int eq = token.indexOf('=');
        if (eq < 0) {
          continue;
        }
        attrs.put(token.substring(0, eq).trim(), token.substring(eq + 1).trim());
      }

      switch (section) {
        case "OFFICE" -> {
          currentOffice = new Office(attrs, new ArrayList<>());
          offices.add(currentOffice);
          currentSchedule = null;
        }
        case "SCHEDULE" -> {
          if (currentOffice == null) {
            throw new IllegalStateException("Schedule before Office");
          }
          currentSchedule = new Schedule(attrs, new ArrayList<>());
          currentOffice.schedules.add(currentSchedule);
        }
        case "OUTCOME" -> {
          if (currentSchedule == null) {
            throw new IllegalStateException("Outcome before Schedule");
          }
          Map<String, String> outcomeAttrs = new LinkedHashMap<>(attrs);
          String matterType = outcomeAttrs.remove("matterType");
          currentSchedule.outcomes.add(new Outcome(matterType, outcomeAttrs));
        }
        default -> throw new IllegalStateException("Unknown section: " + section);
      }
    }

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append(NS_DECL).append('\n');

    for (Office office : offices) {
      xml.append(indent(1)).append("<office ").append(attrsToString(office.attrs)).append(">\n");
      for (Schedule schedule : office.schedules) {
        xml.append(indent(2))
            .append("<schedule ")
            .append(attrsToString(schedule.attrs))
            .append(">\n");
        for (Outcome outcome : schedule.outcomes) {
          xml.append(indent(3)).append("<outcome");
          if (outcome.matterType != null) {
            xml.append(" matterType=\"").append(escape(outcome.matterType)).append('\"');
          }
          xml.append(">\n");
          for (Map.Entry<String, String> item : outcome.items.entrySet()) {
            xml.append(indent(4))
                .append("<outcomeItem name=\"")
                .append(escape(item.getKey()))
                .append("\">")
                .append(escape(item.getValue()))
                .append("</outcomeItem>\n");
          }
          xml.append(indent(3)).append("</outcome>\n");
        }
        xml.append(indent(2)).append("</schedule>\n");
      }
      xml.append(indent(1)).append("</office>\n");
    }
    xml.append("</submission>\n");

    Files.writeString(output, xml.toString(), StandardCharsets.UTF_8);
  }

  private static String attrsToString(Map<String, String> attrs) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : attrs.entrySet()) {
      if (!first) {
        sb.append(' ');
      }
      first = false;
      sb.append(entry.getKey()).append("=\"").append(escape(entry.getValue())).append('"');
    }
    return sb.toString();
  }

  private static String indent(int depth) {
    return "  ".repeat(depth);
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private record Office(Map<String, String> attrs, List<Schedule> schedules) {}

  private record Schedule(Map<String, String> attrs, List<Outcome> outcomes) {}

  private record Outcome(String matterType, Map<String, String> items) {}
}
