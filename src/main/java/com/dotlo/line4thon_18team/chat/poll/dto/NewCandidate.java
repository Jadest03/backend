package com.dotlo.line4thon_18team.chat.poll.dto;

/**
 * 투표를 만들 때 넣는 후보 하나. 투표 후보 만들기(AI)가 채워서 넘긴다.
 *
 * @param tagline 이름 밑 짧은 설명. 없으면 null
 */
public record NewCandidate(String placeId, String name, String tagline) {
}
