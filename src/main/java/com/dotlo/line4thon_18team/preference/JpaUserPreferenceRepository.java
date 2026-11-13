package com.dotlo.line4thon_18team.preference;

import com.dotlo.line4thon_18team.global.error.BusinessException;
import com.dotlo.line4thon_18team.global.error.ErrorCode;
import com.dotlo.line4thon_18team.user.entity.User;
import com.dotlo.line4thon_18team.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 사용자 기본 취향 DB 저장소. 같은 사용자의 값이 있으면 그 줄을 고친다. */
@Transactional
public class JpaUserPreferenceRepository implements UserPreferenceRepository {

    private final UserPreferenceEntityRepository entities;
    private final UserRepository users;

    public JpaUserPreferenceRepository(UserPreferenceEntityRepository entities, UserRepository users) {
        this.entities = entities;
        this.users = users;
    }

    @Override
    public StoredUserPreference save(StoredUserPreference preference) {
        UserPreferenceEntity entity = entities.findByUserId(preference.userId())
                .orElseGet(() -> new UserPreferenceEntity(findUser(preference.userId())));
        entity.update(preference.profile(), preference.rawText(), preference.updatedAt());
        return entities.save(entity).toStored();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredUserPreference> findByUser(long userId) {
        return entities.findByUserId(userId).map(UserPreferenceEntity::toStored);
    }

    @Override
    public void deleteByUser(long userId) {
        entities.deleteByUserId(userId);
    }

    private User findUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
