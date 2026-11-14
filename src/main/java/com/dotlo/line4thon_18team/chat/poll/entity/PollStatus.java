package com.dotlo.line4thon_18team.chat.poll.entity;

/** 투표 상태. 구성원이 모두 투표하면 자동으로 CLOSED 가 된다. 따로 마감하는 버튼은 화면에 없다. */
public enum PollStatus {
    OPEN,
    CLOSED
}
