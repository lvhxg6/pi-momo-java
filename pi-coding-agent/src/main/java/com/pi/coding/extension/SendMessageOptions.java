package com.pi.coding.extension;

/**
 * Options for sending messages.
 *
 * @param triggerTurn whether to trigger a new turn
 * @param deliverAs   how to deliver the message ("steer", "followUp", or "nextTurn")
 */
public record SendMessageOptions(
    Boolean triggerTurn,
    String deliverAs
) {

    /**
     * Delivery mode for messages.
     */
    public static final String DELIVER_AS_STEER = "steer";
    public static final String DELIVER_AS_FOLLOW_UP = "followUp";
    public static final String DELIVER_AS_NEXT_TURN = "nextTurn";

    /**
     * Builder for creating SendMessageOptions instances.
     */
    public static class Builder {
        private Boolean triggerTurn;
        private String deliverAs;

        public Builder triggerTurn(Boolean triggerTurn) {
            this.triggerTurn = triggerTurn;
            return this;
        }

        public Builder deliverAs(String deliverAs) {
            this.deliverAs = deliverAs;
            return this;
        }

        public SendMessageOptions build() {
            return new SendMessageOptions(triggerTurn, deliverAs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
