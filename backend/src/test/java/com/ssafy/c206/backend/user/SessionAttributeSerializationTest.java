package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ObjectStreamClass;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import org.junit.jupiter.api.Test;

class SessionAttributeSerializationTest {

	@Test
	void sessionPayloadsKeepAnExplicitSerializationVersion() {
		assertThat(ObjectStreamClass.lookup(AuthenticatedUser.class).getSerialVersionUID()).isEqualTo(1L);
		assertThat(ObjectStreamClass.lookup(PendingSocialRegistration.class).getSerialVersionUID()).isEqualTo(1L);
	}
}
