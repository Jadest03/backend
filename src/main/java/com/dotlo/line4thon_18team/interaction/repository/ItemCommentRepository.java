package com.dotlo.line4thon_18team.interaction.repository;

import com.dotlo.line4thon_18team.interaction.entity.ItemComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItemCommentRepository extends JpaRepository<ItemComment, Long> {
    @EntityGraph(attributePaths = "user")
    List<ItemComment> findAllByTripIdAndItemIdInOrderByCreatedAtAscIdAsc(Long tripId, Collection<String> itemIds);

    List<ItemComment> findAllByParentId(Long parentId);
    void deleteAllByTripId(Long tripId);
}
