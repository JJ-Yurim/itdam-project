package com.ssafy.c206.backend.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.user.domain.AuthProvider;
import com.ssafy.c206.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	interface UserNicknameRow {
		Long getId();
		String getNickname();
	}

	@Query("select user.id as id, user.nickname as nickname from User user where user.id in :ids")
	List<UserNicknameRow> findNicknamesByIdIn(@Param("ids") Collection<Long> ids);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<User> findByEmail(String email);

	Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
