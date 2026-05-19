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

    private final Set<String> seenIds = new HashSet<>();
    private final Deque<String> insertionOrder = new ArrayDeque<>();

    public boolean isNew( String key )
    {
        return !seenIds.contains( key );
    }

    public void markSeen( String key )
    {
        if( seenIds.contains( key ) )
        {
            return;
        }
        seenIds.add( key );
        insertionOrder.addLast( key );
        if( seenIds.size() > MAX_SEEN )
        {
            String evicted = insertionOrder.removeFirst();
            seenIds.remove( evicted );
        }
    }

    public int seenCount()
    {
        return seenIds.size();
    }
}
