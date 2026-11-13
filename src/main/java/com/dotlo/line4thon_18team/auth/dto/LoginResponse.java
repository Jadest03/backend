package com.dotlo.line4thon_18team.auth.dto;

/**
 * 로그인 응답.
 *
 * @param newUser         처음 로그인한 사용자면 true
 * @param signupCompleted 가입(약관 동의 + 닉네임)을 마쳤으면 true. false 면 가입 화면으로 보낸다
 *                        (가입 중에 앱을 끄면 다음 로그인은 newUser=false, signupCompleted=false)
 */
public record LoginResponse(String accessToken, String refreshToken, boolean newUser, boolean signupCompleted) {
}
