package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V4__armed_trade_mode extends BaseJavaMigration
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
            statement.execute( "ALTER TABLE armed_trade ADD COLUMN mode VARCHAR(255) CHECK (mode IN ('TESTNET', 'PRODUCTION'))" );
        }
    }

    private static boolean columnExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(armed_trade)" ) )
        {
            while( resultSet.next() )
            {
                if( "mode".equalsIgnoreCase( resultSet.getString( "name" ) ) )
                {
                    return true;
                }
            }
            return false;
        }
    }
}
