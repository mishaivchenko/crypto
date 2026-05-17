package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V7__venue_default_latency extends BaseJavaMigration
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
            statement.execute( "ALTER TABLE venue_profile ADD COLUMN default_manual_latency_adjustment_ms BIGINT" );
        }
    }

    private static boolean columnExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(venue_profile)" ) )
        {
            boolean tableExists = false;
            while( resultSet.next() )
            {
                tableExists = true;
                if( "default_manual_latency_adjustment_ms".equalsIgnoreCase( resultSet.getString( "name" ) ) )
                {
                    return true;
                }
            }
            return !tableExists;
        }
    }
}
