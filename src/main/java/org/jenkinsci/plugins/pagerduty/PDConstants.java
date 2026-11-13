package org.jenkinsci.plugins.pagerduty;

/**
 * Created by alex on 10/07/17.
 */
class PDConstants {
    public enum Severity {
        CRITICAL("critical"), ERROR("error"), WARNING("warning"), INFO("info");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum ValidationResult {
        DO_NOTHING, DO_TRIGGER, DO_RESOLVE
    }
}
