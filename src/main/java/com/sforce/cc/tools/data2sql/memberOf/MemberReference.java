package com.sforce.cc.tools.data2sql.memberOf;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MemberReference
{
    @Getter private int                  countEntries    = 0;
    @Getter private Map<String, Integer> uniqueMemberMap = new TreeMap<>();
    @Getter private Map<String, Integer> memberOfMap     = new TreeMap<>();

    private String getEntryRef( String e )
    {
        String[] s1 = e.split( ",", 2 );
        return s1.length > 1 ? s1[1] : e;
    }

    public void addEntryCount( int i )
    {
        countEntries += i;
    }

    public void addUniqueMember( List<String> l )
    {
        for ( String e : l )
        {
            String en = getEntryRef( e );
            uniqueMemberMap.put( en, uniqueMemberMap.getOrDefault( en, 0 ) + 1 );
        }
    }

    public void addMemberOf( List<String> l )
    {
        for ( String e : l )
        {
            String en = getEntryRef( e );
            memberOfMap.put( en, memberOfMap.getOrDefault( en, 0 ) + 1 );
        }
    }

    public int uniqueMemberSize()
    {
        return uniqueMemberMap.values().stream().mapToInt( Integer::intValue ).sum();
    }

    public int memberOfSize()
    {
        return memberOfMap.values().stream().mapToInt( Integer::intValue ).sum();
    }

}
