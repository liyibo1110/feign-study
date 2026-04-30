package com.github.liyibo1110.feign;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * AsyncClient接口的默认实现。
 * @author liyibo
 * @date 2026-04-29 17:11
 */
@Experimental
public class DefaultAsyncClient<C> implements AsyncClient<C> {
    private final Client client;
    private final ExecutorService executorService;

    public DefaultAsyncClient(Client client, ExecutorService executorService) {
        this.client = client;
        this.executorService = executorService;
    }

    @Override
    public CompletableFuture<Response> execute(Request request, Request.Options options, Optional<C> requestContext) {
        final CompletableFuture<Response> result = new CompletableFuture<>();
        final Future<?> future =
                executorService.submit(
                        () -> {
                            try {
                                result.complete(client.execute(request, options));
                            } catch (final Exception e) {
                                result.completeExceptionally(e);
                            }
                        });
        result.whenComplete(
                (response, throwable) -> {
                    if (result.isCancelled()) {
                        future.cancel(true);
                    }
                });
        return result;
    }
}
