package org.opentripplanner.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.model.Coordinate;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.parameters.TripPlanParameters;

class OtpApiClientGraphQlErrorTest {

  @Test
  void planReportsGraphQlErrorsFromSuccessfulHttpResponse() {
    var responseBody =
        """
        {"errors":[{"message":"Exception while fetching data (/plan) : toIndex = 10"}],"data":{"plan":null}}
        """;
    var httpClient =
        new CloseableHttpClient() {
          @Override
          protected CloseableHttpResponse doExecute(
              HttpHost target, ClassicHttpRequest request, HttpContext context) {
            var response = new BasicClassicHttpResponse(200);
            response.setEntity(new StringEntity(responseBody, ContentType.APPLICATION_JSON));
            return CloseableHttpResponse.adapt(response);
          }

          @Override
          public void close(CloseMode closeMode) {}

          @Override
          public void close() {}
        };
    var client =
        OtpApiClient.builder()
            .graphQLUri("https://example.com/otp/gtfs/v1")
            .timeZone(ZoneId.of("Europe/Oslo"))
            .httpClient(httpClient)
            .build();
    var request =
        TripPlanParameters.builder()
            .withFrom(new Coordinate(59.9, 10.7))
            .withTo(new Coordinate(59.8, 10.8))
            .withTime(LocalDateTime.of(2026, 9, 25, 12, 0))
            .withModes(RequestMode.TRANSIT)
            .build();

    var error = assertThrows(IOException.class, () -> client.plan(request));
    assertTrue(error.getMessage().contains("toIndex = 10"));
  }
}
