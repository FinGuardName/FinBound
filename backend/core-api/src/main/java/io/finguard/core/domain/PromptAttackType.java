package io.finguard.core.domain;

/** Prompt 공격 유형. docs/06-common-conventions.md §19. */
public enum PromptAttackType {
    IGNORE_PREVIOUS_INSTRUCTION,
    POLICY_BYPASS,
    SYSTEM_PROMPT_EXTRACTION,
    CROSS_CUSTOMER_ACCESS,
    UNAUTHORIZED_TOOL_REQUEST,
    UNKNOWN_PROMPT_ATTACK,
}
