package com.ssafy.c206.backend.common.security;

import java.io.Serial;
import java.io.Serializable;

public record AuthenticatedUser(Long userId, String email) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
}
