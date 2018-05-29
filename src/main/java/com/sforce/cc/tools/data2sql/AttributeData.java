package com.sforce.cc.tools.data2sql;

import lombok.Getter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * The attribute data class store all data for one attribute over the hole set of data. The data
 * are stored per table.
 */
@Accessors( fluent = true )
public class AttributeData
{
    @Getter private int countOverall = 0;

    // This holds the values for each table
    @Getter private Map<String, List<String>> tables = new TreeMap<>();

    private static Predicate<String> notEmpty = ( String it ) -> !it.isEmpty();

    void addData( @Nonnull String t, @Nonnull String d )
    {
        countOverall++;
        tables.computeIfAbsent( t, k -> new ArrayList<>() ).add( d );
    }

    public String toString()
    {
        return String.format( "count %d in %d different tables", countOverall, tables.size() );
    }

    long entriesPerTable( String t )
    {
        if ( tables.containsKey( t ) )
        {
            return tables.get( t ).size();
        }

        return 0;
    }

    String toStringTable( String t )
    {
        String s = "";
        if ( tables.containsKey( t ) )
        {
            s = "(" + Integer.toString( tables.get( t ).size() ) + ") ";
            s += tables
                    .get( t )
                    .stream()
                    .map( x -> x.substring( 0, min( 20, x.length() ) ) + ( x.length() > 20 ? "..." : "" ) )
                    .sorted()
                    .collect( Collectors.joining( ", " ) );
        }
        return s;
    }

    String longestString( String t )
    {
        if ( tables.containsKey( t ) )
        {
            Optional<String> s = tables.get( t ).stream().max( Comparator.comparing( String::length ) );
            if ( s.isPresent() )
            {
                return s.get();
            }
        }
        return "";
    }

    boolean isNumber( String t )
    {
        if ( tables.containsKey( t ) && null != tables.get(t))
        {
            Pattern pattern = Pattern.compile( "^\\d+$" );
            long    l       = tables.get( t ).stream().filter( pattern.asPredicate() ).count();

            return l == tables.get( t ).size();
        }
        return false;
    }

    long groupsPerTable( String t, long groupCount )
    {
        if ( tables.containsKey( t ) )
        {
            return tables
                    .get( t )
                    .stream()
                    .filter( notEmpty )
                    .collect( collectingAndThen( groupingBy( Function.identity(), counting() ), m -> {
                        m.values().removeIf( c -> c <= groupCount );
                        return m;
                    } ) )
                    .size();
        }

        return 0;
    }

    long emptyEntriesPerTable( String t )
    {
        if ( tables.containsKey( t ) )
        {
            return tables.get( t ).stream().filter( String::isEmpty ).count();
        }

        return 0;
    }

    String toGroupTable( String t, long groupCount )
    {
        StringBuilder s = new StringBuilder();
        if ( tables.containsKey( t ) )
        {
            Map<String, Long> result = tables
                    .get( t )
                    .stream()
                    .filter( notEmpty )
                    .collect( groupingBy( Function.identity(), counting() ) );

            if ( result.size() == tables.get( t ).size() )
            {
                s.setLength( 0 );
            }
            else
            {
                Map<String, Long> finalMap = new LinkedHashMap<>();
                result
                        .entrySet()
                        .stream()
                        .sorted( Map.Entry.<String, Long>comparingByValue().reversed() )
                        .forEachOrdered( e -> finalMap.put( e.getKey(), e.getValue() ) );

                s.append( "[#" );
                s.append( result.size() );
                s.append( "] " );
                for ( Map.Entry<String, Long> e : finalMap.entrySet() )
                {
                    if ( e.getValue() > groupCount )
                    {
                        s.append( e.getKey() );
                        s.append( "(" );
                        s.append( e.getValue().toString() );
                        s.append( ") " );
                    }
                }
            }
        }
        return s.toString();
    }

}
