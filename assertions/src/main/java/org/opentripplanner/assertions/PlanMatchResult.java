package org.opentripplanner.assertions;

import java.util.List;
import java.util.Objects;
import org.opentripplanner.client.model.TripPlan;

/** An evaluation snapshot, including successful matches and all alternative diagnostics. */
public record PlanMatchResult(
    String description,
    boolean matches,
    TripPlan plan,
    TransitLegMatching matchingMode,
    List<List<String>> expectedLegs,
    List<String> itineraryCriteria,
    List<ItineraryMatchResult> itineraryResults,
    List<PlanMatchResult> children) {
  public PlanMatchResult {
    Objects.requireNonNull(description);
    Objects.requireNonNull(plan);
    expectedLegs = expectedLegs.stream().map(List::copyOf).toList();
    itineraryCriteria = List.copyOf(itineraryCriteria);
    itineraryResults = List.copyOf(itineraryResults);
    children = List.copyOf(children);
  }

  public void assertMatches() {
    if (!matches) throw new ItineraryAssertionError(this);
  }

  public String describe() {
    StringBuilder text =
        new StringBuilder(description).append(matches ? " [matched]" : " [failed]");
    if (matchingMode != null) text.append(" (").append(matchingMode).append(")");
    for (int i = 0; i < expectedLegs.size(); i++) {
      text.append("\nLeg ")
          .append(i + 1)
          .append(": ")
          .append(String.join(", ", expectedLegs.get(i)));
    }
    itineraryCriteria.forEach(c -> text.append("\n").append(c));
    for (int i = 0; i < itineraryResults.size(); i++) {
      var result = itineraryResults.get(i);
      text.append("\nItinerary ").append(i + 1).append(result.isSuccess() ? " matched" : " failed");
      result.errors().forEach(e -> text.append("\n  ").append(e));
      result
          .partialMatches()
          .forEach(
              m ->
                  text.append("\n  Matched: ")
                      .append(m.getMatchingCriteria())
                      .append("; missing: ")
                      .append(m.getMissingCriteria()));
      plan.itineraries()
          .get(i)
          .legs()
          .forEach(l -> text.append("\n  Actual: ").append(l.formatLegDescription()));
    }
    children.forEach(child -> text.append("\n").append(child.describe()));
    return text.toString();
  }
}
