package com.sforce.cc.tools.data2sql;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Functions
{
    private Functions()
    {
        // functional class
    }

    /**
     * The method use ClassLoader().getResourceAsStream() to read a resource, that means NO leading slash
     * is needed to address the resources.
     *
     * @param name  first try open a ressource, if not found try open a file if usefs is true
     * @param usefs also check the filesystem if there exists a file with this name and use it
     * @return InputStream or null if neither resssoure or file found
     */
    public static InputStream getInputStreamFromName( String name, boolean usefs )
            throws IOException
    {
        if ( null == name || name.isEmpty() )
        {
            throw new IllegalArgumentException( "input stream name can't be empty" );
        }

        ClassLoader cl = Functions.class.getClassLoader();
        if ( null != cl )
        {
            InputStream stream = cl.getResourceAsStream( name );
            if ( null != stream )
            {
                return stream;
            }
        }

        if ( usefs )
        {
            try
            {
                File tmpFile = new File( name );
                return new FileInputStream( tmpFile );
            }
            catch ( FileNotFoundException ex )
            {
                throw new IOException( String.format( "can't found ressource or file for name '%s'", name ) );
            }
        }

        throw new IOException( String.format( "can't found ressource '%s'", name ) );
    }

    public static void logHeader( Logger LOGGER, Level level, String header )
    {
        String tmpHeader = null != header && !header.isEmpty() ? " " + header + " " : "";
        String pp        = "==============================";
        int    l         = tmpHeader.length();
        int    h         = ( pp.length() * 2 - l ) / 2;
        int    b         = h + ( 60 - 2 * h - l );
        LOGGER.log( level, "{}{}{}", pp.substring( 0, b ), tmpHeader, pp.substring( 0, h ) );
    }
}
