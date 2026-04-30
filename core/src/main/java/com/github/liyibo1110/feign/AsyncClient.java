package com.github.liyibo1110.feign;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author liyibo
 * @date 2026-04-29 17:10
 */
@Experimental
public interface AsyncClient<C> {
    /**
     * Executes the request asynchronously. Calling {@link CompletableFuture#cancel(boolean)} on the
     * result may cause the execution to be cancelled / aborted, but this is not guaranteed.
     *
     * @param request safe to replay
     * @param options options to apply to this request
     * @param requestContext - the optional context, for example for storing session cookies. The
     *     client should update this appropriately based on the received response before completing
     *     the result.
     * @return a {@link CompletableFuture} to be completed with the response, or completed
     *     exceptionally otherwise, for example with an {@link java.io.IOException} on a network error
     *     connecting to {@link Request#url()}.
     */
    CompletableFuture<Response> execute(Request request, Request.Options options, Optional<C> requestContext);

    /**
     * @deprecated use {@link DefaultAsyncClient} instead.
     */
    @Deprecated
    class Default<C> extends DefaultAsyncClient<C> {

        public Default(Client client, ExecutorService executorService) {
            super(client, executorService);
        }
    }

    /**
     * A synchronous implementation of {@link AsyncClient}
     *
     * @param <C> - unused context; synchronous clients handle context internally
     */
    class Pseudo<C> implements AsyncClient<C> {

        private final Client client;

        public Pseudo(Client client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<Response> execute(Request request, Request.Options options, Optional<C> requestContext) {
            final CompletableFuture<Response> result = new CompletableFuture<>();
            try {
                result.complete(client.execute(request, options));
            } catch (final Exception e) {
                result.completeExceptionally(e);
            }

            return result;
        }
    }
}
