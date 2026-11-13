package com.dotlo.line4thon_18team.user.controller;

import com.dotlo.line4thon_18team.auth.LoginUser;
import com.dotlo.line4thon_18team.user.dto.MyStatsResponse;
import com.dotlo.line4thon_18team.user.dto.NicknameCheckResponse;
import com.dotlo.line4thon_18team.user.dto.ProfileUpdateRequest;
import com.dotlo.line4thon_18team.user.dto.SignupRequest;
import com.dotlo.line4thon_18team.user.dto.UserResponse;
import com.dotlo.line4thon_18team.user.dto.WithdrawalPreviewResponse;
import com.dotlo.line4thon_18team.user.service.UserService;
import com.dotlo.line4thon_18team.user.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final WithdrawalService withdrawalService;

    // 내 정보 (로그인 필요)
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@LoginUser Long userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    // 가입 마무리 (약관 동의 + 닉네임 + 캐릭터). 구글 로그인 응답의 signupCompleted 가 false 면 부른다
    @PostMapping("/me/signup")
    public ResponseEntity<UserResponse> signup(@LoginUser Long userId, @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(userService.signup(userId, request));
    }

    // 프로필 수정 (닉네임, 한 줄 소개, 캐릭터)
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@LoginUser Long userId,
                                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    // 닉네임 중복 확인. 형식이 틀리면 400
    @GetMapping("/nickname/check")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@LoginUser Long userId,
                                                               @RequestParam String nickname) {
        return ResponseEntity.ok(userService.checkNickname(userId, nickname));
    }

    // 마이 홈 숫자 (저장한 장소, 저장한 코스, 다녀온 여행)
    @GetMapping("/me/stats")
    public ResponseEntity<MyStatsResponse> getMyStats(@LoginUser Long userId) {
        return ResponseEntity.ok(userService.getMyStats(userId));
    }

    // 탈퇴 전 안내 (같이 삭제될 여행, 방장이 넘어갈 여행, 빠질 여행)
    @GetMapping("/me/withdrawal")
    public ResponseEntity<WithdrawalPreviewResponse> previewWithdrawal(@LoginUser Long userId) {
        return ResponseEntity.ok(withdrawalService.preview(userId));
    }

    // 회원 탈퇴 → 204
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@LoginUser Long userId) {
        withdrawalService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
