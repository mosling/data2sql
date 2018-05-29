package com.sforce.cc.tools.data2sql.splitter;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.sforce.cc.tools.data2sql.Functions;
import com.sforce.cc.tools.data2sql.Mapping;
import com.sforce.cc.tools.data2sql.MappingOptions;
import com.sforce.cc.tools.data2sql.MappingResult;
import com.sforce.cc.tools.data2sql.MappingTable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvSplitter
        implements Splitter
{

    private static final Logger LOGGER = LogManager.getLogger( CsvSplitter.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    private Mapping       mapping;
    private MappingResult mr;

    public CsvSplitter( Mapping mapping )
    {
        this.mapping = mapping;
        mr = new MappingResult( mapping );
    }

    @Override
    public MappingResult execute()
    {
        Functions.logHeader( LOGGER, always, "reading CSV file(s)" );

        try
        {
            File   csvFile = new File( mapping.getInputFilename() );
            String tabname = csvFile.getName().replace( ".csv", "" );

            CsvMapper mapper = new CsvMapper();
            // use first row as header
            CsvSchema schema = CsvSchema
                    .emptySchema()
                    .withColumnSeparator( mapping.getOptions().getCsv().getSeparator() )
                    .withHeader();

            MappingIterator<Map<String, String>> it = mapper
                    .readerFor( Map.class )
                    .with( schema )
                    .readValues( csvFile );

            while ( it.hasNext() )
            {
                Map<String, String>       csvRow  = it.next();
                Map<String, List<String>> tabData = new HashMap<>();

                mr.incTable( tabname );
                for ( Map.Entry<String, String> c : csvRow.entrySet() )
                {
                    mr.addAttribute( tabname, c.getKey(), c.getValue() );
                    if ( !c.getValue().isEmpty() )
                    {
                        tabData.put( c.getKey(), Collections.singletonList( c.getValue() ) );
                    }
                }

                mapping.doTransformation( this, tabname, tabData, mr );
            }
        }
        catch ( IOException e )
        {
            LOGGER.error( "Can't open file: {}", mapping.getInputFilename() );
            LOGGER.error( e );
        }

        return mr;
    }

    @Override
    public String replaceTemplate( MappingTable mappingTable, String attribute, String data )
    {
        List<String>   attributeMapping = mappingTable.findEntryForAttribute( attribute );
        MappingOptions options          = mapping.getOptions();

        if ( !attributeMapping.isEmpty() )
        {
            return attributeMapping.get( 2 ).replace( "$$", options.quoteData( data ) );
        }
        return "";
    }

    @Override
    public String getErrorLocation( Map<String, List<String>> rowData )
    {
        return mapping.getInputFilename();
    }
}
