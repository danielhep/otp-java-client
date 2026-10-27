package org.opentripplanner.assertions;

public final class ItineraryAssertionError extends AssertionError {
  private final PlanMatchResult result;

  public ItineraryAssertionError(PlanMatchResult result) {
    super(result.describe());
    this.result = result;
  }

  public PlanMatchResult result() {
    return result;
  }
}
