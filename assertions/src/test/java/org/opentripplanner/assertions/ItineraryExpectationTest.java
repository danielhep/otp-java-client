package org.opentripplanner.assertions;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opentripplanner.api.types.Route;
import org.opentripplanner.client.model.Agency;
import org.opentripplanner.client.model.Currency;
import org.opentripplanner.client.model.FareProductUse;
import org.opentripplanner.client.model.Itinerary;
import org.opentripplanner.client.model.Leg;
import org.opentripplanner.client.model.LegGeometry;
import org.opentripplanner.client.model.LegMode;
import org.opentripplanner.client.model.Money;
import org.opentripplanner.client.model.Place;
import org.opentripplanner.client.model.Stop;
import org.opentripplanner.client.model.Trip;
import org.opentripplanner.client.model.TripPlan;

class ItineraryExpectationTest {

  private static final OffsetDateTime START = OffsetDateTime.parse("2026-01-01T08:00:00Z");
  private static final String GEOMETRY = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

  @Test
  void singleLegMatchSuccess() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of())));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withMode("BUS").withRouteShortName("10"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void nonTransitLegsAreIgnored() {
    TripPlan plan =
        tripPlan(
            itinerary(
                walkLeg(Duration.ofMinutes(10)),
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                walkLeg(Duration.ofMinutes(10))));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void multiLegMatchSuccess() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                transitLeg("1", "Line 1", LegMode.TRAM, Duration.ofMinutes(15), List.of())));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .withLeg(new TransitLegExpectation().withRouteShortName("1"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void exactTransitLegsMustMatchInOrder() {
    TripPlan plan =
        tripPlan(
            itinerary(
                walkLeg(Duration.ofMinutes(5)),
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                transitLeg("1", "Line 1", LegMode.TRAM, Duration.ofMinutes(15), List.of()),
                walkLeg(Duration.ofMinutes(5))));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .withLeg(new TransitLegExpectation().withRouteShortName("1"))
                .evaluate(plan)
                .assertMatches());

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                    .withLeg(new TransitLegExpectation().withRouteShortName("1"))
                    .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                    .evaluate(plan)
                    .assertMatches());

    assertThat(error.getMessage()).contains("Transit leg at position 1 does not match");
    assertThat(error.result().matchingMode()).isEqualTo(TransitLegMatching.EXACT_ORDERED);
  }

  @Test
  void nonStrictTransitLegsRemainUnordered() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                transitLeg("1", "Line 1", LegMode.TRAM, Duration.ofMinutes(15), List.of())));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withRouteShortName("1"))
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void matchesBoardingAndAlightingStopsByCodeOrGtfsId() {
    Leg leg =
        transitLeg(
            "10",
            "Route 10",
            LegMode.BUS,
            Duration.ofMinutes(20),
            List.of(),
            place("Board", "feed:board", "BOARD"),
            place("Alight", "feed:alight", "ALIGHT"),
            false);
    TripPlan plan = tripPlan(itinerary(leg));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(
                    new TransitLegExpectation()
                        .withBoardingStopCode("BOARD")
                        .withBoardingStopGtfsId("feed:board")
                        .withAlightingStopCode("ALIGHT")
                        .withAlightingStopGtfsId("feed:alight"))
                .evaluate(plan)
                .assertMatches());

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withLeg(
                        new TransitLegExpectation()
                            .withBoardingStopCode("ALIGHT")
                            .withAlightingStopGtfsId("feed:board"))
                    .evaluate(plan)
                    .assertMatches());

    assertThat(error.getMessage()).contains("boarding stop code '[ALIGHT]'");
    assertThat(error.getMessage()).contains("alighting stop GTFS ID '[feed:board]'");
  }

  @Test
  void exactMatchingChecksInterliningAtTheExpectedTransition() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg(
                    "10",
                    "Route 10",
                    LegMode.BUS,
                    Duration.ofMinutes(10),
                    List.of(),
                    place("A"),
                    place("B"),
                    false),
                transitLeg(
                    "10",
                    "Route 10",
                    LegMode.BUS,
                    Duration.ofMinutes(10),
                    List.of(),
                    place("B"),
                    place("C"),
                    true)));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .withLeg(
                    new TransitLegExpectation()
                        .withRouteShortName("10")
                        .withInterlinedWithPreviousLeg(true))
                .evaluate(plan)
                .assertMatches());

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                    .withLeg(
                        new TransitLegExpectation()
                            .withRouteShortName("10")
                            .withInterlinedWithPreviousLeg(true))
                    .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                    .evaluate(plan)
                    .assertMatches());

    assertThat(error.getMessage()).contains("Transit leg at position 1 does not match");
    assertThat(error.getMessage()).contains("interlined with previous leg");
  }

  @Test
  void strictMatchingRejectsExtraTransitLegs() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                transitLeg("1", "Line 1", LegMode.TRAM, Duration.ofMinutes(15), List.of())));

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
                    .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                    .evaluate(plan)
                    .assertMatches());

    assertThat(error.getMessage()).contains("additional transit legs");
    assertThat(error.result().itineraryResults()).hasSize(1);
    assertThat(error.result().itineraryResults().get(0).extraMatches()).hasSize(1);
  }

  @Test
  void partialMatchesAndErrorDetailsAreIncluded() {
    TripPlan plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of())));

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withLeg(new TransitLegExpectation().withRouteShortName("10").withMode("TRAM"))
                    .evaluate(plan)
                    .assertMatches());

    assertThat(error.result().itineraryResults()).hasSize(1);
    ItineraryMatchResult result = error.result().itineraryResults().get(0);
    assertThat(result.errors()).isNotEmpty();
    assertThat(result.partialMatches()).hasSize(1);
    assertThat(result.partialMatches().get(0).getMatchingCriteria()).contains("route '[10]'");
    assertThat(result.partialMatches().get(0).getMissingCriteria()).contains("mode TRAM");
  }

  @Test
  void withFarePriceMatchesFareWithSpecifiedPriceRiderCategoryAndMedium() {
    List<FareProductUse> fares = List.of(fare(2.75f, "orca:regular", "orca:cash"));
    TripPlan plan =
        tripPlan(itinerary(transitLeg("E", "E Line", LegMode.BUS, Duration.ofMinutes(12), fares)));

    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(
                    new TransitLegExpectation()
                        .withRouteShortName("E")
                        .withFarePrice(2.75f, "orca:regular", "orca:cash"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void withFarePriceReportsMismatchedFare() {
    List<FareProductUse> fares = List.of(fare(2.75f, "orca:regular", "orca:cash"));
    TripPlan plan =
        tripPlan(itinerary(transitLeg("E", "E Line", LegMode.BUS, Duration.ofMinutes(12), fares)));

    ItineraryAssertionError error =
        assertThrows(
            ItineraryAssertionError.class,
            () ->
                new ItineraryExpectation()
                    .withLeg(
                        new TransitLegExpectation()
                            .withRouteShortName("E")
                            .withFarePrice(3.00f, "orca:regular", "orca:cash"))
                    .evaluate(plan)
                    .assertMatches());

    String expectedFareCriterion = "fare $3.00 (rider category orca:regular, medium orca:cash)";
    assertThat(error.result().expectedLegs())
        .containsExactly(List.of("route '[E]'", expectedFareCriterion));
    assertThat(error.result().itineraryResults()).hasSize(1);
    assertThat(error.result().itineraryResults().get(0).partialMatches()).hasSize(1);
    LegMatchingState partialMatch =
        error.result().itineraryResults().get(0).partialMatches().get(0);
    assertThat(partialMatch.getMatchingCriteria()).isEqualTo("route '[E]'");
    assertThat(partialMatch.getMissingCriteria()).isEqualTo(expectedFareCriterion);
  }

  @Test
  void missingExpectedLegCannotPassWithAnotherSuccessfulLeg() {
    var plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of())));
    assertThrows(
        ItineraryAssertionError.class,
        () ->
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .withLeg(new TransitLegExpectation().withRouteShortName("missing"))
                .evaluate(plan)
                .assertMatches());
  }

  @Test
  void unorderedMatchingFindsDistinctAssignmentForOverlappingExpectations() {
    var plan =
        tripPlan(
            itinerary(
                transitLeg("10", "Route 10", LegMode.BUS, Duration.ofMinutes(20), List.of()),
                transitLeg("20", "Route 20", LegMode.BUS, Duration.ofMinutes(20), List.of())));
    assertDoesNotThrow(
        () ->
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withMode("BUS"))
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .evaluate(plan)
                .assertMatches());
  }

  @ParameterizedTest
  @CsvSource({
    "EXACT_ORDERED,10:20,true",
    "EXACT_ORDERED,20:10,false",
    "EXACT_ORDERED,10:30:20,false",
    "ORDERED_SUBSEQUENCE,30:10:30:20:30,true",
    "ORDERED_SUBSEQUENCE,20:10,false",
    "UNORDERED_SUBSET,20:30:10,true"
  })
  void matchingModesHaveExplicitOrderAndCountSemantics(
      TransitLegMatching mode, String routes, boolean matches) {
    var actual =
        java.util.Arrays.stream(routes.split(":"))
            .map(route -> transitLeg(route, route, LegMode.BUS, Duration.ofMinutes(10), List.of()))
            .toArray(Leg[]::new);
    var result =
        new ItineraryExpectation()
            .withTransitLegMatching(mode)
            .withLeg(new TransitLegExpectation().withRouteShortName("10"))
            .withLeg(new TransitLegExpectation().withRouteShortName("20"))
            .evaluate(tripPlan(itinerary(actual)));
    assertThat(result.matches()).isEqualTo(matches);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(TransitLegMatching.class)
  void aSingleActualLegCannotSatisfyTwoExpectedLegs(TransitLegMatching mode) {
    var plan =
        tripPlan(itinerary(transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(10), List.of())));
    assertThat(
            new ItineraryExpectation()
                .withTransitLegMatching(mode)
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .evaluate(plan)
                .matches())
        .isFalse();
  }

  @Test
  void explicitFalseProhibitsInterliningAndOmittedLeavesItUnconstrained() {
    var leg =
        transitLeg(
            "10",
            "10",
            LegMode.BUS,
            Duration.ofMinutes(10),
            List.of(),
            place("A"),
            place("B"),
            true);
    var plan = tripPlan(itinerary(leg));
    assertThat(
            new ItineraryExpectation()
                .withLeg(new TransitLegExpectation().withRouteShortName("10"))
                .evaluate(plan)
                .matches())
        .isTrue();
    var result =
        new ItineraryExpectation()
            .withLeg(
                new TransitLegExpectation()
                    .withRouteShortName("10")
                    .withInterlinedWithPreviousLeg(false))
            .evaluate(plan);
    assertThat(result.matches()).isFalse();
    assertThat(result.describe()).contains("interlined with previous leg = false");
  }

  @Test
  void itineraryAndLegConditionsMustHoldOnTheSameItinerary() {
    var slow10 = itinerary(transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(40), List.of()));
    var fast20 = itinerary(transitLeg("20", "20", LegMode.BUS, Duration.ofMinutes(10), List.of()));
    var expected =
        new ItineraryExpectation()
            .withMaxDuration(Duration.ofMinutes(20))
            .withLeg(
                new TransitLegExpectation()
                    .withRouteShortName("10")
                    .withMaxDuration(Duration.ofMinutes(45)));
    assertThat(expected.evaluate(tripPlan(slow10, fast20)).matches()).isFalse();
    var fast10 = itinerary(transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(10), List.of()));
    var result = expected.evaluate(tripPlan(slow10, fast10));
    assertThat(result.matches()).isTrue();
    assertThat(result.itineraryResults()).hasSize(2);
    assertThat(result.itineraryResults().get(0).isSuccess()).isFalse();
    assertThat(result.itineraryResults().get(1).isSuccess()).isTrue();
  }

  @Test
  void itineraryDurationIncludesWaitingBetweenLegs() {
    var first = transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(10), List.of());
    var last = walkLeg(Duration.ofMinutes(5));
    var later =
        new Leg(
            last.from(),
            last.to(),
            START.plusMinutes(40),
            START.plusMinutes(45),
            last.realTime(),
            last.interlineWithPreviousLeg(),
            last.mode(),
            last.duration(),
            last.distance(),
            last.headsign(),
            last.route(),
            last.trip(),
            last.fareProducts(),
            last.accessibilityScore(),
            last.agency(),
            last.geometry(),
            last.rentedVehicle(),
            last.intermediatePlaces());
    assertThat(
            new ItineraryExpectation()
                .withMaxDuration(Duration.ofMinutes(30))
                .evaluate(tripPlan(itinerary(first, later)))
                .matches())
        .isFalse();
  }

  @Test
  void alternativesRetainDiagnosticsAndOnlyAssertionThrows() {
    var plan =
        tripPlan(itinerary(transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(10), List.of())));
    var route20 =
        new ItineraryExpectation().withLeg(new TransitLegExpectation().withRouteShortName("20"));
    var route30 =
        new ItineraryExpectation().withLeg(new TransitLegExpectation().withRouteShortName("30"));
    var failure = PlanExpectation.anyOf(route20, route30).evaluate(plan);
    assertThat(failure.matches()).isFalse();
    assertThat(failure.children()).hasSize(2);
    var error = assertThrows(ItineraryAssertionError.class, failure::assertMatches);
    assertThat(error.result()).isSameInstanceAs(failure);
    assertThat(error.getMessage()).contains("route '[20]'");
    assertThat(error.getMessage()).contains("route '[30]'");
    var success = PlanExpectation.anyOf(route20, new ItineraryExpectation()).evaluate(plan);
    assertThat(success.matches()).isTrue();
    assertThat(success.children()).hasSize(2);
    assertThat(PlanExpectation.allOf(route20, new ItineraryExpectation()).evaluate(plan).matches())
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "10,10,10,20,true",
    "20,10,10,10,false",
    "10,10,20,10,false",
    "10,30,10,10,false",
    "10,10,10,30,false",
    "20,30,20,30,false"
  })
  void allItinerariesMustSatisfyLegAndItineraryConditions(
      String firstRoute, int firstMinutes, String secondRoute, int secondMinutes, boolean matches) {
    var plan =
        tripPlan(
            itinerary(
                transitLeg(
                    firstRoute,
                    firstRoute,
                    LegMode.BUS,
                    Duration.ofMinutes(firstMinutes),
                    List.of())),
            itinerary(
                transitLeg(
                    secondRoute,
                    secondRoute,
                    LegMode.BUS,
                    Duration.ofMinutes(secondMinutes),
                    List.of())));
    var expected =
        new ItineraryExpectation()
            .withMaxDuration(Duration.ofMinutes(20))
            .withLeg(new TransitLegExpectation().withRouteShortName("10"));

    assertThat(ItineraryExpectation.allItinerariesMatching(expected).evaluate(plan).matches())
        .isEqualTo(matches);
  }

  @Test
  void allItinerariesMatchingRetainsDiagnosticsForSuccessfulAndFailedItineraries() {
    var plan =
        tripPlan(
            itinerary(transitLeg("10", "10", LegMode.BUS, Duration.ofMinutes(10), List.of())),
            itinerary(transitLeg("20", "20", LegMode.BUS, Duration.ofMinutes(30), List.of())));
    var expected =
        new ItineraryExpectation()
            .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
            .withMaxDuration(Duration.ofMinutes(20))
            .withLeg(new TransitLegExpectation().withRouteShortName("10"));

    var result = ItineraryExpectation.allItinerariesMatching(expected).evaluate(plan);
    var error = assertThrows(ItineraryAssertionError.class, result::assertMatches);

    assertThat(error.result()).isSameInstanceAs(result);
    assertThat(result.matchingMode()).isEqualTo(TransitLegMatching.EXACT_ORDERED);
    assertThat(result.expectedLegs()).containsExactly(List.of("route '[10]'"));
    assertThat(result.itineraryCriteria()).containsExactly("Maximum itinerary duration: PT20M");
    assertThat(result.itineraryResults()).hasSize(2);
    assertThat(result.itineraryResults().get(0).isSuccess()).isTrue();
    assertThat(result.itineraryResults().get(1).isSuccess()).isFalse();
    assertThat(error.getMessage()).contains("Every itinerary must match [failed]");
    assertThat(error.getMessage()).contains("Itinerary 1 matched");
    assertThat(error.getMessage()).contains("Itinerary 2 failed");
    assertThat(error.getMessage()).contains("route '[10]'");
    assertThat(error.getMessage()).contains("Itinerary duration PT30M exceeds PT20M");
  }

  @Test
  void absentAndEmptyResultsHaveDistinctSemantics() {
    var empty = tripPlan();
    assertThat(new ItineraryExpectation().evaluate(empty).matches()).isFalse();
    assertThat(
            ItineraryExpectation.allItinerariesMatching(new ItineraryExpectation())
                .evaluate(empty)
                .matches())
        .isFalse();
    assertThat(
            ItineraryExpectation.noItineraryMatching(new ItineraryExpectation())
                .evaluate(empty)
                .matches())
        .isTrue();
    assertThat(PlanExpectation.itineraryCountBetween(0, 0).evaluate(empty).matches()).isTrue();
    var walking = tripPlan(itinerary(walkLeg(Duration.ofMinutes(10))));
    var noTransit =
        new ItineraryExpectation().withTransitLegMatching(TransitLegMatching.EXACT_ORDERED);
    assertThat(noTransit.evaluate(walking).matches()).isTrue();
    assertThat(ItineraryExpectation.noItineraryMatching(noTransit).evaluate(walking).matches())
        .isFalse();
    assertThat(PlanExpectation.itineraryCountBetween(0, 0).evaluate(walking).matches()).isFalse();
  }

  private static TripPlan tripPlan(Itinerary... itineraries) {
    return new TripPlan(List.of(itineraries), "", "");
  }

  private static Itinerary itinerary(Leg... legs) {
    return new Itinerary(List.of(legs), OptionalDouble.empty());
  }

  private static Leg walkLeg(Duration duration) {
    return new Leg(
        place("Walk From"),
        place("Walk To"),
        START,
        START.plus(duration),
        false,
        false,
        LegMode.WALK,
        duration,
        1000,
        Optional.empty(),
        null,
        null,
        List.of(),
        OptionalDouble.empty(),
        null,
        new LegGeometry(GEOMETRY),
        false,
        Optional.empty());
  }

  private static Leg transitLeg(
      String routeShortName,
      String routeLongName,
      LegMode mode,
      Duration duration,
      List<FareProductUse> fareProducts) {
    String idToken = routeShortName != null ? routeShortName : routeLongName;
    return transitLeg(
        routeShortName,
        routeLongName,
        mode,
        duration,
        fareProducts,
        place("From " + idToken),
        place("To " + idToken),
        false);
  }

  private static Leg transitLeg(
      String routeShortName,
      String routeLongName,
      LegMode mode,
      Duration duration,
      List<FareProductUse> fareProducts,
      Place from,
      Place to,
      boolean interlineWithPreviousLeg) {
    String idToken = routeShortName != null ? routeShortName : routeLongName;
    Route route =
        Route.builder()
            .setId("route-" + idToken)
            .setGtfsId("gtfs-" + idToken)
            .setShortName(routeShortName)
            .setLongName(routeLongName)
            .build();

    return new Leg(
        from,
        to,
        START,
        START.plus(duration),
        false,
        interlineWithPreviousLeg,
        mode,
        duration,
        1000,
        Optional.empty(),
        route,
        new Trip("trip-" + idToken, Optional.empty(), Optional.empty()),
        fareProducts,
        OptionalDouble.empty(),
        new Agency("agency", "Test Agency"),
        new LegGeometry(GEOMETRY),
        false,
        Optional.empty());
  }

  private static Place place(String name) {
    return new Place(
        name, 10.0f, 10.0f, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static Place place(String name, String stopGtfsId, String stopCode) {
    Stop stop =
        new Stop(
            name,
            stopGtfsId,
            Optional.ofNullable(stopCode),
            Optional.empty(),
            Optional.empty(),
            null);
    return new Place(
        name,
        10.0f,
        10.0f,
        Optional.of(stop),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static FareProductUse fare(String riderCategoryId, String mediumId) {
    return new FareProductUse(
        "fare-" + riderCategoryId + "-" + mediumId,
        new FareProductUse.FareProduct(
            "product-" + riderCategoryId + "-" + mediumId,
            "Test fare",
            null,
            Optional.of(new FareProductUse.FareProduct.RiderCategory(riderCategoryId, "Rider")),
            Optional.of(new FareProductUse.FareProduct.FareMedium(mediumId, "Medium"))));
  }

  private static FareProductUse fare(Float amount, String riderCategoryId, String mediumId) {
    return new FareProductUse(
        "fare-" + riderCategoryId + "-" + mediumId,
        new FareProductUse.FareProduct(
            "product-" + riderCategoryId + "-" + mediumId,
            "Test fare",
            new Money(BigDecimal.valueOf(amount), new Currency(2, "USD")),
            Optional.of(new FareProductUse.FareProduct.RiderCategory(riderCategoryId, "Rider")),
            Optional.of(new FareProductUse.FareProduct.FareMedium(mediumId, "Medium"))));
  }
}
