package com.sforce.cc.tools.data2sql.memberOf;

import com.sforce.cc.tools.data2sql.Functions;
import com.sforce.cc.tools.data2sql.MappingResult;
import com.sforce.cc.tools.data2sql.splitter.Splitter;
import com.sforce.cc.tools.data2sql.splitter.SplitterFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainMemberOf
{
    private static final Logger LOGGER = LogManager.getLogger( MainMemberOf.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    public static void main( String[] args )
    {
        if ( args.length == 0 )
        {
            Functions.logHeader( LOGGER, always, "usage" );
            LOGGER.log( always, "Please start with <input-filename>" );

            return;
        }

        String inputType = "ldif";

        MappingMemberOf mapping = new MappingMemberOf();
        mapping.setInputFilename( args[0] );
        Splitter splitter = SplitterFactory.getSplitter( "ldif", mapping );

        Functions.logHeader( LOGGER, always, "parameter overview" );
        LOGGER.log( always, "input file format      : '{}'", inputType );
        LOGGER.log( always, "transform input file   : '{}'", mapping.getInputFilename() );

        MappingResult mr = splitter.execute();

        mapping.checkConsistency();
        mr.showIgnoredSummary();

        Functions.logHeader( LOGGER, always, "" );
    }

}
