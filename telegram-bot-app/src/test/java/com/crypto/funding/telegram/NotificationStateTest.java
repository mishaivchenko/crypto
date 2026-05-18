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
        assertThat( state.isNew( 42L ) ).isTrue();
    }

    @Test
    void afterMarkSeenIsNoLongerNew()
    {
        NotificationState state = new NotificationState();
        state.markSeen( 42L );
        assertThat( state.isNew( 42L ) ).isFalse();
    }

    @Test
    void duplicateMarkSeenDoesNotGrow()
    {
        NotificationState state = new NotificationState();
        state.markSeen( 1L );
        state.markSeen( 1L );
        assertThat( state.seenCount() ).isEqualTo( 1 );
    }

    @Test
    void evictsOldestWhenOverCapacity()
    {
        NotificationState state = new NotificationState();
        for( long i = 1; i <= 1001; i++ )
        {
            state.markSeen( i );
        }
        assertThat( state.seenCount() ).isEqualTo( 1000 );
        assertThat( state.isNew( 1L ) ).isTrue();
        assertThat( state.isNew( 1001L ) ).isFalse();
    }
}
