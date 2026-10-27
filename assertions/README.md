# OTP Java Assertions
The Assertions package is a utility for validating quality of results from OTP instances, designed to be used with the Java client's response objects. The assertions are defined through builder APIs that let you specify the itinerary and leg conditions, then assert that the trip plan contains the expected pieces. 

```java
var expected = new ItineraryExpectation()
        .withTransitLegMatching(TransitLegMatching.EXACT_ORDERED)
        .withMaxDuration(Duration.ofHours(1))
        .withLeg(new TransitLegExpectation()
                .withMode("BUS")
                .withRouteShortName("2")
                .withInterlinedWithPreviousLeg(false));

PlanMatchResult result = expected.evaluate(plan);
// Inspect or report both successful and failed results before failing the test.
result.assertMatches();
```

By default, at least one itinerary must match. To require every itinerary to
satisfy the same expectation, use:

```java
ItineraryExpectation.allItinerariesMatching(expected).evaluate(plan).assertMatches();
```

This fails for an empty plan and retains diagnostics for every itinerary.
`PlanExpectation.allOf(...)` instead requires every supplied expectation to match;
each expectation can match a different itinerary.

## API Documentation
The API for building expectations is a series of builder patterns used as building blocks to allow for flexibility in defining the exact conditions you'd like to check for in the OTP plan response. 

### PlanExpectation interface
PlanExpectation is just a functional interface that exposes an `evaluate` method, used to validate the expectation against an OTP Client `TripPlan`. Most PlanExpectation instances you will see in the wild are probably going to be `ItineraryExpectation`s.

It contains a factory function that can be used to create a PlanExpectation to require a certain number of itineraries on a TripPlan with `itineraryCountBetween`.

#### Composing expectations
Use `PlanExpectation.allOf(...)` and `PlanExpectation.anyOf(...)` to compose
expectations. These accept as many PlanExpectations as you want and return one that combines all of them, either requiring all to match or at least one. 

### ItineraryExpectation: Define the expectations for an itinerary
Create a new expectation with `new ItinteraryExpectation()`. The ItineraryExpectation uses a builder pattern to set up the expectations. Although you can specify some conditions on the itinerary itself, such as the max duration, the main purpose of the ItineraryExpectation is to contain leg expectations, validating that the itinerary contains the expected legs. 

Each leg expectation can be added with `withLeg(transitLegExpectation)`, which accepts a TransitLegExpectation, containing the set of criteria that is expected to be true for that leg. 

You can customize how exact the leg expectations need to be matched against the actual itinerary. 

#### TransitLegMatching
- **EXACT_ORDERED**: The strictest setting, this ensures that each leg exists in the itinerary, in order, with no extra transit legs (walking, biking, etc legs are okay).
- **ORDERED_SUBSEQUENCE**: Compared to EXACT_ORDERED, this removes the restriction on extra legs. It only requires that the expected legs exist in order.
- **UNORDERED_SUBSET**: The loosest setting, it simply requires that each expected leg exists in the actual itinerary. It uses a maximum matching algorithm to ensure all legs are matched with an expectation, if one is available. 

#### Evaluate against a real TripPlan
Evaluate a real life OTP TripPlan object (returned by the OTP Client library) against your shiny new `ItineraryExpectation` by calling the `.evaluate(TripPlan)` method. It will return a PlanMatchResult which you can use to dig into the successes and failures. 

By default, only one itinerary in the trip plan needs to match in order for the check to pass. But you can change that to an expectation that no itineraries will pass, or that every itinerary will pass by using the static functions noItineraryMatching and allItinerariesMatching.

#### Composing ItineraryExpectations 
ItineraryExpectations can be composed as like any PlanExpectation. See the [PlanExpectation Composition](asdf) section for more details.

### TransitLegExpectations: Define the expectations for a leg
This class is used to set up your expectations for a transit leg. Create a new instance and then define your criteria by using the builder methods. 