package com.dbscodetrust.qa.tests;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * TestWatcher roda DEPOIS do @AfterEach; o relatório precisa saber o resultado ANTES.
 * TestExecutionExceptionHandler é chamado logo que o corpo do teste lança -> marca falha.
 */
public class ResultWatcher implements BeforeEachCallback, TestExecutionExceptionHandler {

    private static final ThreadLocal<Boolean> FAILED = ThreadLocal.withInitial(() -> false);

    @Override
    public void beforeEach(ExtensionContext context) {
        FAILED.set(false);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        FAILED.set(true);
        throw throwable;
    }

    static boolean lastResultPassed() {
        return !FAILED.get();
    }
}
