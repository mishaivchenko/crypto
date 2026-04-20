package com.crypto.funding.security;

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
public class InternalTokenFilter extends OncePerRequestFilter
{
    private final OperatorSecurityProperties properties;

    public InternalTokenFilter( OperatorSecurityProperties properties )
    {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter( HttpServletRequest request )
    {
        return !request.getRequestURI().startsWith( "/internal/v1/engine/" );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException
    {
        String expected = properties.getInternalToken();
        String actual = request.getHeader( "X-Internal-Token" );
        if( expected == null || expected.isBlank() || actual == null || !expected.equals( actual ) )
        {
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentType( MediaType.APPLICATION_JSON_VALUE );
            response.getWriter().write( "{\"message\":\"Valid X-Internal-Token is required.\"}" );
            return;
        }
        filterChain.doFilter( request, response );
    }
}
