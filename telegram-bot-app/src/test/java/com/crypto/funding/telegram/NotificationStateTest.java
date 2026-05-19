package com.crypto.funding.telegram;

import com.crypto.funding.telegram.notification.NotificationState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStateTest
{
    @Test
    void newIdIsInitiallyNew()
    {
        NotificationState state = new NotificationState();
        assertThat( state.isNew( "signal:42" ) ).isTrue();
    }

    @Test
    void afterMarkSeenIsNoLongerNew()
    {
        NotificationState state = new NotificationState();
        state.markSeen( "signal:42" );
        assertThat( state.isNew( "signal:42" ) ).isFalse();
    }

    @Test
    void duplicateMarkSeenDoesNotGrow()
    {
        NotificationState state = new NotificationState();
        state.markSeen( "signal:1" );
        state.markSeen( "signal:1" );
        assertThat( state.seenCount() ).isEqualTo( 1 );
    }

    @Test
    void evictsOldestWhenOverCapacity()
    {
        NotificationState state = new NotificationState();
        for( long i = 1; i <= 1001; i++ )
        {
            state.markSeen( "signal:" + i );
        }
        assertThat( state.seenCount() ).isEqualTo( 1000 );
        assertThat( state.isNew( "signal:1" ) ).isTrue();
        assertThat( state.isNew( "signal:1001" ) ).isFalse();
    }
}
