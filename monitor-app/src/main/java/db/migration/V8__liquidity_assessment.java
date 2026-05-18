package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V8__liquidity_assessment extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( tableExists( connection ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            statement.execute( """
                CREATE TABLE liquidity_assessment (
                    id INTEGER PRIMARY KEY,
                    assessment_id VARCHAR(64) NOT NULL UNIQUE,
                    trade_id BIGINT,
                    venue VARCHAR(255) NOT NULL,
                    symbol VARCHAR(255) NOT NULL,
                    side VARCHAR(255) NOT NULL CHECK (side IN ('LONG', 'SHORT')),
                    best_bid NUMERIC(19,8),
                    best_ask NUMERIC(19,8),
                    spread_bps NUMERIC(19,4),
                    max_slippage_bps NUMERIC(19,4),
                    entry_bid_depth_notional NUMERIC(19,8),
                    exit_ask_depth_notional NUMERIC(19,8),
                    round_trip_safe_notional NUMERIC(19,8),
                    safety_haircut NUMERIC(19,8),
                    recommended_max_order_notional NUMERIC(19,8),
                    score VARCHAR(255) NOT NULL CHECK (score IN ('UNTRADABLE', 'THIN', 'MEDIUM', 'GOOD', 'EXCELLENT')),
                    sampled_at BIGINT NOT NULL,
                    expires_at BIGINT,
                    version BIGINT,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """ );
            statement.execute( "CREATE INDEX idx_liquidity_assessment_trade_id ON liquidity_assessment (trade_id)" );
            statement.execute( "CREATE INDEX idx_liquidity_assessment_venue_symbol ON liquidity_assessment (venue, symbol)" );
            statement.execute( "CREATE INDEX idx_liquidity_assessment_sampled_at ON liquidity_assessment (sampled_at)" );
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
}
