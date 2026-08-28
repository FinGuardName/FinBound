package io.finguard.core.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 업무별 표준 Tool/Data 범위. {@code docs/01-feature-spec.md} F03.
 *
 * <p>Employee Authority를 <strong>대체하지 않는다.</strong> 둘의 교집합이 권한 계산의 일부다.
 * Agent가 제출한 Tool/Data 목록으로 이 값을 바꾸지 않는다.
 */
@Entity
@Table(name = "permission_templates")
public class PermissionTemplate {

    /** 예: {@code LOAN_REVIEW_STANDARD}. docs/06-common-conventions.md §2. */
    @Id
    @Column(name = "template_id", nullable = false, length = 64)
    private String templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;

    @ElementCollection
    @CollectionTable(
            name = "permission_template_allowed_tools",
            joinColumns = @JoinColumn(name = "template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tool", nullable = false, length = 64)
    private Set<Tool> allowedTools = EnumSet.noneOf(Tool.class);

    @ElementCollection
    @CollectionTable(
            name = "permission_template_allowed_data",
            joinColumns = @JoinColumn(name = "template_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 64)
    private Set<DataType> allowedData = EnumSet.noneOf(DataType.class);

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PermissionTemplateStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PermissionTemplate() {
        // JPA
    }

    public PermissionTemplate(
            String templateId,
            TaskType taskType,
            Set<Tool> allowedTools,
            Set<DataType> allowedData,
            int defaultDurationMinutes,
            PermissionTemplateStatus status) {
        this.templateId = templateId;
        this.taskType = taskType;
        this.allowedTools = EnumSet.noneOf(Tool.class);
        this.allowedTools.addAll(allowedTools);
        this.allowedData = EnumSet.noneOf(DataType.class);
        this.allowedData.addAll(allowedData);
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.status = status;
    }

    public String getTemplateId() {
        return templateId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public Set<Tool> getAllowedTools() {
        return Collections.unmodifiableSet(allowedTools);
    }

    public Set<DataType> getAllowedData() {
        return Collections.unmodifiableSet(allowedData);
    }

    public int getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    public PermissionTemplateStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public boolean isActive() {
        return status == PermissionTemplateStatus.ACTIVE;
    }
}
