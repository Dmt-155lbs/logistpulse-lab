package com.logistpulse.lab.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Gestor de transacciones falso: no toca ninguna base de datos pero recuerda si hay una transacción abierta. */
public class FakeTxManager implements PlatformTransactionManager {

    public int begun;
    public int committed;
    public int rolledBack;
    private int open;

    public boolean inTransaction() {
        return open > 0;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        begun++;
        open++;
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        committed++;
        open--;
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        rolledBack++;
        open--;
    }
}
