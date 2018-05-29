package com.sforce.cc.tools.data2sql.splitter;

import com.sforce.cc.tools.data2sql.Functions;
import com.sforce.cc.tools.data2sql.Mapping;
import com.sforce.cc.tools.data2sql.MappingOptions;
import com.sforce.cc.tools.data2sql.MappingResult;
import com.sforce.cc.tools.data2sql.MappingTable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.regex.qual.Regex;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LdifSplitter
        implements Splitter
{
    private static final Logger LOGGER = LogManager.getLogger( LdifSplitter.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    private @Regex( 2 ) Pattern pattern = Pattern.compile( "\\[([0-9+])\\]=(.+)" );
    private Mapping       mapping;
    private MappingResult mr;

    LdifSplitter( Mapping mapping )
    {
        this.mapping = mapping;
        mr = new MappingResult( mapping );
    }

    @Override
    public MappingResult execute()
    {

        Functions.logHeader( LOGGER, always, "reading ldif file" );
        try (BufferedReader br = new BufferedReader( new FileReader( mapping.getInputFilename() ) ))
        {
            String        line;
            StringBuilder ldifLine = new StringBuilder();
            List<String>  dnentry  = new ArrayList<>();
            while ( ( line = br.readLine() ) != null )
            {
                if ( line.isEmpty() )
                {
                    finishDnEntry( ldifLine.toString(), dnentry );
                    ldifLine.setLength( 0 );
                }
                else if ( line.startsWith( " " ) )
                {
                    // additional line for entry, remove the first character only
                    ldifLine.append( line.substring( 1, line.length() ) );
                }
                else
                {
                    // start line, add the last if exists
                    if ( ldifLine.length() > 0 )
                    {
                        dnentry.add( ldifLine.toString() );
                    }
                    ldifLine.setLength( 0 );
                    ldifLine.append( line );
                }
            }
            finishDnEntry( ldifLine.toString(), dnentry );
        }
        catch ( IOException e )
        {
            LOGGER.error( e );
        }

        return mr;
    }

    // Methods return the data part for an insert or update statement. The
    // third part of the attribute mapping contains a template for this where
    // '$$' sign is a placeholder for the data.
    // If the data is a DN to another LDIF entry we use only the primary key part of this
    // data.
    // In the case of multiple rows the ## is used to place the current row number starting at 0
    // If the data start with [<index>]=<data> we split it and replace '##' with the index
    @Override
    public String replaceTemplate(MappingTable mappingTable, String attribute, @Nonnull String data )
    {
        List<String>   attributeMapping = mappingTable.findEntryForAttribute( attribute );
        MappingOptions options          = mapping.getOptions();

        if ( !attributeMapping.isEmpty() )
        {
            String            d     = data;
            Optional<Integer> index = Optional.empty();

            Matcher matcher = pattern.matcher( data );
            if ( matcher.matches() && matcher.groupCount() == 2 )
            {
                String dm = matcher.group( 2 );
                d = dm != null ? dm : "";

                String im = matcher.group( 1 );
                if ( null != im )
                {
                    try
                    {
                        index = Optional.of( Integer.parseInt( im ) );
                    }
                    catch ( NumberFormatException e )
                    {
                        LOGGER.fatal( "error parsing index '{}' at data '{}'", im, data );
                    }
                }
            }

            // extract key 'XYZ' from ldif link ou=XYZ,...,dc=abc,dc=com
            // where dc=abc,dc=com is in the fkEndings list
            if ( !mappingTable.getFullDataAttribs().contains( attribute ) && options.getLdif().isLdifLink( d ) )
            {
                int cp = d.indexOf( ',' );
                if ( cp >= 0 )
                {
                    String ad = data.substring( 0, cp );
                    d = ad.substring( ad.indexOf( '=' ) + 1 );
                }
            }

            String retVal = attributeMapping.get( 2 ).replace( "$$", options.quoteData( d ) );
            if ( index.isPresent() )
            {
                retVal = retVal.replace( "##", index.get().toString() );
            }
            return retVal;
        }
        return "";
    }

    @Override
    public String getErrorLocation( Map<String, List<String>> rowData )
    {
        String tna = mapping.getOptions().getLdif().getTableNameAttribute();
        List<String> l = rowData.get(tna);
        if (null != l)
        {
            return l.get(0);
        }

        return ("(missing " + tna + " attribute)");
    }

    private void finishDnEntry( String ldifLine, List<String> dnentry )
    {
        // block ready
        if ( ldifLine.length() > 0 )
        {
            dnentry.add( ldifLine );
        }
        transformDnEntry( dnentry );
        dnentry.clear();
    }

    @SuppressWarnings( "squid:S3776" )
    private void transformDnEntry( List<String> ldifEntry )
    {
        if ( ldifEntry.isEmpty() )
        {
            return;
        }

        String tabname = "unknownTable";

        // parse attributes, every attribute can have more than one entry
        // we need a list to store alle values for each attribute-name
        Map<String, List<String>> tabData = new HashMap<>();
        LdifOptions               options = mapping.getOptions().getLdif();
        for ( String entryLine : ldifEntry )
        {
            int dp = entryLine.indexOf( ':' );
            if ( dp >= 0 )
            {
                // split the line at the first ':' in a attribute name and the attribute data
                String attrname = entryLine.substring( 0, dp ).trim();
                String attrdata = decode( entryLine.substring( dp + 1 ).trim() );

                if ( options.getIgnoredAttributes().contains( attrname ) )
                {
                    continue;
                }

                // this is the primary key for the entry
                if ( options.getTableNameAttribute().equalsIgnoreCase( attrname ) )
                {
                    // check if the entry can be ignored
                    for ( String in : options.getIgnoredNodes() )
                    {
                        if ( attrdata.endsWith( in ) )
                        {
                            LOGGER.error( "ignore intentionally DN entry '{}'", entryLine );
                            mapping.getCountIgnoredNodes().getAndIncrement();
                            return;
                        }
                    }
                    // generate tabname from <tabneAttribute (i.e. dn)>: uid=XYZ,<tabname>
                    String[] parts = attrdata.split( ",", 2 );
                    if ( parts.length < 2 )
                    {
                        LOGGER.warn( "ignore structural {} entry (no tablename) '{}'", options.getTableNameAttribute(),
                                entryLine );
                        return;
                    }
                    tabname = parts[1];

                    if ( null == mapping.getDbMapping().get( tabname ) )
                    {
                        LOGGER.warn( "ignore entry {} without described mapping '{}'", tabname, entryLine );
                        return;
                    }
                }

                // handle reference attributes
                // <ref-attrname>: <column>=<attrdata>,<ldap address as part of attrname>
                else if ( options.getReferenceAttributes().containsKey( attrname ) )
                {
                    int cp = attrdata.indexOf( ',' );
                    if ( cp >= 0 )
                    {
                        String ad = attrdata.substring( 0, cp );
                        String an = attrdata.substring( cp + 1 );
                        if ( options.getReferenceAttributes().get( attrname ) )
                        {
                            attrname = String.format( "%s-%s", attrname, an );
                        }
                        attrdata = ad.substring( ad.indexOf( '=' ) + 1 );
                    }
                }
                // handle meta attributes, conserve optional indices
                //  <meta-attrname>: <attrname>=<attrdata>
                //  <meta-attrname>: <attrname>=[n]=<attrdata>
                else if ( options.getMetaAttributes().contains( attrname ) )
                {
                    int ep = attrdata.indexOf( '=' );
                    if ( ep >= 0 )
                    {
                        attrname = String.format( "%s%s%s", attrname, "-", attrdata.substring( 0, ep ) );
                        attrdata = attrdata.substring( ep + 1 );
                    }
                }

                // add the attribute name with its data to the tabData structure
                // if data are available only, mapping check again if the first line wasn't
                // the table entry
                if ( null == mapping.getDbMapping().get( tabname ) )
                {
                    LOGGER.warn( "first ldif block line must be the table entry starting with {} but is '{}'",
                            options.getTableNameAttribute(), entryLine );
                    return;
                }

                List<String> newDataList = DataModifier.modifyData( mapping.getDbMapping().get( tabname ), attrname,
                        attrdata );
                if ( !newDataList.isEmpty() )
                {
                    if ( !tabData.containsKey( attrname ) )
                    {
                        tabData.put( attrname, new ArrayList<>() );
                    }
                    tabData.get( attrname ).addAll( newDataList );
                    mr.addAttribute( tabname, attrname, attrdata );
                }
            }
        }

        mr.incTable( tabname );
        mapping.doTransformation( this, tabname, tabData, mr );
    }

    private String decode( String str )
    {
        if ( str.startsWith( ": " ) )
        {
            try
            {
                return new String( Base64.getDecoder().decode( str.substring( 2 ) ), "utf-8" );
            }
            catch ( UnsupportedEncodingException e )
            {
                LOGGER.error( "error encoding '{}' {}", str, e );
            }
        }
        return str;
    }
}
