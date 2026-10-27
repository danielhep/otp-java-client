package org.opentripplanner.assertions;

import java.util.List;
import org.opentripplanner.client.model.Leg;

/**
 * Result of matching one itinerary against all configured leg criteria.
 *
 * @param successfulMatches Legs fully matching each required criterion set
 * @param partialMatches Legs matching some but not all criteria
 * @param extraMatches Transit legs unmatched in strict matching mode
 * @param errors Matching errors
 */
public record ItineraryMatchResult(
    List<LegMatchingState> successfulMatches,
    List<LegMatchingState> partialMatches,
    List<Leg> extraMatches,
    List<String> errors) {
  public ItineraryMatchResult {
    successfulMatches = List.copyOf(successfulMatches);
    partialMatches = List.copyOf(partialMatches);
    extraMatches = List.copyOf(extraMatches);
    errors = List.copyOf(errors);
  }

  /**
   * @return Whether this result represents a successful match.
   */
  public boolean isSuccess() {
    return errors.isEmpty() && extraMatches.isEmpty() && partialMatches.isEmpty();
  }
}
