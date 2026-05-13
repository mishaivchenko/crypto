package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V3__trade_journal_add_cancel_event_code extends BaseJavaMigration
{
    @Override
    public void migrate( Context context ) throws Exception
    {
        Connection connection = context.getConnection();
        if( !tableExists( connection, "trade_journal" ) )
        {
            return;
        }
        try( Statement statement = connection.createStatement() )
        {
            // SQLite does not support ALTER TABLE ... MODIFY CONSTRAINT.
            // Recreate trade_journal with the expanded event_code CHECK constraint.
            statement.execute( """
                CREATE TABLE trade_journal_new (
                    created_at BIGINT NOT NULL,
                    entity_id  BIGINT NOT NULL,
                    id         INTEGER,
                    updated_at BIGINT NOT NULL,
                    version    BIGINT,
                    note       VARCHAR(2000),
                    actor_ref  VARCHAR(255),
                    actor_type VARCHAR(255) NOT NULL CHECK (actor_type IN ('SYSTEM', 'OPERATOR')),
                    entity_type VARCHAR(255) NOT NULL CHECK (
                        entity_type IN ('SIGNAL_CANDIDATE', 'FUNDING_EVENT', 'ARMED_TRADE')
                    ),
                    event_code VARCHAR(255) NOT NULL CHECK (
                        event_code IN (
                            'CANDIDATE_APPROVED',
                            'CANDIDATE_REJECTED',
                            'CANDIDATE_DELETED',
                            'FUNDING_EVENT_CREATED',
                            'FUNDING_EVENT_ARMED',
                            'ARMED_TRADE_CREATED',
                            'ARMED_TRADE_CANCELLED'
                        )
                    ),
                    new_state  VARCHAR(255),
                    old_state  VARCHAR(255),
                    PRIMARY KEY (id)
                )
                """ );
            statement.execute( "INSERT INTO trade_journal_new SELECT * FROM trade_journal" );
            statement.execute( "DROP TABLE trade_journal" );
            statement.execute( "ALTER TABLE trade_journal_new RENAME TO trade_journal" );
            statement.execute( "CREATE INDEX idx_trade_journal_entity    ON trade_journal (entity_type, entity_id)" );
            statement.execute( "CREATE INDEX idx_trade_journal_event_code ON trade_journal (event_code)" );
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
}
