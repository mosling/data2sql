package com.sforce.cc.tools.data2sql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sforce.cc.tools.data2sql.splitter.Splitter;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@JsonIgnoreProperties( ignoreUnknown = true )
public class Mapping
{
    private static final Logger LOGGER        = LogManager.getLogger( Mapping.class );
    private static final Level  always        = Level.getLevel( "ALWAYS" );
    private static final int    DEFAULT_ORDER = 1;

    // @formatter:off
    @Setter @Getter private Map<String, MappingTable> dbMapping   = new TreeMap<>(  );
    @Getter @Setter private List<String>              afterAll    = new ArrayList<>(  );
    @Getter @Setter private List<String>              beforeAll   = new ArrayList<>(  );
    @Getter @Setter private MappingOptions            options     = new MappingOptions();
    @Getter @Setter private String                    comment     = "";

    // some command line arguments
    @JsonIgnore @Getter @Setter private String     mappingFilename   = "";
    @JsonIgnore @Getter @Setter private String     inputFilename     = "";
    @JsonIgnore @Getter @Setter private String     outputFilename    = "";
    @JsonIgnore @Getter @Setter private String     analyzeFilename   = "";
    @JsonIgnore @Getter @Setter private boolean    dropTables        = true;
    @JsonIgnore @Getter @Setter private boolean    showAnalyzedData  = false;
    @JsonIgnore @Getter @Setter private int        groupCount        = 1;
    @JsonIgnore private Map<String, List<String> > errorCategory     = new TreeMap<>(  );
    @Getter @Setter private AtomicInteger          countIgnoredNodes = new AtomicInteger( 0 );

    // simple variant to show an error only once
    private List<String> errorShown = new ArrayList<>();
    // @formatter:on

    public Mapping()
    {
        // Jackson need the default constructor
    }

    boolean checkMappingAttributes()
    {
        Long c = dbMapping.entrySet().stream().filter( e -> !e.getValue().checkDataConsistence( e.getKey() ) ).count();
        return c == 0;
    }

    @SuppressWarnings( "squid:S3776" )
    void showErrorCategories()
    {
        if ( errorCategory.isEmpty() )
            return;

        LOGGER.log( always, "Missing mandatory attribute: <attr-name> || <mapping-block> || <target table> " );

        Map<String, Integer> errorCountingMap = new TreeMap<>();
        for ( String es : options.getErrorCountOnlyFor() )
        {
            errorCountingMap.put( es, 0 );
        }

        List<String> toc    = new ArrayList<>();
        String       spaces = "                  ";
        for ( Map.Entry<String, List<String>> e : errorCategory.entrySet() )
        {
            errorCountingMap.replaceAll( ( k, v ) -> 0 );
            String sizeStr = String.valueOf( e.getValue().size() );
            String hs      = spaces.substring( 0,
                    Math.max( 0, spaces.length() - sizeStr.length() - " times ".length() ) );
            String sCatErr = String.format( "%s%s times : %s", hs, sizeStr, e.getKey() );
            toc.add( sCatErr );
            LOGGER.log( always, sCatErr );
            Collections.sort( e.getValue() );
            long emptyCount = 0;
            for ( String s : e.getValue() )
            {
                if ( s.isEmpty() )
                {
                    emptyCount++;
                    continue;
                }

                boolean show = true;
                for ( String es : options.getErrorCountOnlyFor() )
                {
                    if ( s.contains( es ) )
                    {
                        show = false;
                        errorCountingMap.computeIfPresent( es, ( k, v ) -> v + 1 );
                    }
                }
                if ( show || !options.isShortErrorMsg() )
                {
                    LOGGER.log( always, "{}: {} ", spaces, s );
                }
            }

            if ( emptyCount > 0 )
            {
                LOGGER.log( always, "{}: {} empty entries found.", spaces, emptyCount );
            }

            for ( Map.Entry<String, Integer> err : errorCountingMap.entrySet() )
            {
                if ( err.getValue() > 0 )
                {
                    String d = spaces.substring( 0, Math.max( 0, spaces.length() - err.getKey().length() ) );
                    LOGGER.log( always, "{}{} : counted {} times ", d, err.getKey(), err.getValue() );
                }
            }
        }

        Functions.logHeader( LOGGER, always, "error category TOC" );
        LOGGER.log( always, "Missing mandatory attributes: <attr-name> || <mapping-block> || <target table> " );
        toc.forEach( s -> LOGGER.log( always, s ) );
    }

