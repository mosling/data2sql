package com.sforce.cc.tools.data2sql.memberOf;

import com.sforce.cc.tools.data2sql.Functions;
import com.sforce.cc.tools.data2sql.Mapping;
import com.sforce.cc.tools.data2sql.MappingResult;
import com.sforce.cc.tools.data2sql.splitter.Splitter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MappingMemberOf
        extends Mapping
{
    private static final Logger LOGGER = LogManager.getLogger( MappingMemberOf.class );
    private static final Level  always = Level.getLevel( "ALWAYS" );

    private static final String UK = "uniqueMember";
    private static final String MO = "memberOf";

    private Map<String, MemberData>      entrym     = new TreeMap<>();
    private Map<String, MemberReference> tabm       = new TreeMap<>();
    private Map<String, Integer>         tabOverall = new TreeMap<>();

    @Override
    public boolean hasMappingFor( String nodeName )
    {
        return true;
    }

    @Override
    public void doTransformation( Splitter splitter, String tabname, Map<String, List<String>> tabData,
            MappingResult mr )
    {
        List<String> ukl = tabData.getOrDefault( UK, Collections.emptyList() );
        List<String> mol = tabData.getOrDefault( MO, Collections.emptyList() );

        String pk = tabData.get( this.getOptions().getLdif().getTableNameAttribute() ).get( 0 );

        MemberData mod = new MemberData();
        mod.getUniqueMember().addAll( ukl );
        mod.getMemberOf().addAll( mol );

        if ( entrym.containsKey( pk ) )
        {
            LOGGER.error( "key {} exists twice in the ldif data", pk );
        }

        entrym.put( pk, mod );

        if ( ukl.size() + mol.size() > 0 )
        {
            if ( !tabm.containsKey( tabname ) )
            {
                tabm.put( tabname, new MemberReference() );
            }

            tabm.get( tabname ).addUniqueMember( ukl );
            tabm.get( tabname ).addMemberOf( mol );
            tabm.get( tabname ).addEntryCount( 1 );
        }

        tabOverall.put( tabname, 1 + tabOverall.getOrDefault( tabname, 0 ) );
    }

    private String f( int i, int l )
    {
        String p = String.format( "%%%dd", l );
        return String.format( p, i );
    }

    public void checkConsistency()
    {
        Functions.logHeader( LOGGER, always, "top 10 ldap nodes" );
        tabOverall
                .entrySet()
                .stream()
                .sorted( ( e1, e2 ) -> Integer.compare( e2.getValue(), e1.getValue() ) )
                .limit( 10 )
                .forEach( e -> LOGGER.info( "{} : {}", f( e.getValue(), 6 ), e.getKey() ) );

        Functions.logHeader( LOGGER, always, "uniqueMember split for ldap node name" );
        tabm.entrySet().stream().filter( e -> e.getValue().uniqueMemberSize() > 0 ).forEach( e -> {
            LOGGER.info( e.getKey() );
            e.getValue().getUniqueMemberMap().forEach( ( k, v ) -> LOGGER.info( "      {}: {}", f( v, 6 ), k ) );
        } );

        Functions.logHeader( LOGGER, always, "list of nodes with UK/MO" );
        LOGGER.info( "Count;  Unique; Member; ldap node name" );
        tabm
                .entrySet()
                .stream()
                .sorted( ( e1, e2 ) -> Integer.compare( e2.getValue().getCountEntries(),
                        e1.getValue().getCountEntries() ) )
                .forEach( e -> LOGGER.info( "{}; {}; {} for {}", f( e.getValue().getCountEntries(), 6 ),
                        f( e.getValue().uniqueMemberSize(), 6 ), f( e.getValue().memberOfSize(), 6 ), e.getKey() ) );

        Map<String, Integer> missingMemberOf    = new TreeMap<>();
        Map<String, Integer> uniqueMemeberCount = new TreeMap<>();

        // for each dn:Y and uniqueMember:X check if dn:X exists and memberOf:Y exists
        boolean sh = true;
        for ( String dn : entrym.keySet() )

        {
            for ( String um : entrym.get( dn ).getUniqueMember() )
            {
                String umTab = um.split( ",", 2 )[1];
                uniqueMemeberCount.put( umTab, 1 + uniqueMemeberCount.getOrDefault( umTab, 0 ) );

                if ( !entrym.containsKey( um ) )
                {
                    if ( sh )
                    {
                        Functions.logHeader( LOGGER, always, "list of inconsistent entries" );
                        sh = false;
                    }
                    LOGGER.error( "missing uniqueMember target '{}' in {}", um, dn );
                    continue;
                }

                if ( !entrym.get( um ).getMemberOf().contains( dn ) )
                {
                    missingMemberOf.put( dn, missingMemberOf.getOrDefault( dn, 0 ) + 1 );
                }
            }
        }

        Functions.logHeader( LOGGER, always, "consistency summary" );
        LOGGER.log( always, "                            dn entries : {}", entrym.size() );
        LOGGER.log( always, "                 existing uniqueMember : {}", entrym.values().

                stream().

                mapToInt( v -> v.getUniqueMember().

                        size() ).

                sum() );
        LOGGER.log( always, "                   referenced memberOf : {}", entrym.values().

                stream().

                mapToInt( v -> v.getMemberOf().

                        size() ).

                sum() );
        LOGGER.log( always, "                      missing memberOf : {}", missingMemberOf.values().

                stream().

                mapToInt( v -> v ).

                sum() );
    }
}
