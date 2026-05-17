package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V6__armed_trade_sltp extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( !tableExists( connection ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            if( !columnExists( connection, "stop_loss_usd" ) )
            {
                statement.execute( "ALTER TABLE armed_trade ADD COLUMN stop_loss_usd NUMERIC(19,8)" );
            }
            if( !columnExists( connection, "take_profit_usd" ) )
            {
                statement.execute( "ALTER TABLE armed_trade ADD COLUMN take_profit_usd NUMERIC(19,8)" );
            }
        }
    }

    private static boolean tableExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT 1 FROM sqlite_master WHERE type='table' AND name='armed_trade'"
             ) )
        {
            return resultSet.next();
        }
    }

    private static boolean columnExists( Connection connection, String columnName ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(armed_trade)" ) )
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
