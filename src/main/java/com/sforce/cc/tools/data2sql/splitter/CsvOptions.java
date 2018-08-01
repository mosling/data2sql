package com.sforce.cc.tools.data2sql.splitter;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

public class CsvOptions
{

    @Getter @Setter private Character    separator    = ',';
    @Getter @Setter private boolean      skipFirstRow = false;
    @Getter @Setter private List<String> columnNames  = Collections.emptyList();

    public CsvOptions()
    {
        // default constructor for jackson
    }
}
