package io.finguard.core.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.finguard.core.domain.PermissionTemplate;
import io.finguard.core.domain.PermissionTemplateStatus;
import io.finguard.core.domain.TaskType;

public interface PermissionTemplateRepository extends JpaRepository<PermissionTemplate, String> {

    /**
     * 업무 종류별 표준 Template. <strong>상태로 거르지 않는다.</strong>
     *
     * <p>{@code status = ACTIVE} 로 조회하면 비활성 Template 이 "없는 것"이 되어
     * {@code CONTEXT_NOT_FOUND} 가 나간다. 실제로는 존재하지만 꺼져 있는 것이므로
     * {@code PERMISSION_TEMPLATE_INACTIVE} 여야 한다({@code docs/06} §20).
     * 상태 판정은 권한 계산기가 한 곳에서 담당한다.
     *
     * <p>{@code templateId} 순으로 고정해 결과를 결정적으로 만든다 — 같은 taskType 에 Template 이
     * 둘이면 권한 발급이 호출마다 달라질 수 있다.
     */
    Optional<PermissionTemplate> findFirstByTaskTypeOrderByTemplateIdAsc(TaskType taskType);
}
