package com.github.liyibo1110.feign;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Retryer接口的默认实现。
 * @author liyibo
 * @date 2026-04-29 11:16
 */
public class DefaultRetryer implements Retryer {

    private final int maxAttempts;
    private final long period;
    private final long maxPeriod;
    int attempt;
    long sleptForMillis;

    public DefaultRetryer() {
        this(100, SECONDS.toMillis(1), 5);
    }

    public DefaultRetryer(long period, long maxPeriod, int maxAttempts) {
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.maxAttempts = maxAttempts;
        this.attempt = 1;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public void continueOrPropagate(RetryableException e) {
        if (attempt++ >= maxAttempts)
            throw e;

        long interval;
        if (e.retryAfter() != null) {
            interval = e.retryAfter() - currentTimeMillis();
            if (interval > maxPeriod)
                interval = maxPeriod;

            if (interval < 0)
                return;

        } else {
            interval = nextMaxInterval();
        }

        try {
            Thread.sleep(interval);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw e;
        }
        sleptForMillis += interval;
    }

    long nextMaxInterval() {
        long interval = (long) (period * Math.pow(1.5, attempt - 1));
        return Math.min(interval, maxPeriod);
    }

    @Override
    public Retryer clone() {
        return new DefaultRetryer(period, maxPeriod, maxAttempts);
    }
}
