package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V12__drop_operator_token_hash extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( !columnExists( connection, "token_hash" ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            statement.execute(
                """
                CREATE TABLE operator_account_new (
                    enabled BOOLEAN NOT NULL,
                    created_at BIGINT NOT NULL,
                    id INTEGER PRIMARY KEY,
                    updated_at BIGINT NOT NULL,
                    version BIGINT,
                    username VARCHAR(255) NOT NULL UNIQUE
                )
                """ );
            statement.execute(
                """
                INSERT INTO operator_account_new (enabled, created_at, id, updated_at, version, username)
                SELECT enabled, created_at, id, updated_at, version, username FROM operator_account
                """ );
            statement.execute( "DROP TABLE operator_account" );
            statement.execute( "ALTER TABLE operator_account_new RENAME TO operator_account" );
            statement.execute( "DROP INDEX IF EXISTS idx_operator_account_token_hash" );
        }
    }

    private static boolean columnExists( Connection connection, String columnName ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(operator_account)" ) )
        {
            while( resultSet.next() )
            {
                if( columnName.equalsIgnoreCase( resultSet.getString( "name" ) ) )
                {
                    return true;
                }
            }
            return false;
        }
    }
}
