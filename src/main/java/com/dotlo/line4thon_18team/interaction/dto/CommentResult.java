package com.dotlo.line4thon_18team.interaction.dto;

import com.dotlo.line4thon_18team.interaction.entity.ItemComment;

import java.time.LocalDateTime;

public record CommentResult(Long id, String itemId, String content, Author author, LocalDateTime createdAt) {
    public record Author(Long id, String name, String profileImageUrl) {}

    public static CommentResult from(ItemComment comment) {
        var user = comment.getUser();
        return new CommentResult(comment.getId(), comment.getItemId(), comment.getContent(),
                new Author(user.getId(), user.getName(), user.getProfileImageUrl()), comment.getCreatedAt());
    }
}
