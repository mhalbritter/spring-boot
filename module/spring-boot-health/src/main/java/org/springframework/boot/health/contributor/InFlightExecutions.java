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

/**
 * Registry of the health checks which are currently running. A caller which finds a check
 * of the same indicator already in flight joins it instead of starting a second one, so
 * concurrent probes share a single check and, when that check blocks, a single thread.
 * <p>
 * A check which has overrun its own deadline is considered abandoned: the next caller
 * starts a new execution rather than joining a check which may never return. Each
 * execution holds a permit until its check reports that it ended, which caps how many
 * executions of the same indicator can pile up.
 * <p>
 * Only a check with a deadline is shared. A check without one has no point at which it
 * turns stale, so joining it would hand every later caller to a check which may never
 * return: an indicator which hangs once would never answer again, not even when a new
 * check would succeed. Such a check is therefore started through {@link #start} and takes
 * a permit without becoming joinable, so it is bounded by the permits alone.
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
 * Every caller of an execution observes the same result at the same moment.
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

	private final Map<Key, Integer> inFlightCounts = new ConcurrentHashMap<>();

	private final int maxExecutionsPerKey;

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
	 * @param timeout the timeout of the check
	 * @param starter creates a new check
	 * @return the execution to wait for
	 * @throws TooManyChecksInFlightException if the indicator has too many checks in
	 * flight
	 */
	Execution<T> joinOrStart(Key key, Duration timeout, Supplier<T> starter) throws TooManyChecksInFlightException {
		boolean[] created = new boolean[1];
		Execution<T> execution = this.executions.compute(key, (ignored, existing) -> {
			if (existing != null && existing.canJoin(this.nanoTime.getAsLong())) {
				return existing;
			}
			if (!tryAcquire(key)) {
				throw new TooManyChecksInFlightException(key.indicatorName(), this.maxExecutionsPerKey);
			}
			T check = create(key, starter);
			created[0] = true;
			return new Execution<>(check, deadline(timeout));
		});
		if (created[0]) {
			startJoinable(key, execution);
		}
		return execution;
	}

	/**
	 * Starts a check which no other caller can join, taking a permit for it.
	 * @param key the key of the check
	 * @param starter creates the check
	 * @return the started check
	 * @throws TooManyChecksInFlightException if the indicator has too many checks in
	 * flight
	 */
	T start(Key key, Supplier<T> starter) throws TooManyChecksInFlightException {
		if (!tryAcquire(key)) {
			throw new TooManyChecksInFlightException(key.indicatorName(), this.maxExecutionsPerKey);
		}
		T check = create(key, starter);
		try {
			check.start();
		}
		catch (RuntimeException ex) {
			release(key);
			throw ex;
		}
		return check;
	}

	/**
	 * Signals that a check has ended and returns its permit. Must be called once the
	 * check itself has ended.
	 * @param key the key of the check
	 */
	void finished(Key key) {
		release(key);
	}

	private long deadline(Duration timeout) {
		return this.nanoTime.getAsLong() + timeout.toNanos();
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

	private void startJoinable(Key key, Execution<T> execution) {
		try {
			execution.check().start();
		}
		catch (RuntimeException ex) {
			this.executions.remove(key, execution);
			release(key);
			throw ex;
		}
	}

	private boolean tryAcquire(Key key) {
		boolean[] acquired = new boolean[1];
		this.inFlightCounts.compute(key, (ignored, count) -> {
			int inFlight = (count != null) ? count : 0;
			if (inFlight >= this.maxExecutionsPerKey) {
				return inFlight;
			}
			acquired[0] = true;
			return inFlight + 1;
		});
		return acquired[0];
	}

	private void release(Key key) {
		this.inFlightCounts.computeIfPresent(key, (ignored, count) -> (count > 1) ? count - 1 : null);
	}

	/**
	 * A check which is shared by every caller joined to it.
	 */
	interface Check {

		/**
		 * Starts the check. Called once, by the caller which created it.
		 */
		void start();

		/**
		 * Whether the check has ended, which makes it unjoinable.
		 * @return whether the check has ended
		 */
		boolean hasEnded();

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
	 * check turns stale
	 */
	record Execution<T extends Check>(T check, long deadline) {

		/**
		 * Whether another caller may join this execution. A check which has ended has
		 * nothing left to report, and one past its deadline is considered abandoned.
		 * @param now the current time, on the {@code nanoTime} scale
		 * @return whether the execution can be joined
		 */
		private boolean canJoin(long now) {
			return !this.check.hasEnded() && now - this.deadline < 0;
		}

	}

}
