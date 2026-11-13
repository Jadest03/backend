package com.dotlo.line4thon_18team.user.service;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.place.repository.SavedPlaceRepository;
import com.dotlo.line4thon_18team.trip.repository.TripRepository;
import com.dotlo.line4thon_18team.user.dto.MyStatsResponse;
import com.dotlo.line4thon_18team.user.dto.NicknameCheckResponse;
import com.dotlo.line4thon_18team.user.dto.ProfileUpdateRequest;
import com.dotlo.line4thon_18team.user.dto.SignupRequest;
import com.dotlo.line4thon_18team.user.dto.UserResponse;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // 한글(자음/모음만 있는 것도 허용), 영문, 숫자 2~12자. 공백, 특수문자는 안 됨
    private static final Pattern NICKNAME = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]{2,12}");

    private final UserRepository userRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final TripRepository tripRepository;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    // 지금 내 닉네임이면 쓸 수 있다고 본다 (수정 화면에서 그대로 저장할 수 있게)
    public NicknameCheckResponse checkNickname(Long userId, String nickname) {
        validateNickname(nickname);
        return new NicknameCheckResponse(isAvailable(userId, nickname));
    }

    // 구글 로그인 다음 단계. 필수 약관 3개에 모두 동의해야 하고, 한 번만 할 수 있다
    @Transactional
    public UserResponse signup(Long userId, SignupRequest request) {
        User user = findUser(userId);
        if (user.isSignupCompleted()) {
            throw new BusinessException(ErrorCode.ALREADY_SIGNED_UP);
        }
        if (!request.agreements().allRequired()) {
            throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED);
        }
        validateNewNickname(userId, request.nickname());
        user.completeSignup(request.nickname(), request.characterId(), request.agreements().location(),
                LocalDateTime.now());
        return flushNickname(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        validateNewNickname(userId, request.nickname());
        User user = findUser(userId);
        String bio = request.bio() == null || request.bio().isBlank() ? null : request.bio().trim();
        user.updateMyProfile(request.nickname(), bio, request.characterId());
        return flushNickname(user);
    }

    // 찜 기능(BE2)이 생기기 전까지 저장한 코스는 0
    public MyStatsResponse getMyStats(Long userId) {
        findUser(userId);
        return new MyStatsResponse(
                savedPlaceRepository.countInMyTrips(userId),
                0,
                tripRepository.countEndedMyTrips(userId, LocalDate.now()));
    }

    private void validateNewNickname(Long userId, String nickname) {
        validateNickname(nickname);
        if (!isAvailable(userId, nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
    }

    private UserResponse flushNickname(User user) {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 다른 사람이 같은 닉네임을 먼저 저장한 경우 (nickname UNIQUE)
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
        return UserResponse.from(user);
    }

    private boolean isAvailable(Long userId, String nickname) {
        return userRepository.findByNickname(nickname)
                .map(owner -> owner.getId().equals(userId))
                .orElse(true);
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())   // 탈퇴한 사람의 남은 토큰으로는 내 정보가 안 보이게
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
