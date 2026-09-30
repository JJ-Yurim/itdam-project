package com.ssafy.c206.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.common.logging.RequestId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

class AiEvaluationRequestCorrelationTest {

	private HttpServer server;

	@AfterEach
	void cleanup() {
		MDC.clear();
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsTheCurrentRequestIdInBothHeaderAndJsonBody() throws Exception {
		AtomicReference<String> capturedHeader = new AtomicReference<>();
		AtomicReference<String> capturedBody = new AtomicReference<>();
		startServer(capturedHeader, capturedBody);
		UUID requestId = UUID.randomUUID();
		MDC.put(RequestId.MDC_KEY, requestId.toString());

		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		AiEvaluationProperties properties = new AiEvaluationProperties(
				"http", baseUrl(), "sentence-evaluation-v1", Duration.ofSeconds(1), Duration.ofSeconds(2));
		HttpAiEvaluationClient client = new HttpAiEvaluationClient(
				RestClient.builder().baseUrl(baseUrl()).build(), objectMapper, properties);

		client.evaluate(command());

		JsonNode requestJson = objectMapper.readTree(capturedBody.get());
		assertThat(capturedHeader).hasValue(requestId.toString());
		assertThat(requestJson.path("correlationId").asText()).isEqualTo(requestId.toString());
	}

	private void startServer(AtomicReference<String> capturedHeader,
			AtomicReference<String> capturedBody) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/v1/evaluations", exchange -> {
			capturedHeader.set(exchange.getRequestHeaders().getFirst(RequestId.HEADER));
			capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = rejectedResponse().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private AiEvaluationCommand command() {
		return new AiEvaluationCommand(42, "correlation test", LocalDate.of(2026, 8, 3), List.of(
				new DailyWordCard(101, "one", "noun", "first"),
				new DailyWordCard(102, "two", "noun", "second"),
				new DailyWordCard(103, "three", "noun", "third"),
				new DailyWordCard(104, "four", "noun", "fourth"),
				new DailyWordCard(105, "five", "noun", "fifth")));
	}

	private String rejectedResponse() {
		return """
				{
				  "safety": {"status": "REJECTED", "reason": "test rejection"},
				  "scores": null,
				  "overallFeedback": null,
				  "moods": [],
				  "usedDailyWords": [],
				  "modelName": "test-model",
				  "promptVersion": "test-prompt",
				  "rubricVersion": "test-rubric"
				}
				""";
	}
}
