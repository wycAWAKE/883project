package com.example.springboot.faq.service;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.llm.ConversationTurn;
import com.example.springboot.faq.support.FaqRequestBudget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.List;
import java.util.function.Function;

/** Bounds HTTP waiting, including a blocked lookup; does not move transaction-local Service callers. */
@Component
public class FaqRequestRunner {
    private final FaqService service;
    private final long timeoutMillis;
    // ponytail: four concurrent FAQ tasks per app, no queue; size only after measured demand.
    private final ThreadPoolExecutor executor;

    public FaqRequestRunner(FaqService service, @Value("${faq.request-timeout-ms:35000}") long timeoutMillis) {
        new FaqRequestBudget(timeoutMillis); // Fail startup before allocating resources on invalid configuration.
        this.service = service;
        this.timeoutMillis = timeoutMillis;
        this.executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), task -> {
                    Thread thread = new Thread(task, "faq-request");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public FaqAnswerResponse ask(String question) {
        return ask(question, new FaqRequestBudget(timeoutMillis));
    }

    public FaqAnswerResponse ask(String question, FaqRequestBudget budget) {
        return execute(() -> service.ask(question, budget), budget, Function.identity());
    }

    public FaqService.ChatAnswer chat(String question, List<ConversationTurn> history, FaqRequestBudget budget) {
        return execute(() -> service.chat(question, history, budget), budget,
                fallback -> new FaqService.ChatAnswer(fallback,
                        "extractive".equals(fallback.getMode()) ? budget.getConversationQuestion() : null,
                        "extractive".equals(fallback.getMode()) && budget.isConversationNewTopic()));
    }

    private <T> T execute(Callable<T> operation, FaqRequestBudget budget, Function<FaqAnswerResponse, T> fallback) {
        Future<T> task;
        try {
            task = executor.submit(operation);
            budget.registerTask(task);
        } catch (RejectedExecutionException error) {
            throw unavailable();
        }
        try {
            return task.get(budget.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            return fallback.apply(fallbackOrUnavailable(budget));
        } catch (CancellationException error) {
            return fallback.apply(fallbackOrUnavailable(budget));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return fallback.apply(fallbackOrUnavailable(budget));
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof FaqRequestBudget.ExpiredException) return fallback.apply(fallbackOrUnavailable(budget));
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw unavailable();
        } finally {
            budget.cancel();
            if (!task.isDone()) task.cancel(true);
        }
    }

    private FaqAnswerResponse fallbackOrUnavailable(FaqRequestBudget budget) {
        FaqAnswerResponse response = budget.getFallback();
        if (response == null) throw unavailable();
        return response;
    }

    private DataAccessResourceFailureException unavailable() {
        return new DataAccessResourceFailureException("FAQ processing is temporarily unavailable.");
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
