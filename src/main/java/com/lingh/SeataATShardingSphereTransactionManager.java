package com.lingh;

import io.seata.config.FileConfiguration;
import io.seata.core.context.RootContext;
import io.seata.core.exception.TransactionException;
import io.seata.core.rpc.netty.RmNettyRemotingClient;
import io.seata.core.rpc.netty.TmNettyRemotingClient;
import io.seata.rm.RMClient;
import io.seata.rm.datasource.DataSourceProxy;
import io.seata.tm.TMClient;
import io.seata.tm.api.GlobalTransaction;
import io.seata.tm.api.GlobalTransactionContext;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Seata AT transaction manager.
 */
@SuppressWarnings("unused")
public final class SeataATShardingSphereTransactionManager {

    private final Map<String, DataSource> dataSourceMap = new HashMap<>();

    private final String applicationId;

    private final String transactionServiceGroup;

    private final boolean enableSeataAT;

    private final int globalTXTimeout;

    public SeataATShardingSphereTransactionManager() {
        FileConfiguration config = new FileConfiguration("seata.conf");
        enableSeataAT = config.getBoolean("sharding.transaction.seata.at.enable", true);
        applicationId = config.getConfig("client.application.id");
        transactionServiceGroup = config.getConfig("client.transaction.service.group", "default");
        globalTXTimeout = config.getInt("sharding.transaction.seata.tx.timeout", 60);
    }

    public void init(final Map<String, DataSource> dataSources) {
        if (enableSeataAT) {
            initSeataRPCClient();
            dataSources.forEach((key, value) -> dataSourceMap.put(key, new DataSourceProxy(value)));
        }
    }

    private void initSeataRPCClient() {
        if (null == applicationId) {
            throw new RuntimeException("Please config application id within seata.conf file");
        }
        TMClient.init(applicationId, transactionServiceGroup);
        RMClient.init(applicationId, transactionServiceGroup);
    }

    public TransactionType getTransactionType() {
        return TransactionType.BASE;
    }

    public boolean isInTransaction() {
        checkSeataATEnabled();
        return null != RootContext.getXID();
    }

    public Connection getConnection(final String databaseName, final String dataSourceName) throws SQLException {
        checkSeataATEnabled();
        return dataSourceMap.get(databaseName + "." + dataSourceName).getConnection();
    }

    public void begin() {
        begin(globalTXTimeout);
    }

    @SneakyThrows(TransactionException.class)
    public void begin(final int timeout) {
        if (!(timeout >= 0)) {
            throw new RuntimeException("Transaction timeout should more than 0s");
        }
        checkSeataATEnabled();
        GlobalTransaction globalTransaction = GlobalTransactionContext.getCurrentOrCreate();
        globalTransaction.begin(timeout * 1000);
        SeataTransactionHolder.set(globalTransaction);
    }

    @SneakyThrows(TransactionException.class)
    public void commit(final boolean rollbackOnly) {
        checkSeataATEnabled();
        try {
            SeataTransactionHolder.get().commit();
        } finally {
            SeataTransactionHolder.clear();
            RootContext.unbind();
            SeataXIDContext.remove();
        }
    }

    @SneakyThrows(TransactionException.class)
    public void rollback() {
        checkSeataATEnabled();
        try {
            SeataTransactionHolder.get().rollback();
        } finally {
            SeataTransactionHolder.clear();
            RootContext.unbind();
            SeataXIDContext.remove();
        }
    }

    private void checkSeataATEnabled() {
        if (!enableSeataAT) {
            throw new RuntimeException("ShardingSphere Seata-AT transaction has been disabled");
        }
    }

    public void close() {
        dataSourceMap.clear();
        SeataTransactionHolder.clear();
        RmNettyRemotingClient.getInstance().destroy();
        TmNettyRemotingClient.getInstance().destroy();
    }
}
