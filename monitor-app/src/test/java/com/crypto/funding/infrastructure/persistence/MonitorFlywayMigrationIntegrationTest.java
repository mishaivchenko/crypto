package com.crypto.funding.infrastructure.persistence;

import com.crypto.funding.MonitorApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorFlywayMigrationIntegrationTest
{
    @TempDir
    Path tempDir;

    @Test
    void createsFlywayHistoryWhenStartingWithAnEmptyDatabase()
    {
        Path dbPath = tempDir.resolve( "empty.sqlite" );

        try( ConfigurableApplicationContext ignored = runMonitor( dbPath ) )
        {
            assertThat( tableExists( dbPath, "flyway_schema_history" ) ).isTrue();
            assertThat( tableExists( dbPath, "signal_candidate" ) ).isTrue();
        }
    }

    @Test
    void baselinesExistingHibernateManagedDatabase()
    {
        Path dbPath = tempDir.resolve( "legacy.sqlite" );
        createLegacySchema( dbPath );
        insertLegacyCandidate( dbPath );

        try( ConfigurableApplicationContext ignored = runMonitor( dbPath ) )
        {
            assertThat( tableExists( dbPath, "flyway_schema_history" ) ).isTrue();
            assertThat( countRows( dbPath, "signal_candidate" ) ).isEqualTo( 1 );
            assertThat( historyTypes( dbPath ) ).containsExactly( "BASELINE", "JDBC", "JDBC", "JDBC", "JDBC" );
        }
    }

    private ConfigurableApplicationContext runMonitor( Path dbPath )
    {
        return new SpringApplicationBuilder( MonitorApplication.class )
            .profiles( "local-safe" )
            .run(
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:sqlite:" + dbPath.toAbsolutePath(),
                "--trading.candidate-source.enabled=false",
                "--trading.metadata.sync-on-startup=false",
                "--trading.metadata.schedule-enabled=false",
                "--security.operators.bootstrap-users="
            );
    }

    private void createLegacySchema( Path dbPath )
    {
        try( ConfigurableApplicationContext ignored = new SpringApplicationBuilder( MonitorApplication.class )
            .profiles( "local-safe" )
            .run(
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:sqlite:" + dbPath.toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=create",
                "--spring.flyway.enabled=false",
                "--trading.candidate-source.enabled=false",
                "--trading.metadata.sync-on-startup=false",
                "--trading.metadata.schedule-enabled=false",
                "--security.operators.bootstrap-users="
            ) )
        {
            assertThat( tableExists( dbPath, "signal_candidate" ) ).isTrue();
        }
    }

    private void insertLegacyCandidate( Path dbPath )
    {
        try( Connection connection = openConnection( dbPath );
             PreparedStatement statement = connection.prepareStatement(
                 """
                     insert into signal_candidate (
                         version,
                         created_at,
                         updated_at,
                         source_type,
                         source_chat_id,
                         source_message_id,
                         raw_payload,
                         raw_symbol,
                         detected_at,
                         status
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """
             ) )
        {
            statement.setLong( 1, 0L );
            statement.setLong( 2, 1L );
            statement.setLong( 3, 1L );
            statement.setString( 4, "FUNDING_API" );
            statement.setLong( 5, 1L );
            statement.setLong( 6, 1L );
            statement.setString( 7, "{\"coin\":\"BTC\"}" );
            statement.setString( 8, "BTC/USDT" );
            statement.setLong( 9, 1L );
            statement.setString( 10, "NEW" );
            statement.executeUpdate();
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to insert legacy candidate", ex );
        }
    }

    private boolean tableExists( Path dbPath, String tableName )
    {
        try( Connection connection = openConnection( dbPath );
             PreparedStatement statement = connection.prepareStatement(
                 "select 1 from sqlite_master where type = 'table' and name = ?"
             ) )
        {
            statement.setString( 1, tableName );
            try( ResultSet resultSet = statement.executeQuery() )
            {
                return resultSet.next();
            }
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to inspect table " + tableName, ex );
        }
    }

    private int countRows( Path dbPath, String tableName )
    {
        try( Connection connection = openConnection( dbPath );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "select count(*) from " + tableName ) )
        {
            resultSet.next();
            return resultSet.getInt( 1 );
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to count rows for " + tableName, ex );
        }
    }

    private java.util.List<String> historyTypes( Path dbPath )
    {
        try( Connection connection = openConnection( dbPath );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "select type from flyway_schema_history order by installed_rank"
             ) )
        {
            java.util.List<String> values = new java.util.ArrayList<>();
            while( resultSet.next() )
            {
                values.add( resultSet.getString( 1 ) );
            }
            return values;
        }
        catch( Exception ex )
        {
            throw new IllegalStateException( "Failed to inspect flyway history", ex );
        }
    }

    private Connection openConnection( Path dbPath ) throws Exception
    {
        return DriverManager.getConnection( "jdbc:sqlite:" + dbPath.toAbsolutePath() );
    }
}
