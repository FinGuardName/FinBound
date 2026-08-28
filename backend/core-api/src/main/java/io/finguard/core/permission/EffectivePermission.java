package io.finguard.core.permission;

import java.util.Set;

import io.finguard.core.domain.DataType;
import io.finguard.core.domain.Tool;

/**
 * 현재 Case에서 Agent에게 실제로 유효한 권한.
 *
 * <p>{@code Agent Effective Permission ⊆ Employee Authority} 가 항상 성립해야 한다 ({@code AGENTS.md}).
 */
public record EffectivePermission(Set<Tool> allowedTools, Set<DataType> allowedData) {
}
