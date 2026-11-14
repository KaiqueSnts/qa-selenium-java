package com.qa.automation.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.automation.models.Booking;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.time.Duration;

/** HTTP client for the public Restful Booker demo API. */
public class BookingClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String username;
    private final String password;

    public BookingClient(String baseUrl, String username, String password) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.baseUri = URI.create(baseUrl.replaceAll("/$", "") + "/");
        this.username = username;
        this.password = password;
    }

    public ApiResponse authenticate() {
        return authenticate(username, password);
    }

    public ApiResponse authenticate(String user, String pass) {
        return send("POST", "auth", Map.of("username", user, "password", pass), null);
    }

    public String createToken() {
        ApiResponse response = authenticate();
        JsonNode body = response.json();
        if (!body.hasNonNull("token")) {
            throw new IllegalStateException("API authentication failed: " + body.path("reason").asText(response.body()));
        }
        return body.get("token").asText();
    }

    public ApiResponse getBookingIds(String firstName) {
        String encodedName = URLEncoder.encode(firstName, StandardCharsets.UTF_8);
        return send("GET", "booking?firstname=" + encodedName, null, null);
    }

    public ApiResponse createBooking(Booking booking) {
        return send("POST", "booking", booking, null);
    }

    public ApiResponse getBooking(int id) {
        return send("GET", "booking/" + id, null, null);
    }

    public ApiResponse updateBooking(int id, Booking booking, String token) {
        return send("PUT", "booking/" + id, booking, token);
    }

    public ApiResponse deleteBooking(int id, String token) {
        return send("DELETE", "booking/" + id, null, token);
    }

    private ApiResponse send(String method, String path, Object body, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Cookie", "token=" + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(toJson(body)));
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body(), objectMapper);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the booking API", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not call the booking API", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize request body", exception);
        }
    }

    public record ApiResponse(int statusCode, String body, ObjectMapper mapper) {
        public JsonNode json() {
            try {
                return mapper.readTree(body);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("API response is not valid JSON", exception);
            }
        }

        public <T> T jsonAs(Class<T> type) {
            try {
                return mapper.readValue(body, type);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not map API response", exception);
            }
        }
    }
}
