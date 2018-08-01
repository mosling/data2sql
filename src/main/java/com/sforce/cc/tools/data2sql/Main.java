package com.sforce.cc.tools.data2sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sforce.cc.tools.data2sql.splitter.Splitter;
import com.sforce.cc.tools.data2sql.splitter.SplitterFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public class Main
{
    private static final Logger LOGGER = LogManager.getLogger( Main.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    @SuppressWarnings( "squid:S3776" )
    public static void main( String[] args )
    {
        if ( args.length <= 2 )
        {
            Functions.logHeader( LOGGER, always, "usage" );
            LOGGER.log( always, "Please start with <input-filename> <mapping-filename> <output-filename> " );
            LOGGER.log( always, "                  [analyze=<analyze-output> ['showdata'] ['group-count'=<number>]]" );
            LOGGER.log( always, "                  ['ldif'|'csv'] ['nodrop'] ['noerror']" );
            LOGGER.log( always, "  input-filename : file to translate" );
            LOGGER.log( always, "mapping-filename : mapping file in yaml format" );
            LOGGER.log( always, " output-filename : name of the generated output file" );
            LOGGER.log( always, "  analyze-output : anaylyze the data and give some hints for SQL transformation" );
            LOGGER.log( always, "        showdata : add data to the analyze datafile" );
            LOGGER.log( always, "     group-count : used for analyzing data" );
            LOGGER.log( always, "                 : a group starts with more as <number> equal values (default 1) " );
            LOGGER.log( always, "       ldif, cvs : type of the input file (default is ldif)" );
            LOGGER.log( always, "          nodrop : don't add all pre- postcondition lines starting with drop ..." );

            return;
        }

        boolean showErrorAtEnd = true;
        try (InputStream s = Functions.getInputStreamFromName( args[1], true ))
        {
            ObjectMapper mapper = new ObjectMapper( new YAMLFactory() );

            Mapping dbMapping = mapper.readValue( s, Mapping.class );
            Functions.logHeader( LOGGER, always,"check mapping attributs" );
            if ( !dbMapping.checkMappingAttributes() )
            {
                return;
            }
            else
            {
                LOGGER.log( always, "ok." );
            }

            String inputType = "ldif";

            if ( args.length > 3 )
            {
                for ( int a = 3; a < args.length; ++a )
                    if ( "nodrop".equalsIgnoreCase( args[a] ) )
                    {
                        dbMapping.setDropTables( false );
                    }
                    else if ( "ldif".equalsIgnoreCase( args[a] ) )
                    {
                        inputType = "ldif";
                    }
                    else if ( "csv".equalsIgnoreCase( args[a] ) )
                    {
                        inputType = "csv";
                    }
                    else if ( "showdata".equalsIgnoreCase( args[a] ) )
                    {
                        dbMapping.setShowAnalyzedData( true );
                    }
                    else if ( args[a].startsWith( "analyze=" ) )
                    {
                        String[] x = args[a].split( "=" );
                        if ( x.length > 1 )
                        {
                            dbMapping.setAnalyzeFilename( x[1] );
                        }
                    }
                    else if ( args[a].startsWith( "group-count=" ) )
                    {
                        String[] x = args[a].split( "=" );
                        if ( x.length > 1 )
                        {
                            dbMapping.setGroupCount( Integer.parseInt( x[1] ) );
                        }
                    }
                    else
                    {
                        LOGGER.error( "unknown parameter {} ", args[a] );
                    }
            }

            dbMapping.setMappingFilename( args[1] );
            dbMapping.setInputFilename( args[0] );
            dbMapping.setOutputFilename( args[2] );

            Functions.logHeader( LOGGER, always,"parameter overview" );
            LOGGER.log( always, "input file format      : '{}'", inputType );
            LOGGER.log( always, "transform input file   : '{}'", dbMapping.getInputFilename() );
            LOGGER.log( always, "using mapping file     : '{}'", dbMapping.getMappingFilename() );
            boolean analyze = !dbMapping.getAnalyzeFilename().isEmpty();
            if ( analyze )
            {
                LOGGER.log( always, "analyze results        : '{}'", dbMapping.getAnalyzeFilename() );
                LOGGER.log( always, "         show-all-data : {}", dbMapping.isShowAnalyzedData() ? "yes" : "no" );
                LOGGER.log( always, "   group element count : more than {}", dbMapping.getGroupCount() );
            }
            LOGGER.log( always, "drop temporary tables  : '{}'", dbMapping.isDropTables() );
            LOGGER.log( always, "generated output file  : '{}'", dbMapping.getOutputFilename() );

            Splitter splitter = SplitterFactory.getSplitter( inputType, dbMapping );
            if ( splitter == null)
            {
                LOGGER.fatal( "can't find splitter for input type {} (must be ldif or csv)", inputType );
                return;
            }

            MappingResult mr = splitter.execute( );

            mr.showIgnoredNodes();
            mr.showMappingOrder();
            mr.outputSqlStatements();
            mr.analyzeData();

            if ( showErrorAtEnd )
            {
                dbMapping.showErrorCategories();
            }

            mr.showIgnoredSummary();
            Functions.logHeader( LOGGER, always,"" );
        }
        catch ( IOException e )
        {
            LOGGER.fatal( e );
        }
    }

}
