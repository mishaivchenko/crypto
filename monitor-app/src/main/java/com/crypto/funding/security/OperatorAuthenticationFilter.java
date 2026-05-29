package com.crypto.funding.security;

import com.crypto.funding.application.security.OperatorAccountService;
import com.crypto.funding.config.OperatorSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OperatorAuthenticationFilter extends OncePerRequestFilter
{
    private final OperatorSecurityProperties properties;
    private final OperatorAccountService operatorAccountService;

    public OperatorAuthenticationFilter(
        OperatorSecurityProperties properties,
        OperatorAccountService operatorAccountService
    )
    {
        this.properties = properties;
        this.operatorAccountService = operatorAccountService;
    }

    @Override
    protected boolean shouldNotFilter( HttpServletRequest request )
    {
        String path = request.getRequestURI();
        return !properties.isAuthEnabled()
               || !path.startsWith( "/api/" );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException
    {
        try
        {
            String token = request.getHeader( "X-Operator-Token" );
            var principal = operatorAccountService.authenticate( token );
            if( principal.isEmpty() )
            {
                reject( response, HttpServletResponse.SC_UNAUTHORIZED, "Valid X-Operator-Token is required." );
                return;
            }
            OperatorContext.set( principal.get() );
            filterChain.doFilter( request, response );
        }
        finally
        {
            OperatorContext.clear();
        }
    }

    private void reject( HttpServletResponse response, int status, String message ) throws IOException
    {
        response.setStatus( status );
        response.setContentType( MediaType.APPLICATION_JSON_VALUE );
        response.getWriter().write( "{\"message\":\"" + message + "\"}" );
    }
}
