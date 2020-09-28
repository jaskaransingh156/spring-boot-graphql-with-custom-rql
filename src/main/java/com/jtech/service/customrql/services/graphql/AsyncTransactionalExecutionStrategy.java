package com.jtech.service.customrql.services.graphql;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import graphql.ExecutionResult;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;

@Service
public class AsyncTransactionalExecutionStrategy extends AsyncExecutionStrategy {

    @Override
    @Transactional
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {
        return super.execute(executionContext, executionStrategyParameters);
    }
}