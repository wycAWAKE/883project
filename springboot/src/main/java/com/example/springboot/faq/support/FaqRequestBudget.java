package com.example.springboot.faq.support;

import com.example.springboot.faq.dto.FaqAnswerResponse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Request-local monotonic deadline; no Session, credentials or shared request state. */
public final class FaqRequestBudget {
    private final long deadline;
    private final FaqRequestBudget parent;
    private volatile boolean cancelled;
    private final AtomicReference<Future<?>> task = new AtomicReference<>();
    private final AtomicReference<FaqAnswerResponse> fallback = new AtomicReference<>();
    private volatile String conversationQuestion;
    private volatile boolean conversationNewTopic;

    public FaqRequestBudget(long timeoutMillis) {
        this(timeoutMillis, null);
    }

    private FaqRequestBudget(long timeoutMillis, FaqRequestBudget parent) {
        if (timeoutMillis <= 0 || timeoutMillis > 35000) {
            throw new IllegalArgumentException("FAQ request timeout must be between 1 and 35000 milliseconds.");
        }
        this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        this.parent = parent;
    }

    public FaqRequestBudget child(long timeoutMillis) {
        checkActive();
        return new FaqRequestBudget(timeoutMillis, this);
    }

    public long remainingNanos() {
        long own = Math.max(0, deadline - System.nanoTime());
        return parent == null ? own : Math.min(own, parent.remainingNanos());
    }

    public void checkActive() {
        if (parent != null) parent.checkActive();
        if (cancelled || remainingNanos() == 0) throw new ExpiredException();
    }

    public int socketTimeoutMillis(int maximum) {
        checkActive();
        return (int) Math.min(maximum, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos())));
    }

    public void publishFallback(FaqAnswerResponse response) {
        checkActive();
        fallback.set(response);
    }

    public FaqAnswerResponse getFallback() { return fallback.get(); }
    /** Determined before lookup, so a successful extractive timeout still establishes this turn's topic. */
    public void setConversationContext(String question, boolean newTopic) {
        checkActive();
        conversationNewTopic = newTopic;
        conversationQuestion = question;
    }
    public String getConversationQuestion() { return conversationQuestion; }
    public boolean isConversationNewTopic() { return conversationNewTopic; }
    public void registerTask(Future<?> value) {
        task.set(value);
        if (cancelled) value.cancel(true);
    }

    public void cancel() {
        cancelled = true;
        Future<?> value = task.get();
        if (value != null && !value.isDone()) value.cancel(true);
    }

    public static final class ExpiredException extends RuntimeException {
        public ExpiredException() { super("FAQ request deadline exceeded."); }
    }
}