    /**
     * The main method for transforming the data from the Map<ColumnName, List<ColumnData>> to Output.
     *
     * @param options      options read from mapping file
     * @param mappingBlock the name of the mapping block from the transformation description
     * @param rowData      for every attribute(i.e. column) a list of values
     * @return (possible empty) list of ready to use dml statements Short process overview: 1. check that we have a
     * mapping described for the given mappingBlock 2. check that all mandatory attribute from the mapping exists in
     * rowData 3. check that every rowData with size greater 1 have the same size, this simple generate size
     * statements (we will not support a rollover mechanism, this matches not the reality and confuses everyone) 4.
     * rowData has no data for mandatory fields, return empty list 5. generate size dml statements
     */
    @SuppressWarnings( "squid:S3776" )
    private List<String> generateDmlStatement( Splitter splitter, MappingOptions options, String mappingBlock,
            Map<String, List<String>> rowData )
    {
        MappingTable mappingTable = dbMapping.get( mappingBlock );

        if ( null == mappingTable )
        {
            return Collections.emptyList();
        }

        List<String> columns    = new ArrayList<>();
        List<String> values     = new ArrayList<>();
        boolean      dnSeen     = false;
        int          rows       = 1;
        String       maxRowAttr = "";
        boolean      matchAll   = true;
        Integer      matchCount = 0;

        for ( List<String> sl : mappingTable.getAttribs() )
        {
            String attr = sl.get( 0 );
            if ( "_".equalsIgnoreCase( attr ) )
            {
                matchCount++;
                continue;
            }

            if ( !rowData.containsKey( attr ) )
            {
                boolean optional = mappingTable.getOptionalAttribs().contains( attr );
                Level   ll       = Level.getLevel( optional ? "WARN" : "ERROR" );
                if ( !options.isShortErrorMsg() )
                {
                    if ( !dnSeen )
                    {
                        LOGGER.log( ll, "transforming entry: {}", splitter.getErrorLocation( rowData ) );
                        LOGGER.log( ll, "   with mapping #{}# for target #{}#", mappingBlock, mappingTable.getTable() );
                        if ( LOGGER.getLevel().intLevel() >= ll.intLevel() )
                        {
                            dnSeen = true;
                        }
                    }
                    LOGGER.log( ll, "            no data for {} attribute #{}#", optional ? "optional" : "mandatory",
                            attr );
                }

                if ( !optional )
                {
                    String x = MessageFormat.format( "''{0}'' || at ''{1}'' || for target ''{2}''", attr, mappingBlock,
                            mappingTable.getTable() );
                    if ( !errorCategory.containsKey( x ) )
                    {
                        errorCategory.put( x, new ArrayList<>() );
                    }

                    StringBuilder sb = new StringBuilder();
                    if ( !mappingTable.getFriendlyNames().isEmpty() )
                    {
                        for ( String fn : mappingTable.getFriendlyNames() )
                        {
                            if ( sb.length() > 0 )
                            {
                                sb.append( " -- " );
                            }

                            if ( rowData.containsKey( fn ) )
                            {
                                sb.append( rowData.get( fn ).get( 0 ) );
                            }
                            else
                            {
                                sb.append( "<" );
                                sb.append( fn );
                                sb.append( ">" );
                            }
                        }
                        sb.append( " (" );
                        sb.append( splitter.getErrorLocation( rowData ) );
                        sb.append( ")" );

                    }
                    else
                    {
                        sb.append( splitter.getErrorLocation( rowData ) );
                    }

                    errorCategory.get( x ).add( sb.toString() );
                }

                matchAll = matchAll && optional;
            }
            else
            {
                matchCount++;
                int countData = rowData.get( attr ).size();
                if ( countData > 1 )
                {
                    if ( rows > 1 && ( countData != rows ) )
                    {
                        LOGGER.error( "transforming entry: {}", splitter.getErrorLocation( rowData ) );
                        dnSeen = true;
                        LOGGER.error( "   found attributes with different sizes {}[{}] and {}[{}] ", attr, countData,
                                maxRowAttr, rows );
                        LOGGER.error( "   use max number and from all other attributes "
                                + "we use the entry 0 for each index greater the size of the attribute" );
                    }
                    else
                    {
                        rows = countData;
                        maxRowAttr = attr;
                    }
                }
            }
        }

        // create no output for missing attributes or to less attributes
        if ( !matchAll || 0 == matchCount )
        {
            if ( !options.isShortErrorMsg() )
            {
                rowData.forEach( ( k, v ) -> {
                    String s = v.stream().collect( Collectors.joining( "," ) );
                    LOGGER.error( "                 {} := {}", k, s.substring( 0, Math.min( 60, s.length() ) ) );
                } );
            }

            return Collections.emptyList();
        }

        // create maximal rows entries
        // example if we have attribute (a1, x, y); (a2, 5); (a3, 1, 2, 3) that will create this entries
        // (x, 5, 1); (y, 5, 2) and (x, 5, 3)
        List<String> dmlList = new ArrayList<>();
        for ( int r = 0; r < rows; ++r )
        {
            columns.clear();
            values.clear();

            // adding values for all attributes, to conserv the original order
            for ( List<String> sl : mappingTable.getAttribs() )
            {
                String key = sl.get( 0 );
                if ( "_".equalsIgnoreCase( key ) )
                {
                    columns.add( sl.get( 1 ) );
                    values.add( sl.get( 2 ) );
                }
                else if ( rowData.containsKey( key ) )
                {
                    String cn    = mappingTable.getColumn( key );
                    int    index = rowData.get( key ).size() <= r ? 0 : r;
                    String d     = splitter.replaceTemplate( mappingTable, key, rowData.get( key ).get( index ) );
                    if ( !cn.isEmpty() && !d.isEmpty() )
                    {
                        columns.add( cn );
                        values.add( d );
                    }
                }
                else if ( 4 <= sl.size() )
                {
                    // add an optional default value for non exiting values
                    columns.add( sl.get( 1 ) );
                    values.add( sl.get( 3 ) );
                }
            }

            mappingTable.getElements().getAndIncrement();

            if ( options.isDataOnly() )
            {
                dmlList.add( values.stream().collect( Collectors.joining( options.getDataOnlySeparator() ) ) );
            }
            else
            {
                if ( !columns.contains( "_update_" ) )
                {
                    dmlList.add( "INSERT into " + mappingTable.getTable() + "(" + columns
                            .stream()
                            .collect( Collectors.joining( "," ) ) + ") values (" + values
                            .stream()
                            .collect( Collectors.joining( "," ) ) + ");" );
                }
                else
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( "UPDATE " ).append( mappingTable.getTable() ).append( " " );
                    boolean       firstColumn   = true;
                    StringBuilder updateKeyData = new StringBuilder();
                    for ( int c = 0; c < columns.size(); c++ )
                    {
                        if ( columns.get( c ).equalsIgnoreCase( "_update_" ) )
                        {
                            if ( updateKeyData.length() != 0 )
                            {
                                updateKeyData.append( " AND " );
                            }
                            updateKeyData.append( values.get( c ) );
                        }
                        else
                        {
                            if ( firstColumn )
                            {
                                sb.append( "SET " );
                                firstColumn = false;
                            }
                            else
                            {
                                sb.append( ", " );
                            }
                            sb.append( columns.get( c ) ).append( "=" ).append( values.get( c ) );
                        }
                    }

                    if ( updateKeyData.length() == 0 )
                    {
                        updateKeyData.append( "<missing update attrib '_update_'>" );
                    }

                    sb.append( " where " );
                    sb.append( updateKeyData );
                    sb.append( ";" );

                    dmlList.add( sb.toString() );

                }
            }
        }

