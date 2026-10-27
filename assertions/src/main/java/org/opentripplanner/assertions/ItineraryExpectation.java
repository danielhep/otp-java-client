package org.opentripplanner.assertions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.client.model.Itinerary;
import org.opentripplanner.client.model.TripPlan;

/** All conditions must hold for the same itinerary. An empty expectation matches any itinerary. */
public final class ItineraryExpectation implements PlanExpectation {
  private final List<List<LegCriterion>> expectedLegs = new ArrayList<>();
  private TransitLegMatching matchingMode = TransitLegMatching.UNORDERED_SUBSET;
  private Duration maxDuration;

  public static PlanExpectation noItineraryMatching(ItineraryExpectation expectation) {
    Objects.requireNonNull(expectation);
    return plan -> {
      var evaluated = expectation.evaluate(plan);
      return new PlanMatchResult(
          "No itinerary must match",
          !evaluated.matches(),
          plan,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(evaluated));
    };
  }

  /** Requires at least one itinerary, with every itinerary satisfying the expectation. */
  public static PlanExpectation allItinerariesMatching(ItineraryExpectation expectation) {
    Objects.requireNonNull(expectation);
    return plan -> {
      var evaluated = expectation.evaluate(plan);
      var itineraries = evaluated.itineraryResults();
      return new PlanMatchResult(
          "Every itinerary must match",
          !itineraries.isEmpty() && itineraries.stream().allMatch(ItineraryMatchResult::isSuccess),
          evaluated.plan(),
          evaluated.matchingMode(),
          evaluated.expectedLegs(),
          evaluated.itineraryCriteria(),
          itineraries,
          evaluated.children());
    };
  }

  public ItineraryExpectation withTransitLegMatching(TransitLegMatching mode) {
    matchingMode = Objects.requireNonNull(mode);
    return this;
  }

  /**
   * Captures this leg's conditions; subsequent changes to its builder do not affect this
   * expectation.
   */
  public ItineraryExpectation withLeg(TransitLegExpectation leg) {
    expectedLegs.add(leg.criteria());
    return this;
  }

  /** Total elapsed time, including walking and waiting, from first departure to final arrival. */
  public ItineraryExpectation withMaxDuration(Duration duration) {
    TransitLegExpectation.requireDuration(duration);
    maxDuration = duration;
    return this;
  }

  /** Evalutes every itinerary in the plan to check for matching results */
  @Override
  public PlanMatchResult evaluate(TripPlan plan) {
    Objects.requireNonNull(plan);
    var results = plan.itineraries().stream().map(this::evaluateItinerary).toList();
    return new PlanMatchResult(
        "At least one itinerary must match",
        results.stream().anyMatch(ItineraryMatchResult::isSuccess),
        plan,
        matchingMode,
        expectedLegs.stream().map(l -> l.stream().map(LegCriterion::message).toList()).toList(),
        maxDuration == null ? List.of() : List.of("Maximum itinerary duration: " + maxDuration),
        results,
        List.of());
  }

  /** Evaluates an individual itinerary for a match */
  private ItineraryMatchResult evaluateItinerary(Itinerary itinerary) {
    var result = new TransitLegMatcher(expectedLegs, itinerary.transitLegs()).match(matchingMode);
    if (maxDuration == null || elapsed(itinerary).compareTo(maxDuration) <= 0) return result;
    var errors = new ArrayList<>(result.errors());
    errors.add("Itinerary duration " + elapsed(itinerary) + " exceeds " + maxDuration);
    return new ItineraryMatchResult(
        result.successfulMatches(), result.partialMatches(), result.extraMatches(), errors);
  }

  private static Duration elapsed(Itinerary itinerary) {
    if (itinerary.legs().isEmpty()) return Duration.ZERO;
    return Duration.between(
        itinerary.legs().getFirst().startTime(), itinerary.legs().getLast().endTime());
  }
}
