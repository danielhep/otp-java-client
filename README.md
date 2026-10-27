## OTP GraphQL API Java Client

This library provides an easy way to access OTP's GTFS GraphQL API with Java.

### OTP version compatibility

Not every version of this client works with every version of OTP. Check the
following table to select the correct artifact:

| OTP version | Client version |
|-------------|----------------|
| 2.4.0       | =< 0.0.31      |
| 2.5.0       | 0.1.0 - 0.1.13 |
| 2.7.0       | => 1.0.0       |

### Backwards compatibility

Until version 2.0.0 this library will not be strictly following semantic versioning as a lot 
of changes are planned.

### Installation

[![Maven Central](https://img.shields.io/maven-central/v/org.opentripplanner/otp-client.svg)](https://mvnrepository.com/artifact/org.opentripplanner/otp-client)

It's deployed to Maven Central so add it to your project like this:

```xml
<dependency>
   <groupId>org.opentripplanner</groupId>
   <artifactId>otp-client</artifactId>
   <version>${LATEST_VERSION}</version>
</dependency>
```

If you want itinerary assertion helpers for tests, add the assertions module too:

```xml
<dependency>
   <groupId>org.opentripplanner</groupId>
   <artifactId>otp-client-assertions</artifactId>
   <version>${LATEST_VERSION}</version>
   <scope>test</scope>
</dependency>
```

### Usage

```java
Coordinate ORIGIN = new Coordinate(52.4885, 13.3398);
Coordinate DEST = new Coordinate(52.5211, 13.4106);
OtpApiClient client = new OtpApiClient(ZoneId.of("Europe/Berlin"), "https://example.com");

var result = client.plan(
      TripPlanParameters.builder()
        .withFrom(ORIGIN)
        .withTo(DEST)
        .withTime(LocalDateTime.now())
        .withModes(Set.of(RequestMode.TRANSIT))
        .build()
    );
```

To create a more customized client, for example to specify the API path or some default Headers in the HTTP client, you can also use the builder API of the client:
```java
OtpApiClient client = OtpApiClient.builder()
        .graphQLUri("https://example.com/custom/path")
        .timeZone(ZoneId.of("Europe/Berlin"))
        .httpClient(customHttpClient)
        .build();
```

For more examples take a look at [`IntegrationTest.java`](https://github.com/opentripplanner/otp-java-client/blob/main/client/src/test/java/org/opentripplanner/IntegrationTest.java).

### Evaluating itinerary expectations (3.x)

The 3.x assertions module separates leg conditions, itinerary conditions, and
asserting the evaluated result. This replaces the old `ItineraryAssertions`
fluent interface without compatibility aliases.

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

All conditions in one `ItineraryExpectation` apply to the same itinerary.
By default, at least one itinerary must match. To require every itinerary to
match, use `ItineraryExpectation.allItinerariesMatching(expected).evaluate(plan)`
and call `assertMatches()` on the result. This also requires a nonempty plan
and preserves diagnostics for every itinerary.
`withMaxDuration` on a leg limits that leg; on an itinerary it limits elapsed
time from first departure to final arrival, including walking and waiting.
Limits are inclusive. An empty itinerary expectation matches any returned
itinerary; it does not match an empty response. A leg expectation needs at least
one condition and is copied when added to an itinerary expectation.

All matching modes use distinct transit legs and ignore non-transit legs:

| Mode | Order | Extra transit legs |
| --- | --- | --- |
| `EXACT_ORDERED` | Required | Prohibited |
| `ORDERED_SUBSEQUENCE` | Required | Allowed anywhere |
| `UNORDERED_SUBSET` (default) | Unconstrained | Allowed |

With `EXACT_ORDERED`, no leg expectations means no transit legs are allowed.
Unordered matching finds a valid assignment even when expectations overlap.
`withInterlinedWithPreviousLeg(boolean)` is the only interlining method: omitted
is unconstrained, true requires interlining, false prohibits it. It describes
continuation from the actual preceding transit leg, including an extra leg when
subset matching allows one.

Compose `PlanExpectation` values through `PlanExpectation.anyOf(...)`,
`PlanExpectation.allOf(...)`, and
`PlanExpectation.itineraryCountBetween(minimum, maximum)`. Use
`ItineraryExpectation.noItineraryMatching(expected)` to require no itinerary
to match an expectation. Separate itinerary expectations may
match different itineraries. All children are evaluated and retained in
`PlanMatchResult.children()`, even when one alternative already passed.
`itineraryResults()` retains every itinerary's result in response order.
A mismatch is data; only `assertMatches()` throws. Invalid definitions still
throw during configuration/evaluation. `ItineraryAssertionError.result()` gives
the full evaluation, including mode, criteria, response, and child diagnostics.

### Releasing

```sh
git tag 0.0.4 -m "Release 0.0.4"
mvn clean deploy
git push --tags
```
