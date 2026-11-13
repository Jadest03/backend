package com.dotlo.line4thon_18team.user.repository;

import com.dotlo.line4thon_18team.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 가입한 적이 있는지 확인
    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByNickname(String nickname);
}