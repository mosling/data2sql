package com.sforce.cc.tools.data2sql;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MappingResult
{
    private static final Logger LOGGER = LogManager.getLogger( MappingResult.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    // list of resulting DML statements, where integer is used as execution order
    private Map<Integer, List<String>> dmlStatements = new TreeMap<>();

    // rows per table
    private Map<String, AtomicInteger> tables = new TreeMap<>();

    // ignored input nodes
    private Map<String, AtomicInteger> ignoredNodes = new TreeMap<>();

    // attribute per table
    private Map<String, AttributeData> attributes = new TreeMap<>();

    private Mapping mapping;

    private Boolean analyze = false;

    private MappingResult()
    {
        // need to initialize with Mapping object
        mapping = new Mapping();
    }

    public MappingResult( Mapping mapping )
    {
        this.mapping = mapping;
        analyze = !mapping.getAnalyzeFilename().isEmpty();
    }

    /**
     * @param orderIdx      the order for this list of statements
     * @param statementList the staments
     * @return number of added statements
     */
    public int addToDmlStatements( int orderIdx, @Nonnull List<String> statementList )
    {
        dmlStatements.computeIfAbsent( orderIdx, k -> new ArrayList<>() ).addAll( statementList );
        return statementList.size();
    }

    public void showIgnoredNodes()
    {
        Functions.logHeader( LOGGER, always, "ignored input data nodes" );
        ignoredNodes.forEach( ( k, c ) -> LOGGER.log( always, "{} : {} entries", k, c ) );
    }

    public void showIgnoredSummary()
    {
        int sum = ignoredNodes.values().stream().mapToInt( Number::intValue ).sum();

        Functions.logHeader( LOGGER, always, "summary of untransformed data nodes" );
        LOGGER.log( always, "        count of nodes without mapping : {}", ignoredNodes.size() );
        LOGGER.log( always, "                          with entries : {}", sum );
        LOGGER.log( always, "count of intentionally ignored entries : {}", mapping.getCountIgnoredNodes() );
    }

    public void showMappingOrder()
    {
        Map<Integer, List<String>> so = new TreeMap<>();
        for ( Map.Entry<String, MappingTable> entry : mapping.getDbMapping().entrySet() )
        {
            int          o  = entry.getValue().getOrder();
            List<String> oe = so.computeIfAbsent( o, k -> new ArrayList<>() );
            oe.add( String.format( "%s -- %s -- #%s", entry.getKey(), entry.getValue().getTable(),
                    entry.getValue().getElements() ) );
        }

        Functions.logHeader( LOGGER, always, "generated output order" );
        so.forEach( ( k, l ) -> l.forEach( v -> LOGGER.log( always, "{} : {}", k, v ) ) );
    }

    public void outputSqlStatements()
    {
        Functions.logHeader( LOGGER, always, "output sql statements" );

        addToDmlStatements( 0, mapping.getBeforeAll() );

        if ( mapping.getAfterAll() != null )
        {
            mapping
                    .getAfterAll()
                    .stream()
                    .filter( s -> !s.startsWith( "drop" ) || mapping.isDropTables() )
                    .forEach( s -> addToDmlStatements( Integer.MAX_VALUE, Collections.singletonList( s ) ) );
        }

        try (FileOutputStream os = new FileOutputStream( mapping.getOutputFilename() );
             Writer writer = new BufferedWriter( new OutputStreamWriter( os, "utf-8" ) ))
        {
            for ( List<String> l : dmlStatements.values() )
            {
                for ( String s : l )
                {
                    writer.write( s );
                    writer.write( "\n" );
                }
            }
        }
        catch ( IOException e )
        {
            LOGGER.error( e );
        }
    }

    // add the table name to the Map, so in the end we have something like select count(*) from tabname
    public void incTable( String tabname )
    {
        if ( analyze )
        {
            tables.computeIfAbsent( tabname, k -> new AtomicInteger( 0 ) ).incrementAndGet();
        }
    }

    // add the table name to the Map, so in the end we have something like select count(*) from tabname
    public boolean incIgnoredNode( String tabname )
    {
        return 1 == ignoredNodes.computeIfAbsent( tabname, k -> new AtomicInteger( 0 ) ).incrementAndGet();
    }

    public void addAttribute( String tabname, String attrname, String attrdata )
    {
        if ( analyze )
        {
            attributes.computeIfAbsent( attrname, k -> new AttributeData() ).addData( tabname, attrdata );
        }
    }

    @SuppressWarnings( "squid:S3776" )
    public void analyzeData()
    {
        if ( !analyze )
        {
            return;
        }

        Functions.logHeader( LOGGER, always, "analyzing data" );
        try (BufferedWriter bw = new BufferedWriter( new FileWriter( mapping.getAnalyzeFilename() ) ))
        {
            for ( Map.Entry<String, AtomicInteger> entry : tables.entrySet() )
            {
                String t    = entry.getKey();
                int    rows = entry.getValue().get();

                for ( Map.Entry<String, AttributeData> attr : attributes.entrySet() )
                {
                    if ( attr.getValue().tables().containsKey( t ) )
                    {
                        bw.write( "=======TABLE: " + t + " ============================\n" );
                        bw.write( "        ROWS: " + rows + "\n" );
                        bw.write( MessageFormat.format( "   ATTRIBUTE: [{0}] {1}\n", attr.getKey(), attr.getValue() ) );
                        String longestData = attr.getValue().longestString( t );
                        bw.write( "      COLUMN: " );
                        bw.write(
                                attr.getValue().isNumber( t ) ? "INTEGER" : "VARCHAR(" + longestData.length() + ")\n" );
                        bw.write( MessageFormat.format( "  MAX LENGTH: ({0}) {1}\n", longestData.length(),
                                longestData ) );
                        long empty = attr.getValue().emptyEntriesPerTable( t );
                        bw.write( "       EMPTY: " + empty + "\n" );

                        String todo = "    Relation: ";

                        long entries = attr.getValue().entriesPerTable( t );
                        long groups  = attr.getValue().groupsPerTable( t, mapping.getGroupCount() );

                        if ( entries <= rows )
                        {
                            // decrement groups by 1 if more than 1 empty entry (was counted as group)
                            if ( groups > 1 )
                            {
                                bw.write( todo + "Foreign Key Column" + "\n" );
                            }
                            else
                            {
                                bw.write( todo + "Table Column" + "\n" );
                            }
                        }
                        else
                        {
                            bw.write( todo + " n:m Relation" + "\n" );
                        }

                        String g = attr.getValue().toGroupTable( t, mapping.getGroupCount() );
                        if ( !g.isEmpty() )
                        {
                            bw.write( "      GROUPS: " + g + "\n" );
                        }

                        if ( mapping.isShowAnalyzedData() )
                        {
                            bw.write( "        DATA: " + attr.getValue().toStringTable( t ) );
                        }
                    }
                }
            }
        }
        catch ( IOException e )
        {
            LOGGER.error( e );
        }
    }
}
