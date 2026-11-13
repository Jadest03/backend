package com.dotlo.line4thon_18team.auth;

import com.dotlo.line4thon_18team.auth.dto.LoginResponse;
import com.dotlo.line4thon_18team.auth.dto.TokenResponse;
import com.dotlo.line4thon_18team.auth.google.GoogleOAuthClient;
import com.dotlo.line4thon_18team.auth.google.GoogleUser;
import com.dotlo.line4thon_18team.auth.jwt.JwtProvider;
import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 로그인 · 토큰 재발급 · 로그아웃.
 * refresh 토큰은 사용자당 1개만 유효하다. (다른 기기에서 로그인하면 이전 기기의 refresh 토큰은 못 씀)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    /**
     * 구글 code 로 로그인한다. 처음이면 가입, 이미 있으면 프로필만 최신으로 갱신.
     */
    public LoginResponse login(String code) {
        GoogleUser googleUser = googleOAuthClient.login(code);

        Optional<User> existing = userRepository.findByGoogleSub(googleUser.sub());
        boolean newUser = existing.isEmpty();

        User user = existing.orElseGet(() -> userRepository.save(User.builder()
                .googleSub(googleUser.sub())
                .email(googleUser.email())
                .name(googleUser.name())
                .profileImageUrl(googleUser.picture())
                .build()));
        if (!newUser) {
            user.updateProfile(googleUser.email(), googleUser.name(), googleUser.picture());
        }

        TokenResponse tokens = issueTokens(user);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), newUser, user.isSignupCompleted());
    }

    /**
     * refresh 토큰으로 새 토큰을 발급한다. 쓴 refresh 토큰은 바로 무효가 된다. (재사용 방지)
     */
    public TokenResponse refresh(String refreshToken) {
        User user = findUserByValidRefreshToken(refreshToken);
        return issueTokens(user);
    }

    /**
     * 로그아웃: 저장된 refresh 토큰을 지워서 더 이상 재발급을 못 받게 한다.
     * access 토큰은 만료(30분)될 때까지 남아 있으므로 프론트에서도 지워야 한다.
     */
    public void logout(String refreshToken) {
        User user = findUserByValidRefreshToken(refreshToken);
        user.clearRefreshToken();
    }

    // ---------- 내부 ----------

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        user.updateRefreshTokenHash(hash(refreshToken));
        return new TokenResponse(accessToken, refreshToken);
    }

    // 서명·만료 확인 + "지금 저장된 refresh 토큰과 같은지" 확인 (로그아웃했거나 이미 쓴 토큰이면 실패)
    private User findUserByValidRefreshToken(String refreshToken) {
        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (user.getRefreshTokenHash() == null || !user.getRefreshTokenHash().equals(hash(refreshToken))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return user;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);   // 64자
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
