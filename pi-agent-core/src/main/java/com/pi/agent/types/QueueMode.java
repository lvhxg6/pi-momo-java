package com.pi.agent.types;

/**
 * Controls how messages are dequeued from steering and follow-up queues.
 *
 * <ul>
 *   <li>{@link #ALL} — dequeue all pending messages at once.</li>
 *   <li>{@link #ONE_AT_A_TIME} — dequeue one message per turn.</li>
 * </ul>
 */
public enum QueueMode {

    /** Dequeue all pending messages at once. */
    ALL,

    /** Dequeue one message per turn. */
    ONE_AT_A_TIME
}
