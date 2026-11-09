package com.dotlo.line4thon_18team.interaction.repository;

import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemCommentRepository extends JpaRepository<ItemComment, Long> {
    @EntityGraph(attributePaths = {"user", "trip"})
    Optional<ItemComment> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "user")
    List<ItemComment> findAllByTripIdAndItemIdInOrderByCreatedAtAscIdAsc(Long tripId, Collection<String> itemIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ItemComment comment where comment.id = :id and comment.user.id = :userId")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
}
