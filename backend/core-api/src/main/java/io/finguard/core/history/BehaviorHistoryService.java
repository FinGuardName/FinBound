package io.finguard.core.history;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.finguard.core.domain.AuditStatus;
import io.finguard.core.repository.AuditEventRepository;

/** 완료된 Business Audit만 AI용 행동 이력으로 투영한다. */
@Service
public class BehaviorHistoryService {

    private static final Pattern WINDOW_PATTERN = Pattern.compile("^([1-9]\\d*)([smhd])$");

    private final AuditEventRepository auditEvents;
    private final Clock clock;

    public BehaviorHistoryService(AuditEventRepository auditEvents, Clock clock) {
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BehaviorHistoryResponse findCompletedEvents(String agentId, String windowValue) {
        Duration window = parseWindow(windowValue);
        Instant cutoff;
        try {
            cutoff = clock.instant().minus(window);
        } catch (DateTimeException | ArithmeticException exception) {
            throw InvalidBehaviorHistoryWindowException.invalid();
        }

        List<BehaviorHistoryResponse.CompletedEvent> completedEvents =
                auditEvents
                        .findByAgentIdAndStatusAndRequestedAtGreaterThanEqualOrderByRequestedAtDesc(
                                agentId, AuditStatus.COMPLETED, cutoff)
                        .stream()
                        .map(BehaviorHistoryResponse.CompletedEvent::from)
                        .toList();
        return new BehaviorHistoryResponse(agentId, windowValue, completedEvents);
    }

    private Duration parseWindow(String value) {
        Matcher matcher = WINDOW_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw InvalidBehaviorHistoryWindowException.invalid();
        }

        try {
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw InvalidBehaviorHistoryWindowException.invalid();
            };
        } catch (NumberFormatException | ArithmeticException exception) {
            throw InvalidBehaviorHistoryWindowException.invalid();
        }
    }
}
