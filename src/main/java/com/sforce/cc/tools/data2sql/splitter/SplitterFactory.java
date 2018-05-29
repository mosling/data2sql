package com.sforce.cc.tools.data2sql.splitter;

import com.sforce.cc.tools.data2sql.Mapping;

import javax.annotation.Nullable;

public class SplitterFactory
{
    private SplitterFactory()
    {
        // builder class
    }

    public static @Nullable
    Splitter getSplitter( String name, Mapping mapping )
    {
        if ( "ldif".equalsIgnoreCase( name ) )
        {
            return new LdifSplitter( mapping );
        }
        else if ( "csv".equalsIgnoreCase( name ) )
        {
            return new CsvSplitter( mapping );
        }
        else
        {
            return null;
        }
    }
}
