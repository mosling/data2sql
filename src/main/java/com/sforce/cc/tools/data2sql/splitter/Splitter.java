package com.sforce.cc.tools.data2sql.splitter;

import com.sforce.cc.tools.data2sql.MappingResult;
import com.sforce.cc.tools.data2sql.MappingTable;

import java.util.List;
import java.util.Map;

public interface Splitter
{
    MappingResult execute( );

    String replaceTemplate( MappingTable mappingTable, String attribute, String data );

    String getErrorLocation( Map<String, List<String>> rowData);
}
