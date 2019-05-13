package com.sforce.cc.tools.data2sql.splitter;

import com.sforce.cc.tools.data2sql.MappingTable;
import org.checkerframework.checker.regex.RegexUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class DataModifier
{

    private DataModifier()
    {
        // helper class
    }

    //
    static List<String> modifyData( @Nullable MappingTable tableMapping, String attr, String data )
    {
        if ( data.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<String> dataList = new ArrayList<>();

        if ( tableMapping != null && tableMapping.getSplitData().containsKey( attr ) )
        {
            String rx = RegexUtil.asRegex( tableMapping.getSplitData().get( attr ) );
            dataList.addAll( Arrays.asList( data.split( rx ) ) );
        }
        else
        {
            dataList.add( data );
        }

        return dataList;
    }
}
