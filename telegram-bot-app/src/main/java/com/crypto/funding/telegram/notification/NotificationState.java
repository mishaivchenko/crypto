package com.crypto.funding.telegram.notification;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@Component
public class NotificationState
{
    private static final int MAX_SEEN = 1000;

    private final Set<Long> seenIds = new HashSet<>();
    private final Deque<Long> insertionOrder = new ArrayDeque<>();

    public boolean isNew( Long id )
    {
        return !seenIds.contains( id );
    }

    public void markSeen( Long id )
    {
        if( seenIds.contains( id ) )
        {
            return;
        }
        seenIds.add( id );
        insertionOrder.addLast( id );
        if( seenIds.size() > MAX_SEEN )
        {
            Long evicted = insertionOrder.removeFirst();
            seenIds.remove( evicted );
        }
    }

    public int seenCount()
    {
        return seenIds.size();
    }
}
