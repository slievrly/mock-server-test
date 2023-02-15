package com.lingh;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Mocked data source.
 */
@SuppressWarnings("unused")
@NoArgsConstructor
@Getter
@Setter
public final class MockedDataSource implements DataSource, AutoCloseable {
    
    private String url = "jdbc:mock://127.0.0.1/foo_ds";
    
    private String driverClassName;
    
    private String username = "root";
    
    private String password = "root";
    
    private Integer maxPoolSize;
    
    private Integer minPoolSize;
    
    private List<String> connectionInitSqls;
    
    private Properties jdbcUrlProperties;
    
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Connection connection;
    
    @Setter(AccessLevel.NONE)
    private boolean closed;
    
    public MockedDataSource(final Connection connection) {
        this.connection = connection;
    }
    
    @SuppressWarnings("MagicConstant")
    @Override
    public Connection getConnection() throws SQLException {
        if (null != connection) {
            return connection;
        }
        Connection result = mock(Connection.class, RETURNS_DEEP_STUBS);
        when(result.getMetaData().getURL()).thenReturn(url);
        when(result.createStatement(anyInt(), anyInt(), anyInt()).getConnection()).thenReturn(result);
        return result;
    }
    
    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return getConnection();
    }
    
    @SuppressWarnings("ReturnOfNull")
    @Override
    public <T> T unwrap(final Class<T> iface) {
        return null;
    }
    
    @Override
    public boolean isWrapperFor(final Class<?> iface) {
        return false;
    }
    
    @SuppressWarnings("ReturnOfNull")
    @Override
    public PrintWriter getLogWriter() {
        return null;
    }
    
    @Override
    public void setLogWriter(final PrintWriter out) {
    }
    
    @Override
    public void setLoginTimeout(final int seconds) {
    }
    
    @Override
    public int getLoginTimeout() {
        return 0;
    }
    
    @SuppressWarnings("ReturnOfNull")
    @Override
    public Logger getParentLogger() {
        return null;
    }
    
    @Override
    public void close() {
        closed = true;
    }
}
