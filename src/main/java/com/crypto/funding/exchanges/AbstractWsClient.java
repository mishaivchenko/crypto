package com.crypto.funding.exchanges;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractWsClient
{
    protected final Logger log = LoggerFactory.getLogger( getClass() );
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final HttpClient http = HttpClient.newBuilder()
                                                .connectTimeout( Duration.ofSeconds( 8 ) )
                                                .build();

    private volatile WebSocket ws;
    private volatile boolean open = false;

    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor();

    public abstract String name();

    protected abstract URI endpoint();

    protected abstract String buildSubscribeMessage( List<String> unifiedSymbols );

    protected abstract void handleTextMessage( String text ) throws Exception ;

    /**
     * Коннект + слушатель (вирт.поток для обработки)
     */
    public void start( List<String> unifiedSymbols )
    {
        if( !running.compareAndSet( false, true ) )
        {
            return;
        }

        vthreads.submit( () -> {
            while( running.get() )
            {
                try {
                    sleep( 60_000 );
                    if ( open && ws != null )
                    {
                        ws.sendPing(ByteBuffer.wrap("ping".getBytes()));
                    }
                } catch( Exception ignored )
                {
                    log.error( "Error while sending ping", ignored );
                }
                try
                {
                    log.debug("[{}] connecting {}", name(), endpoint());
                    ws = http.newWebSocketBuilder()
                             .connectTimeout( Duration.ofSeconds( 10 ) )
                             .buildAsync( endpoint(), new Listener()
                             {
                                 private final StringBuilder partialBuffer = new StringBuilder();

                                 @Override
                                 public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                                     try {
                                         synchronized (partialBuffer) {
                                             partialBuffer.append(data);
                                             if ( last ) {
                                                 String json = partialBuffer.toString();
                                                 partialBuffer.setLength(0);
                                                 vthreads.submit(() -> safeHandle(json));
                                             }
                                         }
                                     } catch (Exception e) {
                                         log.error("[{}] onText error", name(), e);
                                     }
                                     ws.request(1);
                                     return CompletableFuture.completedFuture(null);
                                 }

                                 @Override
                                 public void onOpen( WebSocket webSocket )
                                 {
                                     open = true;
                                     log.info("[{}] open", name());
                                     webSocket.request( 1 );
                                     trySubscribe( unifiedSymbols );
                                 }

                                 @Override
                                 public CompletionStage<?> onBinary( WebSocket webSocket, ByteBuffer data, boolean last )
                                 {
                                     webSocket.request( 1 );
                                     return CompletableFuture.completedFuture( null );
                                 }

                                 @Override
                                 public CompletionStage<?> onClose( WebSocket webSocket, int statusCode, String reason )
                                 {
                                     open = false;
                                     log.warn( "[{}] closed {} {}", name(), statusCode, reason );
                                     return CompletableFuture.completedFuture( null );
                                 }

                                 @Override
                                 public void onError( WebSocket webSocket, Throwable error )
                                 {
                                     open = false;
                                     log.error( "[{}] error", name(), error );
                                 }
                             } ).join();

                    // держим соединение активным
                    open = true;
                    while( running.get() && open )
                    {
                        Thread.sleep( 1_000 );
                    }
                }
                catch( Throwable t )
                {
                    log.error( "[{}] connect loop error", name(), t );
                } finally
                {
                    ws = null;
                }
                // backoff и retry
                sleep( 2_000 );
            }
        } );
    }

    public void trySubscribe( List<String> unifiedSymbols )
    {
        if( ws == null || !open)
        {
            return;

            //start(  unifiedSymbols );
        }
        String sub = buildSubscribeMessage( unifiedSymbols );
        if( sub == null || sub.isBlank() )
        {
            return;
        }
        ws.sendText( sub, true );
        log.debug("[{}] subscribed {}", name(), unifiedSymbols);
    }

    private void safeHandle(String s) {
        try {
            if (s == null || s.isEmpty()) return;

            // срежем мусор до первого JSON-токена
            int i = 0;
            while (i < s.length() && s.charAt(i) != '{' && s.charAt(i) != '[') i++;
            if (i > 0) {
                s = s.substring(i);
                if (s.isEmpty()) return;
            }

            handleTextMessage(s);
        } catch (Throwable t) {
            // не спамим прод-логи: короткий префикс, DEBUG-стек
            String preview = s.length() > 200 ? s.substring(0, 200) + "..." : s;
            log.error("[{}] parse err (preview): {}", name(), preview, t);
            System.out.println(t);
        }
    }

    private static void sleep( long ms )
    {
        try
        {
            Thread.sleep( ms );
        }
        catch( InterruptedException ignored )
        {
        }
    }
}
