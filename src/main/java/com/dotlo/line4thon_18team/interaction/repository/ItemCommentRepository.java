package com.dotlo.line4thon_18team.interaction.repository;

import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemCommentRepository extends JpaRepository<ItemComment, Long> {
    @EntityGraph(attributePaths = "user")
    List<ItemComment> findAllByTripIdAndItemIdInOrderByCreatedAtAscIdAsc(Long tripId, Collection<String> itemIds);

    // 여행 잠금 전에 어느 여행인지만 본다. 댓글 엔티티를 미리 읽어 두지 않으려고 (잠금 뒤에 새로 읽는다)
    @Query("select c.trip.id from ItemComment c where c.id = :id")
    Optional<Long> findTripIdById(@Param("id") Long id);

    List<ItemComment> findAllByParentId(Long parentId);
    void deleteAllByTripId(Long tripId);
}
