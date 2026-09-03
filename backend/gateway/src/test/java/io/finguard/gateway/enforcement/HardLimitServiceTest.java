package io.finguard.gateway.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HardLimitServiceTest {

    @Test
    void allowsRequestsUntilCapacityIsConsumed() {
        HardLimitService service = new HardLimitService(2);

        assertThat(service.isExceeded("LOAN-AGENT-01")).isFalse();
        assertThat(service.isExceeded("LOAN-AGENT-01")).isFalse();
    }

    @Test
    void exceedsAfterCapacityIsConsumed() {
        HardLimitService service = new HardLimitService(1);

        assertThat(service.isExceeded("LOAN-AGENT-01")).isFalse();
        assertThat(service.isExceeded("LOAN-AGENT-01")).isTrue();
    }

    @Test
    void countsPerAgentIndependently() {
        HardLimitService service = new HardLimitService(1);

        assertThat(service.isExceeded("LOAN-AGENT-01")).isFalse();
        assertThat(service.isExceeded("LOAN-AGENT-02")).isFalse();
        assertThat(service.isExceeded("LOAN-AGENT-01")).isTrue();
    }
}
