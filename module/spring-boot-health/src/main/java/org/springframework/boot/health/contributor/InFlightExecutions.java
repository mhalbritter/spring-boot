/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.health.contributor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Registry of the health checks which are currently running. A caller which finds a check
 * of the same indicator already in flight joins it instead of starting a second one, so
 * concurrent probes share a single check and, when that check blocks, a single thread.
 * <p>
 * A check which has overrun its own deadline is considered abandoned: the next caller
 * starts a new execution rather than joining a check which may never return. Each
 * execution holds a permit until its check reports that it ended, which caps how many
 * executions of the same indicator can pile up.
 *
 * <pre>
 * timeout 5s, blocking check which ignores interruption
 *
 * t=0  probe 1  start execution 1     [permit 1/4]
 * t=2  probe 2  join execution 1
 * t=5  deadline  both probes report DOWN, execution 1 is interrupted but keeps its
 *                thread and therefore its permit
 * t=7  probe 3  execution 1 is stale, start execution 2  [permit 2/4]
 * ...  probe n  no permit left, reports DOWN
 * </pre>
 * <p>
 * How much the cap is worth depends on how faithfully a check reports its end. A blocking
 * check ends when its thread returns, so one which ignores interruption cannot be
 * restarted without limit. A reactive check ends when it is cancelled at the deadline,
 * which is the only signal a reactive source gives, so one which ignores cancellation
 * returns its permit while its work continues and can be restarted on every probe.
 * <p>
 * Every caller of an execution observes the same result at the same moment, because the
 * execution is bounded once, when it starts. Nothing here knows about the individual
 * callers.
 * <p>
 * An ended execution is replaced by the next caller rather than removed, so the size of
 * the execution registry is bounded by the contributors which are registered: at most one
 * entry per indicator name and {@code includeDetails} value.
 *
 * @param <T> type of the running check
 * @author Moritz Halbritter
 */
class InFlightExecutions<T extends InFlightExecutions.Check> {

	static final int MAX_EXECUTIONS_PER_KEY = 4;

	private final Map<Key, Execution<T>> executions = new ConcurrentHashMap<>();

	// Number of checks in flight, keyed like the executions themselves. A slow indicator
	// whose check cannot be stopped keeps holding resources after its timeout has been
	// reported, so without a cap every probe adds another stuck execution until the
	// shared executor is exhausted and unrelated indicators start failing. Callers which
	// ask for details and callers which do not get a budget each, because only the
	// former are authorized: sharing one budget lets an unauthorized caller use up the
	// executions of an authorized one. A key without a check in flight has no entry.
	private final Map<Key, Integer> inFlightCounts = new ConcurrentHashMap<>();

	private final int maxExecutionsPerKey;

	// A deadline needs a monotonic source. A java.time.Clock is wall-clock and can jump
	// backwards or forwards, which would turn a running check stale at once or never.
	private final LongSupplier nanoTime;

	InFlightExecutions() {
		this(MAX_EXECUTIONS_PER_KEY, System::nanoTime);
	}

	InFlightExecutions(int maxExecutionsPerKey, LongSupplier nanoTime) {
		this.maxExecutionsPerKey = maxExecutionsPerKey;
		this.nanoTime = nanoTime;
	}

	/**
	 * Joins the check which is already running for the given key, or starts a new one.
	 * @param key the key of the check
	 * @param timeout the timeout of the check, which defines when it turns stale, or
	 * {@code null} for a check which is joinable until it ends
	 * @param starter creates a new check. Runs under the lock of the key, so it must do
	 * no more than create it: the check is started afterwards, through
	 * {@link Check#start()}.
	 * @return the execution to wait for
	 * @throws TooManyChecksInFlightException if the indicator has too many checks in
	 * flight
	 */
	Execution<T> join(Key key, @Nullable Duration timeout, Supplier<T> starter) throws TooManyChecksInFlightException {
		// Only the decision who runs the check needs the lock of the key. Running it does
		// not, and must not: a check which ends while it is being started would re-enter
		// this registry, and a slow start would hold up every other key in the same bin.
		Created<T> created = new Created<>();
		Execution<T> execution = this.executions.compute(key, (ignored, existing) -> {
			if (existing != null && canJoin(existing)) {
				return existing;
			}
			if (!tryAcquire(key)) {
				// Throwing leaves the mapping as it is, so a check which is still running
				// stays joinable for the next caller.
				throw new TooManyChecksInFlightException(key.indicatorName(), this.maxExecutionsPerKey);
			}
			created.check = create(key, starter);
			return new Execution<>(created.check, deadline(timeout));
		});
		if (created.check != null) {
			start(key, created.check);
		}
		return execution;
	}

