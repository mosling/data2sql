package com.sforce.cc.tools.data2sql.splitter;

import lombok.Getter;
import lombok.Setter;

public class CsvOptions
{

    // @formatter:off
    @Getter @Setter Character separator = ',';
    // @formatter:on

    public CsvOptions()
    {
        // default constructor for jackson
    }
}
