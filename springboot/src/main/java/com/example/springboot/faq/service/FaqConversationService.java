package com.example.springboot.faq.service;

import com.example.springboot.faq.dto.FaqAnswerResponse;
import com.example.springboot.faq.dto.FaqChatResponse;
import com.example.springboot.faq.llm.ConversationTurn;
import com.example.springboot.faq.support.FaqRequestBudget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.servlet.http.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.UUID;

/** Session lifecycle and bounded multi-turn context; final generation always uses freshly retrieved evidence. */
@Service
public class FaqConversationService {
    private static final String ATTRIBUTE = "faq.conversation";
    private final FaqRequestRunner runner;
    private final long timeoutMillis;

    public FaqConversationService(FaqRequestRunner runner, @Value("${faq.request-timeout-ms:35000}") long timeoutMillis) {
        new FaqRequestBudget(timeoutMillis);
        this.runner = runner;
        this.timeoutMillis = timeoutMillis;
    }

    public FaqChatResponse chat(String question, String id, HttpSession session) {
        try { return chatInSession(question, id, session); }
        catch (IllegalStateException error) {
            authorize(session); // An invalidation can race any attribute operation after the first check.
            throw error;
        }
    }

    private FaqChatResponse chatInSession(String question, String id, HttpSession session) {
        Conversation conversation;
        List<ConversationTurn> history;
        FaqRequestBudget budget = new FaqRequestBudget(timeoutMillis);
        synchronized (session) {
            authorize(session);
            conversation = current(session);
            if (id == null) {
                // Attribute replacement invokes valueUnbound, cancelling any obsolete request.
                conversation = new Conversation(session.getAttribute("User"), session.getAttribute("Identity"));
                session.setAttribute(ATTRIBUTE, conversation);
            } else if (conversation == null || !conversation.id.equals(id)) {
                throw notFound();
            }
            if (conversation.pending != null) throw new ConversationException(409,
                    "FAQ_CONVERSATION_BUSY", "This conversation is already processing a question.");
            conversation.pending = budget;
            history = new ArrayList<>(conversation.history);
            // Keep the bounded exchanges, but only the current topic may participate in rewriting.
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).isStartsTopic()) { history = new ArrayList<>(history.subList(i, history.size())); break; }
            }
        }
        try {
            FaqService.ChatAnswer result = runner.chat(question, history, budget);
            FaqAnswerResponse answer = result.getAnswer();
            synchronized (session) {
                authorize(session);
                if (current(session) != conversation) throw notFound();
                if (result.getQuestion() != null) conversation.history.addLast(
                        new ConversationTurn(result.getQuestion(), answer, result.isNewTopic()));
                while (conversation.history.size() > 3) conversation.history.removeFirst();
                return new FaqChatResponse(conversation.id, answer);
            }
        } catch (RuntimeException error) {
            // Invalidation/replacement outranks an old worker's success or database failure.
            synchronized (session) {
                authorize(session);
                if (current(session) != conversation) throw notFound();
            }
            throw error;
        } finally {
            synchronized (session) {
                conversation.pending = null;
                budget.cancel();
            }
        }
    }

    public void clear(HttpSession session) {
        try {
            synchronized (session) { authorize(session); session.removeAttribute(ATTRIBUTE); }
        } catch (IllegalStateException error) {
            authorize(session);
            throw error;
        }
    }

    public void authorize(HttpSession session) {
        try {
            Object role = session.getAttribute("Identity");
            if (session.getAttribute("User") == null || role == null) {
                throw unauthenticated();
            }
            if (!"stu".equals(role) && !"dormManager".equals(role) && !"admin".equals(role)) {
                throw new ConversationException(403, "FAQ_FORBIDDEN", "You do not have permission to use FAQ assistance.");
            }
        } catch (IllegalStateException expired) { throw unauthenticated(); }
    }

    private Conversation current(HttpSession session) {
        Conversation value = (Conversation) session.getAttribute(ATTRIBUTE);
        if (value != null && (value.owner != session.getAttribute("User")
                || !value.role.equals(session.getAttribute("Identity")))) {
            session.removeAttribute(ATTRIBUTE);
            return null;
        }
        return value;
    }

    /** Existing signOut removes User/Identity without invalidating Session; clear FAQ state on either change. */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionAttributeListener> sessionCleanupListener() {
        return new ServletListenerRegistrationBean<>(new HttpSessionAttributeListener() {
            @Override public void attributeRemoved(HttpSessionBindingEvent event) { clean(event); }
            @Override public void attributeReplaced(HttpSessionBindingEvent event) { clean(event); }
            private void clean(HttpSessionBindingEvent event) {
                if ("User".equals(event.getName()) || "Identity".equals(event.getName())) {
                    synchronized (event.getSession()) {
                        try { event.getSession().removeAttribute(ATTRIBUTE); }
                        catch (IllegalStateException expired) { /* Invalidation already unbinds state. */ }
                    }
                }
            }
        });
    }

    private static ConversationException unauthenticated() {
        return new ConversationException(401, "FAQ_UNAUTHENTICATED", "Please sign in to use FAQ assistance.");
    }
    private static ConversationException notFound() {
        return new ConversationException(404, "FAQ_CONVERSATION_NOT_FOUND", "The FAQ conversation was not found.");
    }

    public static final class ConversationException extends RuntimeException {
        private final int status;
        private final String code;
        private ConversationException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
        public int getStatus() { return status; }
        public String getCode() { return code; }
    }

    // Deliberately not Serializable: conversations must not be restored after a server restart.
    private static final class Conversation implements HttpSessionBindingListener {
        private final String id = UUID.randomUUID().toString();
        private final Object owner;
        private final Object role;
        private final Deque<ConversationTurn> history = new ArrayDeque<>();
        private volatile FaqRequestBudget pending;
        private Conversation(Object owner, Object role) { this.owner = owner; this.role = role; }
        @Override public void valueUnbound(HttpSessionBindingEvent event) {
            FaqRequestBudget request = pending;
            if (request != null) request.cancel();
            synchronized (event.getSession()) { history.clear(); }
        }
    }

}