	/**
	 * Signals that a check has ended and returns its permit. Must be called once the
	 * check itself has ended, not once its result has been reported: a blocking check
	 * which ignores interruption keeps its thread long after the last caller gave up on
	 * it.
	 * @param key the key of the check
	 */
	void finished(Key key) {
		release(key);
	}

	private boolean canJoin(Execution<T> execution) {
		return !execution.check().hasEnded() && !execution.isStale(this.nanoTime.getAsLong());
	}

	private @Nullable Long deadline(@Nullable Duration timeout) {
		return (timeout != null) ? this.nanoTime.getAsLong() + timeout.toNanos() : null;
	}

	private T create(Key key, Supplier<T> starter) {
		try {
			return starter.get();
		}
		catch (RuntimeException ex) {
			release(key);
			throw ex;
		}
	}

	private void start(Key key, T check) {
		try {
			check.start();
		}
		catch (RuntimeException ex) {
			// Nobody may join a check which never ran.
			this.executions.remove(key);
			release(key);
			throw ex;
		}
	}

	private boolean tryAcquire(Key key) {
		int[] inFlight = new int[1];
		this.inFlightCounts.compute(key, (ignored, count) -> {
			inFlight[0] = (count != null) ? count : 0;
			return (inFlight[0] < this.maxExecutionsPerKey) ? inFlight[0] + 1 : inFlight[0];
		});
		return inFlight[0] < this.maxExecutionsPerKey;
	}

	private void release(Key key) {
		this.inFlightCounts.computeIfPresent(key, (ignored, count) -> (count > 1) ? count - 1 : null);
	}

	/**
	 * A check which is shared by every caller joined to it.
	 */
	interface Check {

		/**
		 * Starts the check. Called once, by the caller which created it, after the
		 * registry knows about it: another caller can therefore join the check before it
		 * has been started, which is why it must be joinable from the moment it is
		 * created.
		 */
		void start();

		/**
		 * Whether the check has ended, which makes it unjoinable.
		 * @return whether the check has ended
		 */
		boolean hasEnded();

	}

	/**
	 * The check which the calling thread created, if it is the one which has to start it.
	 * Carries it out of the registry update.
	 *
	 * @param <T> type of the running check
	 */
	private static final class Created<T extends Check> {

		private @Nullable T check;

	}

	/**
	 * Identifies a check. The {@code includeDetails} flag is part of the key because
	 * {@link HealthIndicator#health(boolean)} can be overridden to run a different check
	 * depending on it, so its result must not be shared with a caller which asked for the
	 * other value.
	 *
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether details are included
	 */
	record Key(String indicatorName, boolean includeDetails) {
	}

	/**
	 * Thrown when an indicator has as many checks in flight as it is allowed to have.
	 */
	static final class TooManyChecksInFlightException extends RuntimeException {

		private TooManyChecksInFlightException(String indicatorName, int maxExecutionsPerKey) {
			super("Health indicator %s already has %d checks in flight".formatted(indicatorName, maxExecutionsPerKey));
		}

	}

	/**
	 * A running check together with the deadline it is bounded by.
	 *
	 * @param <T> type of the running check
	 * @param check the running check
	 * @param deadline the point in time, on the {@code nanoTime} scale, at which the
	 * check turns stale, or {@code null} for a check which stays joinable until it ends,
	 * so that a check which never returns occupies one thread instead of one per caller
	 */
	record Execution<T extends Check>(T check, @Nullable Long deadline) {

		private boolean isStale(long now) {
			return this.deadline != null && now - this.deadline >= 0;
		}

	}

}
