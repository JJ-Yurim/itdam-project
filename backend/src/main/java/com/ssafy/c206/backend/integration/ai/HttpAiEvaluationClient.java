package com.ssafy.c206.backend.integration.ai;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.common.logging.RequestId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP adapter for the FastAPI sentence-evaluation contract. */
@Component
@ConditionalOnProperty(prefix = "ai.evaluation", name = "client", havingValue = "http")
public class HttpAiEvaluationClient implements AiEvaluationClient {

	private static final String EVALUATION_PATH = "/internal/v1/evaluations";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final AiEvaluationProperties properties;

	public HttpAiEvaluationClient(RestClient aiEvaluationRestClient, ObjectMapper objectMapper,
		AiEvaluationProperties properties) {
		this.restClient = aiEvaluationRestClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public AiEvaluationResult evaluate(AiEvaluationCommand command) {
		UUID correlationId = RequestId.currentOrCreate();
		AiEvaluationHttpRequest request = AiEvaluationHttpRequest.from(
				command, properties.contractVersion(), correlationId);
		try {
			AiEvaluationResult result = restClient.post()
				.uri(EVALUATION_PATH)
				.header(RequestId.HEADER, correlationId.toString())
				.body(request)
				.exchange((clientRequest, clientResponse) -> readResponse(clientResponse));
			AiEvaluationResultValidator.validate(command, result);
			return result;
		} catch (AiEvaluationTransientException | AiEvaluationInvalidResponseException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new AiEvaluationTransientException("AI evaluation transport failed.");
		}
	}

	private AiEvaluationResult readResponse(org.springframework.http.client.ClientHttpResponse response) throws IOException {
		HttpStatusCode status = response.getStatusCode();
		if (status.value() == 502 || status.value() == 422) {
			throw new AiEvaluationInvalidResponseException("AI Service returned an invalid evaluation response.");
		}
		if (status.is5xxServerError()) {
			throw new AiEvaluationTransientException("AI Service returned a retryable error.");
		}
		if (!status.is2xxSuccessful()) {
			throw new AiEvaluationInvalidResponseException("AI Service returned an unexpected status.");
		}
		try {
			return objectMapper.readValue(response.getBody(), AiEvaluationResult.class);
		} catch (IOException | RuntimeException exception) {
			throw new AiEvaluationInvalidResponseException("AI Service response could not be parsed.");
		}
	}

	private record AiEvaluationHttpRequest(
		String contractVersion,
		UUID correlationId,
		long sentenceVersionId,
		String text,
		LocalDate serviceDate,
		List<DailyWordCard> dailyWords
	) {
		private static AiEvaluationHttpRequest from(AiEvaluationCommand command, String contractVersion,
				UUID correlationId) {
			return new AiEvaluationHttpRequest(
				contractVersion,
				correlationId,
				command.sentenceVersionId(),
				command.text(),
				command.serviceDate(),
				command.dailyWords()
			);
		}
	}
}
