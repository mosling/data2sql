package com.sforce.cc.tools.data2sql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MappingTable
{
    private static final Logger LOGGER = LogManager.getLogger( MappingTable.class );

    // @formatter:off
    @Setter @Getter private String table                   = "";
    @Getter @Setter private String description             = "<missing description>";
    @Getter @Setter private Integer order                  = 1;
    @Getter @Setter private List<String> successors        = Collections.emptyList();
    @Getter @Setter private List<String> optionalAttribs   = Collections.emptyList();
    @Getter @Setter private List<String> fullDataAttribs   = Collections.emptyList();
    @Setter @Getter private List<List<String>> attribs     = Collections.emptyList();
    @Getter @Setter private Map<String, String > splitData = new TreeMap<>(  );
    @Setter @Getter private List<String> friendlyNames     = Collections.emptyList();

    @JsonIgnore @Getter @Setter private AtomicInteger elements = new AtomicInteger( 0 );
    @JsonIgnore private Map<String, List<String>> columnAccess = new TreeMap<>(  );
    // @formatter:on

    private MappingTable()
    {
        // Jackson need the default constructor
    }

    boolean checkDataConsistence( String m )
    {
        boolean      result = true;
        List<String> al     = new ArrayList<>();

        for ( List<String> cl : attribs )
        {
            String an = cl.get( 0 );

            if ( cl.size() != 3 )
            {
                LOGGER.fatal( "attribute needs 3 parts {}.'{}' has {}", m, an, cl.size() );
            }

            if ( al.contains( an ) )
            {
                LOGGER.fatal( "duplicate attribute name {}.'{}' -- only one is coincidentally used", m, an );
                result = false;
            }
            else
            {
                al.add( an );
            }
        }

        return result;
    }

    // column is the entry with index 1 in the attribute list
    public String getColumn( String attribute )
    {
        List<String> l = findEntryForAttribute( attribute );
        if ( !l.isEmpty() )
        {
            return l.get( 1 );
        }
        return "";
    }

    // Every dbMapping has a number of attribs where each attrib
    // has three parts (attribute, db-column-name, insert-data)
    // Returning empty list or the list of this three values.
    public List<String> findEntryForAttribute( String attribute )
    {
        if ( columnAccess.containsKey( attribute ) )
        {
            return columnAccess.get( attribute );
        }
        else
        {
            for ( List<String> cl : attribs )
            {
                if ( attribute.equalsIgnoreCase( cl.get( 0 ) ) )
                {
                    columnAccess.put( attribute, cl );
                    return cl;
                }
            }
        }
        return Collections.emptyList();
    }
}
