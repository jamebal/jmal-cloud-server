// package com.jmal.clouddisk.config.mongodb;
//
// import javax.sql.DataSource;
// import java.io.PrintWriter;
// import java.sql.Connection;
// import java.sql.SQLException;
// import java.sql.SQLFeatureNotSupportedException;
// import java.util.logging.Logger;
//
// public final class NoOpDataSource implements DataSource {
//
//     @Override
//     public Connection getConnection() {
//         throw new UnsupportedOperationException("NoOpDataSource cannot provide a connection.");
//     }
//
//     @Override
//     public Connection getConnection(String username, String password) {
//         throw new UnsupportedOperationException("NoOpDataSource cannot provide a connection.");
//     }
//
//     @Override
//     public PrintWriter getLogWriter() {
//         return null;
//     }
//
//     @Override
//     public void setLogWriter(PrintWriter out) {
//     }
//
//     @Override
//     public void setLoginTimeout(int seconds) {
//     }
//
//     @Override
//     public int getLoginTimeout() {
//         return 0;
//     }
//
//     @Override
//     public Logger getParentLogger() throws SQLFeatureNotSupportedException {
//         throw new SQLFeatureNotSupportedException();
//     }
//
//     @Override
//     public <T> T unwrap(Class<T> iface) throws SQLException {
//         throw new SQLException("NoOpDataSource is a wrapper and cannot be unwrapped.");
//     }
//
//     @Override
//     public boolean isWrapperFor(Class<?> iface) {
//         return false;
//     }
// }
