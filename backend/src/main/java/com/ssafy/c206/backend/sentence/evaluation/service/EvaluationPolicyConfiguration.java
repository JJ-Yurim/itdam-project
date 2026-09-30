package com.ssafy.c206.backend.sentence.evaluation.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EvaluationPolicyConfiguration {

	@Bean
	EvaluationPolicy evaluationPolicy() {
		return EvaluationPolicy.defaults();
	}
}
