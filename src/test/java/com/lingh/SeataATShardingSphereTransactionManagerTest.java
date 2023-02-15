package com.lingh;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import com.lingh.fixture.MockSeataServer;
import io.seata.core.context.RootContext;
import io.seata.core.protocol.MergeResultMessage;
import io.seata.core.protocol.MergedWarpMessage;
import io.seata.core.protocol.RegisterRMRequest;
import io.seata.core.protocol.RegisterRMResponse;
import io.seata.core.protocol.RegisterTMRequest;
import io.seata.core.protocol.RegisterTMResponse;
import io.seata.core.protocol.transaction.GlobalBeginRequest;
import io.seata.core.protocol.transaction.GlobalBeginResponse;
import io.seata.core.protocol.transaction.GlobalCommitRequest;
import io.seata.core.protocol.transaction.GlobalCommitResponse;
import io.seata.core.protocol.transaction.GlobalRollbackRequest;
import io.seata.core.protocol.transaction.GlobalRollbackResponse;
import io.seata.core.rpc.netty.RmNettyRemotingClient;
import io.seata.core.rpc.netty.TmNettyRemotingClient;
import io.seata.rm.datasource.ConnectionProxy;
import io.seata.rm.datasource.DataSourceProxy;
import io.seata.tm.api.GlobalTransactionContext;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.configuration.plugins.Plugins;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("SameParameterValue")
public final class SeataATShardingSphereTransactionManagerTest {
    
    private static final MockSeataServer MOCK_SEATA_SERVER = new MockSeataServer();
    
    private static final String DATA_SOURCE_UNIQUE_NAME = "sharding_db.ds_0";
    
    private final SeataATShardingSphereTransactionManager seataTransactionManager = new SeataATShardingSphereTransactionManager();
    
    private final Queue<Object> requestQueue = MOCK_SEATA_SERVER.getMessageHandler().getRequestQueue();
    
    private final Queue<Object> responseQueue = MOCK_SEATA_SERVER.getMessageHandler().getResponseQueue();
    
    @BeforeClass
    public static void before() {
        Executors.newSingleThreadExecutor().submit(MOCK_SEATA_SERVER::start);
        while (true) {
            if (MOCK_SEATA_SERVER.getInitialized().get()) {
                return;
            }
        }
    }
    
    @AfterClass
    public static void after() {
        MOCK_SEATA_SERVER.shutdown();
    }
    
    @Before
    public void setUp() {
        seataTransactionManager.init(Collections.singletonMap(DATA_SOURCE_UNIQUE_NAME, new MockedDataSource()));
    }
    
    @After
    public void tearDown() {
        SeataXIDContext.remove();
        RootContext.unbind();
        SeataTransactionHolder.clear();
        seataTransactionManager.close();
        releaseRpcClient();
        requestQueue.clear();
        responseQueue.clear();
    }
    
    @Test
    public void assertInit() {
        Map<String, DataSource> actual = getDataSourceMap();
        assertThat(actual.size(), is(1));
        assertThat(actual.get(DATA_SOURCE_UNIQUE_NAME), instanceOf(DataSourceProxy.class));
        assertThat(seataTransactionManager.getTransactionType(), is(TransactionType.BASE));
    }
    
    @Test
    public void assertGetConnection() throws SQLException {
        Connection actual = seataTransactionManager.getConnection("sharding_db", "ds_0");
        assertThat(actual, instanceOf(ConnectionProxy.class));
    }
    
    @Test
    public void assertBegin() {
        seataTransactionManager.begin();
        assertTrue(seataTransactionManager.isInTransaction());
        assertResult(GlobalBeginRequest.class, GlobalBeginResponse.class);
    }
    
    @Test
    public void assertBeginTimeout() {
        seataTransactionManager.begin(30);
        assertTrue(seataTransactionManager.isInTransaction());
        assertResult(GlobalBeginRequest.class, GlobalBeginResponse.class);
    }
    
    @Test
    public void assertCommit() {
        SeataTransactionHolder.set(GlobalTransactionContext.getCurrentOrCreate());
        setXID("testXID");
        seataTransactionManager.commit(false);
        assertResult(GlobalCommitRequest.class, GlobalCommitResponse.class);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertCommitWithoutBegin() {
        SeataTransactionHolder.set(GlobalTransactionContext.getCurrentOrCreate());
        seataTransactionManager.commit(false);
    }
    
    @Test
    public void assertRollback() {
        SeataTransactionHolder.set(GlobalTransactionContext.getCurrentOrCreate());
        setXID("testXID");
        seataTransactionManager.rollback();
        assertResult(GlobalRollbackRequest.class, GlobalRollbackResponse.class);
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertRollbackWithoutBegin() {
        SeataTransactionHolder.set(GlobalTransactionContext.getCurrentOrCreate());
        seataTransactionManager.rollback();
    }

    private void assertResult(Class<?> requestClazz, Class<?> responseClazz) {
        int requestQueueSize = requestQueue.size();
        if (3 == requestQueueSize) {
            assertThat(requestQueue.poll(), instanceOf(RegisterTMRequest.class));
            assertThat(requestQueue.poll(), instanceOf(RegisterRMRequest.class));
            assertThat(requestQueue.poll(), instanceOf(requestClazz));
        }
        int responseQueueSize = responseQueue.size();
        if (3 == responseQueueSize) {
            assertThat(responseQueue.poll(), instanceOf(RegisterTMResponse.class));
            assertThat(responseQueue.poll(), instanceOf(RegisterRMResponse.class));
            assertThat(responseQueue.poll(), instanceOf(responseClazz));
        }
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    @SuppressWarnings("unchecked")
    private Map<String, DataSource> getDataSourceMap() {
        return (Map<String, DataSource>) Plugins.getMemberAccessor().get(seataTransactionManager.getClass().getDeclaredField("dataSourceMap"), seataTransactionManager);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setXID(final String xid) {
        Plugins.getMemberAccessor().set(SeataTransactionHolder.get().getClass().getDeclaredField("xid"), SeataTransactionHolder.get(), xid);
        RootContext.bind(xid);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void releaseRpcClient() {
        Plugins.getMemberAccessor().set(TmNettyRemotingClient.getInstance().getClass().getDeclaredField("initialized"), TmNettyRemotingClient.getInstance(), new AtomicBoolean(false));
        Plugins.getMemberAccessor().set(TmNettyRemotingClient.getInstance().getClass().getDeclaredField("instance"), TmNettyRemotingClient.getInstance(), null);
        Plugins.getMemberAccessor().set(RmNettyRemotingClient.getInstance().getClass().getDeclaredField("initialized"), RmNettyRemotingClient.getInstance(), new AtomicBoolean(false));
        Plugins.getMemberAccessor().set(RmNettyRemotingClient.getInstance().getClass().getDeclaredField("instance"), RmNettyRemotingClient.getInstance(), null);
    }
}
