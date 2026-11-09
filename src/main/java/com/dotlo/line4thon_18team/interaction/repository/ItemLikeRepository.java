package com.dotlo.line4thon_18team.interaction.repository;

import com.dotlo.line4thon_18team.interaction.entity.ItemLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemLikeRepository extends JpaRepository<ItemLike, Long> {
    boolean existsByTripIdAndItemIdAndUserId(Long tripId, String itemId, Long userId);
    long countByTripIdAndItemId(Long tripId, String itemId);
    long deleteByTripIdAndItemIdAndUserId(Long tripId, String itemId, Long userId);
}
