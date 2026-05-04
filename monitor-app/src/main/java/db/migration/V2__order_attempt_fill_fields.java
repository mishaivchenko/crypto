package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V2__order_attempt_fill_fields extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( !tableExists( connection, "order_attempt" ) )
        {
            return;
        }
        addColumnIfMissing( connection, "average_fill_price", "NUMERIC(19,8)" );
        addColumnIfMissing( connection, "filled_quantity", "NUMERIC(19,8)" );
        addColumnIfMissing( connection, "fee_usd", "NUMERIC(19,8)" );
    }

    private static void addColumnIfMissing( Connection connection, String columnName, String definition ) throws Exception
    {
        if( columnExists( connection, columnName ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            statement.execute( "ALTER TABLE order_attempt ADD COLUMN " + columnName + " " + definition );
        }
    }

    private static boolean tableExists( Connection connection, String tableName ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "select 1 from sqlite_master where type = 'table' and name = '" + tableName + "'"
             ) )
        {
            return resultSet.next();
        }
    }

    private static boolean columnExists( Connection connection, String columnName ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery( "PRAGMA table_info(order_attempt)" ) )
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
