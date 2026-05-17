package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V5__order_attempt_request_duration extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( columnExists( connection ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            statement.execute( "ALTER TABLE order_attempt ADD COLUMN request_duration_ms BIGINT" );
        }
    }

    private static boolean columnExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(order_attempt)" ) )
        {
            while( resultSet.next() )
            {
                if( "request_duration_ms".equalsIgnoreCase( resultSet.getString( "name" ) ) )
                {
                    return true;
                }
            }
            return false;
        }
    }
}
