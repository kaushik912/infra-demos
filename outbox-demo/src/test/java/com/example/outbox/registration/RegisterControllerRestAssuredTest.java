package com.example.outbox.registration;

import com.example.outbox.outbox.OutboxRepository;
import com.example.outbox.user.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

// Real Postgres + Kafka via Testcontainers (same images/config as
// OutboxIntegrationTest) so the full context — including the KafkaAdmin
// topic auto-creation and the @Scheduled OutboxRelay — has a real broker
// to talk to instead of retrying against a non-existent localhost:9092.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RegisterControllerRestAssuredTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        outboxRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void givenValidRequest_whenRegister_thenReturns201WithUser() {
        // Given
        String body = """
                {"username": "alice", "email": "alice@example.com"}""";

        // When / Then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/register")
        .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("username", equalTo("alice"))
                .body("email", equalTo("alice@example.com"));
    }

    @Test
    void givenUsernameAlreadyTaken_whenRegister_thenReturns409() {
        // Given
        String body = """
                {"username": "bob", "email": "bob@example.com"}""";
        RestAssured.given().contentType(ContentType.JSON).body(body).post("/register");

        // When / Then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/register")
        .then()
                .statusCode(409)
                .body("error", equalTo("username already taken: bob"));
    }

    @Test
    void givenInvalidEmail_whenRegister_thenReturns400() {
        // Given
        String body = """
                {"username": "carol", "email": "not-an-email"}""";

        // When / Then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/register")
        .then()
                .statusCode(400);
    }
}
