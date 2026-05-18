package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V9__armed_trade_warmup extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( columnExists( connection, "warmup_p50_ms" ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            statement.execute( "ALTER TABLE armed_trade ADD COLUMN warmup_p50_ms BIGINT" );
            statement.execute( "ALTER TABLE armed_trade ADD COLUMN warmup_p95_ms BIGINT" );
            statement.execute( "ALTER TABLE armed_trade ADD COLUMN warmup_fallback_used boolean" );
            statement.execute( "ALTER TABLE armed_trade ADD COLUMN warmup_done_at BIGINT" );
        }
    }

    private static boolean columnExists( Connection connection, String columnName ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(armed_trade)" ) )
        {
            boolean tableExists = false;
            while( resultSet.next() )
            {
                tableExists = true;
                if( columnName.equalsIgnoreCase( resultSet.getString( "name" ) ) )
                {
                    return true;
                }
            }
            return !tableExists;
        }
    }
}