        return dmlList;
    }

    @SuppressWarnings( "squid:S3776" )
    public void doTransformation( Splitter splitter, String tabname, Map<String, List<String>> tabData,
            MappingResult mr )
    {
        // create DML statements for the table and all its successors,
        // this operation isn't transitive!
        MappingTable mappingTable = dbMapping.get( tabname );
        if ( null != mappingTable )
        {
            int parentOrder = mappingTable.getOrder();
            int sc          = mr.addToDmlStatements( parentOrder,
                    generateDmlStatement( splitter, options, tabname, tabData ) );
            LOGGER.debug( "transform primary table {}: add {} statements", tabname, sc );
            int arrayOrder = parentOrder;
            for ( String str : mappingTable.getSuccessors() )
            {
                arrayOrder++;
                String p = str;
                if ( p.contains( "?" ) )
                {
                    String queryAttr = p.substring( 0, p.indexOf( '?' ) );
                    if ( !tabData.containsKey( queryAttr ) )
                    {
                        continue;
                    }
                    p = p.substring( p.indexOf( '?' ) + 1 );
                }
                String sucTabname = p + "-" + tabname;
                if ( dbMapping.containsKey( sucTabname ) )
                {
                    boolean      inheritNames = false;
                    MappingTable subMapping   = dbMapping.get( sucTabname );
                    int          childOrder   = subMapping.getOrder();
                    childOrder = DEFAULT_ORDER == childOrder ? arrayOrder : childOrder;
                    subMapping.setOrder( childOrder );
                    if ( subMapping.getFriendlyNames().isEmpty() )
                    {
                        subMapping.setFriendlyNames( mappingTable.getFriendlyNames() );
                        inheritNames = true;

                    }

                    if ( !subMapping.getSplitData().isEmpty() && !errorShown.contains( "splitData-" + sucTabname ) )
                    {
                        errorShown.add( "splitData-" + sucTabname );
                        LOGGER.fatal( "splitting data isn't available for sub tables, please move the entry from "
                                + "'{}' to '{}'", sucTabname, tabname );
                    }

                    sc = mr.addToDmlStatements( childOrder,
                            generateDmlStatement( splitter, options, sucTabname, tabData ) );
                    LOGGER.debug( "      call successor: {} add {} statements", sucTabname, sc );
                    if ( inheritNames )
                    {
                        subMapping.setFriendlyNames( Collections.emptyList() );
                    }
                }
                else
                {
                    LOGGER.error( "No dbMapping for successor '{}' found, should be '{}'", str, sucTabname );
                }
            }
        }
    }

}
