package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V11__ai_signal_advice extends BaseJavaMigration
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
                CREATE TABLE ai_signal_advice (
                    id INTEGER PRIMARY KEY,
                    signal_candidate_id BIGINT NOT NULL,
                    recommendation VARCHAR(10) NOT NULL CHECK (recommendation IN ('GO', 'WATCH', 'PASS')),
                    confidence REAL NOT NULL,
                    reasoning TEXT,
                    model_used VARCHAR(255),
                    prompt_tokens INTEGER,
                    completion_tokens INTEGER,
                    analyzed_at BIGINT NOT NULL,
                    version BIGINT,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    FOREIGN KEY (signal_candidate_id) REFERENCES signal_candidate(id)
                )
                """ );
            statement.execute( "CREATE INDEX idx_ai_advice_signal_candidate_id ON ai_signal_advice (signal_candidate_id)" );
        }
    }

    private static boolean tableExists( Connection connection ) throws Exception
    {
        try( Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery( "SELECT name FROM sqlite_master WHERE type='table' AND name='ai_signal_advice'" ) )
        {
            return rs.next();
        }
    }
}
