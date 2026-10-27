package org.opentripplanner.assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opentripplanner.client.model.Leg;

/**
 * Internal matching algorithms; unordered matching uses augmenting paths for distinct assignment.
 * Note: in unordered
 */
final class TransitLegMatcher {
  private final List<List<LegCriterion>> criteria;
  private final List<Leg> actual;
  private final LegMatchingState[][] states;

  /**
   * Caller provides the criteria and the actual transit legs, the constructor then precomputes the
   * matching state for every leg for every criterion. The matching state can then be used by the
   * algorithms below without repeating the computations.
   */
  TransitLegMatcher(List<List<LegCriterion>> criteria, List<Leg> actual) {
    this.criteria = criteria;
    this.actual = actual;
    states = new LegMatchingState[criteria.size()][actual.size()];
    for (int e = 0; e < criteria.size(); e++) {
      for (int a = 0; a < actual.size(); a++)
        states[e][a] = evaluate(actual.get(a), criteria.get(e));
    }
  }

  /** Runs the itinerary match algorithm based * */
  ItineraryMatchResult match(TransitLegMatching mode) {
    return switch (mode) {
      case EXACT_ORDERED -> exact();
      case ORDERED_SUBSEQUENCE -> generateMatchResult(ordered(), true);
      case UNORDERED_SUBSET -> generateMatchResult(unordered(), false);
    };
  }

  /** Matches each expectation against the actual leg at the same position. */
  private ItineraryMatchResult exact() {
    var complete = new ArrayList<LegMatchingState>();
    var partial = new ArrayList<LegMatchingState>();
    var errors = new ArrayList<String>();
    for (int e = 0; e < criteria.size(); e++) positional(e, complete, partial, errors);
    var extras =
        actual.size() > criteria.size()
            ? actual.subList(criteria.size(), actual.size())
            : List.<Leg>of();
    if (!extras.isEmpty())
      errors.add("Itinerary contains additional transit legs: " + extras.size());
    return new ItineraryMatchResult(complete, partial, extras, errors);
  }

  /**
   * Records the outcome of one exact-position requirement, including a missing actual leg. This is
   * the simplest calculation.
   */
  private void positional(
      int e, List<LegMatchingState> complete, List<LegMatchingState> partial, List<String> errors) {
    if (e >= actual.size()) {
      errors.add("No transit leg at position " + (e + 1));
      return;
    }
    var state = states[e][e];
    if (state.isFullMatch()) {
      complete.add(state);
      return;
    }
    partial.add(state);
    errors.add("Transit leg at position " + (e + 1) + " does not match criteria");
  }

  /**
   * Builds an expected-index to actual-index assignment for an ordered subsequence. For each
   * expectation index, we find the next matching leg index. To ensure the order is maintained, each
   * subsequent expectation will only match with legs AFTER the previously matched leg. Note: this
   * allows for additional legs to appear between the matched legs.
   */
  private int[] ordered() {
    int[] assignment = emptyAssignment(criteria.size());
    // index of previous matched leg + 1
    int next = 0;
    for (int e = 0; e < criteria.size(); e++) {
      int candidate = nextMatch(e, next);
      if (candidate >= 0) {
        assignment[e] = candidate;
        next = candidate + 1;
      }
    }
    return assignment;
  }

  /** Finds the first full match at or after start, or returns -1 when no candidate remains. */
  private int nextMatch(int expected, int start) {
    for (int a = start; a < actual.size(); a++) if (states[expected][a].isFullMatch()) return a;
    return -1;
  }

  /**
   * Finds a maximum distinct assignment without enforcing order. owner[actual] tracks which
   * requirement currently owns a leg; augment can reassign earlier owners when requirements
   * overlap. Converts that ownership map to expected-index to actual-index form for reporting.
   * Note: unordered executes a maximum matching algorithm, modeling the expectations and legs as a
   * bipartite graph. <a
   * href="https://cp-algorithms.com/graph/kuhn_maximum_bipartite_matching.html">More info on
   * Bipartite Matching</a>
   */
  private int[] unordered() {
    // owner = [rightIndex] = leftIndex
    int[] owner = emptyAssignment(actual.size());
    for (int e = 0; e < criteria.size(); e++) augment(e, owner, new boolean[actual.size()]);
    int[] assignment = emptyAssignment(criteria.size());
    for (int a = 0; a < owner.length; a++) if (owner[a] >= 0) assignment[owner[a]] = a;
    return assignment;
  }

  /**
   * Tries to give this requirement a leg, moving its current owner to another candidate if needed.
   * For example, a wildcard BUS requirement can move from route 10 to route 20 so a route-10-only
   * requirement can take route 10. visited tracks actual legs explored during this assignment
   * attempt, avoiding cycles; unordered starts a fresh visited set for each new requirement.
   * Ownership changes only along a successful reassignment path, so a failed attempt preserves the
   * existing assignment.
   */
  private boolean augment(int expected, int[] owner, boolean[] visited) {
    for (int a = 0; a < actual.size(); a++) {
      if (visited[a] || !states[expected][a].isFullMatch()) continue;
      // we know that the left can be matched with right
      visited[a] = true;
      if (owner[a] < 0 || augment(owner[a], owner, visited)) {
        owner[a] = expected;
        return true;
      }
    }
    return false;
  }

  /**
   * Converts a subset assignment into reportable results. Unassigned requirements produce errors
   * and candidate-level diagnostics; additional actual legs are allowed in both subset modes. The
   * ordered flag only adds travel-order context to error messages.
   */
  private ItineraryMatchResult generateMatchResult(int[] assignment, boolean ordered) {
    var complete = new ArrayList<LegMatchingState>();
    var partial = new ArrayList<LegMatchingState>();
    var errors = new ArrayList<String>();
    for (int e = 0; e < assignment.length; e++) {
      if (assignment[e] >= 0) {
        complete.add(states[e][assignment[e]]);
      } else {
        errors.add(
            "No distinct transit leg matching requirement "
                + (e + 1)
                + (ordered ? " in travel order" : ""));
        partial.addAll(Arrays.stream(states[e]).filter(LegMatchingState::hasAnyMatch).toList());
      }
    }
    return new ItineraryMatchResult(complete, partial, List.of(), errors);
  }

  /** Initializes an assignment or ownership map with -1, the sentinel for an unassigned entry. */
  private static int[] emptyAssignment(int size) {
    int[] assignment = new int[size];
    Arrays.fill(assignment, -1);
    return assignment;
  }

  /** Evaluates every condition on one leg, retaining both satisfied and failed conditions. */
  private static LegMatchingState evaluate(Leg leg, List<LegCriterion> criteria) {
    var state = new LegMatchingState(leg);
    for (var criterion : criteria) {
      if (criterion.test().test(leg)) state.addMatch(criterion.message());
      else state.addFailure(criterion.message());
    }
    return state;
  }
}
