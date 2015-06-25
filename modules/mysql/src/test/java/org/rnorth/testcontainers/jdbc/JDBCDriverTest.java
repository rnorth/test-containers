package org.rnorth.testcontainers.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class JDBCDriverTest {

    @Test
    public void testMySQLWithVersion() throws SQLException {
        performSimpleTest("jdbc:tc:mysql:5.5.43://hostname/databasename");
    }

    @Test
    public void testMySQLWithNoSpecifiedVersion() throws SQLException {
        performSimpleTest("jdbc:tc:mysql://hostname/databasename");
    }

    @Test
    public void testMySQLWithClasspathInitScript() throws SQLException {
        performSimpleTest("jdbc:tc:mysql://hostname/databasename?TC_INITSCRIPT=somepath/init_mysql.sql");

        performTestForScriptedSchema("jdbc:tc:mysql://hostname/databasename?TC_INITSCRIPT=somepath/init_mysql.sql");
    }

    @Test
    public void testMySQLWithClasspathInitFunction() throws SQLException {
        performSimpleTest("jdbc:tc:mysql://hostname/databasename?TC_INITFUNCTION=org.rnorth.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction");

        performTestForScriptedSchema("jdbc:tc:mysql://hostname/databasename?TC_INITFUNCTION=org.rnorth.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction");
    }

    @Test
    public void testMySQLWithConnectionPoolUsingSameContainer() throws SQLException {
        HikariDataSource dataSource = getDataSource("jdbc:tc:mysql://hostname/databasename?TC_INITFUNCTION=org.rnorth.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction", 10);
        for (int i = 0; i < 100; i++) {
            new QueryRunner(dataSource).insert("INSERT INTO my_counter (n) VALUES (5)", new ResultSetHandler<Object>() {
                @Override
                public Object handle(ResultSet rs) throws SQLException {
                    return true;
                }
            });
        }

        new QueryRunner(dataSource).query("SELECT COUNT(1) FROM my_counter", new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                rs.next();
                int resultSetInt = rs.getInt(1);
                assertEquals(100, resultSetInt);
                return true;
            }
        });

        new QueryRunner(dataSource).query("SELECT SUM(n) FROM my_counter", new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                rs.next();
                int resultSetInt = rs.getInt(1);
                assertEquals(500, resultSetInt);
                return true;
            }
        });
    }

    @Test
    public void testMySQLWithQueryParams() throws SQLException {
        performSimpleTestWithCharacterSet("jdbc:tc:mysql://hostname/databasename?useUnicode=yes&characterEncoding=utf8");
    }

    private void performSimpleTest(String jdbcUrl) throws SQLException {
        HikariDataSource dataSource = getDataSource(jdbcUrl, 1);
        new QueryRunner(dataSource).query("SELECT 1", new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                rs.next();
                int resultSetInt = rs.getInt(1);
                assertEquals(1, resultSetInt);
                return true;
            }
        });
        dataSource.close();
    }

    private void performTestForScriptedSchema(String jdbcUrl) throws SQLException {
        HikariDataSource dataSource = getDataSource(jdbcUrl, 1);
        new QueryRunner(dataSource).query("SELECT foo FROM bar WHERE foo LIKE '%world'", new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                rs.next();
                String resultSetString = rs.getString(1);
                assertEquals("hello world", resultSetString);
                return true;
            }
        });
        dataSource.close();
    }

    private void performSimpleTestWithCharacterSet(String jdbcUrl) throws SQLException {
        HikariDataSource dataSource = getDataSource(jdbcUrl, 1);
        new QueryRunner(dataSource).query("SHOW VARIABLES LIKE 'character\\_set\\_connection'", new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                rs.next();
                String resultSetInt = rs.getString(2);
                assertEquals("utf8", resultSetInt);
                return true;
            }
        });
        dataSource.close();
    }

    private HikariDataSource getDataSource(String jdbcUrl, int poolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setMaximumPoolSize(poolSize);

        return new HikariDataSource(hikariConfig);
    }

    public static void sampleInitFunction(Connection connection) throws SQLException {
        connection.createStatement().execute("CREATE TABLE bar (\n" +
                "  foo VARCHAR(255)\n" +
                ");");
        connection.createStatement().execute("INSERT INTO bar (foo) VALUES ('hello world');");
        connection.createStatement().execute("CREATE TABLE my_counter (\n" +
                "  n INT\n" +
                ");");
    }
}
