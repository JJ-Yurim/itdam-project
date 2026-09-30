package com.ssafy.c206.backend.user.service;

import java.io.Serial;
import java.io.Serializable;

import com.ssafy.c206.backend.user.domain.AuthProvider;

public record PendingSocialRegistration(
		AuthProvider provider,
		String providerId,
		String email) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
