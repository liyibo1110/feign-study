package com.github.liyibo1110.feign;

/**
 * 在每次调用Client.execute(Request, Request.Options)时都会被复制。
 * 具体实现可以保存状态，以确定重试操作是否应继续进行。
 * @author liyibo
 * @date 2026-04-29 11:14
 */
public interface Retryer extends Cloneable {

    /**
     * 如果允许重试，则直接返回（可能要backoff一段时间），否则会传播异常。
     */
    void continueOrPropagate(RetryableException e);

    Retryer clone();

    @Deprecated
    class Default extends DefaultRetryer {

        public Default() {
            super();
        }

        public Default(long period, long maxPeriod, int maxAttempts) {
            super(period, maxPeriod, maxAttempts);
        }
    }

    Retryer NEVER_RETRY = new Retryer() {
        @Override
        public void continueOrPropagate(RetryableException e) {
            throw e;
        }

        @Override
        public Retryer clone() {
            return this;
        }
    };
}
