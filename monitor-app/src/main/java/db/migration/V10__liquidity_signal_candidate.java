package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V10__liquidity_signal_candidate extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( !tableExists( connection ) )
        {
            return;
        }
        if( !columnExists( connection ) )
        {
            try( Statement statement = connection.createStatement() )
            {
                statement.execute( "ALTER TABLE liquidity_assessment ADD COLUMN signal_candidate_id BIGINT" );
            }
        }
        if( !indexExists( connection ) )
        {
            try( Statement statement = connection.createStatement() )
            {
                statement.execute( "CREATE INDEX idx_liquidity_assessment_signal_candidate_id ON liquidity_assessment (signal_candidate_id)" );
            }
        }
    }

    private static boolean tableExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery( "SELECT name FROM sqlite_master WHERE type='table' AND name='liquidity_assessment'" ) )
        {
            return rs.next();
        }
    }

    private static boolean columnExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery( "PRAGMA table_info(liquidity_assessment)" ) )
        {
            while( rs.next() )
            {
                if( "signal_candidate_id".equals( rs.getString( "name" ) ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery( "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_liquidity_assessment_signal_candidate_id'" ) )
        {
            return rs.next();
        }
    }
}
