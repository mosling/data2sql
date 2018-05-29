package com.sforce.cc.tools.data2sql;

import lombok.Getter;
import lombok.Setter;
import com.sforce.cc.tools.data2sql.splitter.CsvOptions;
import com.sforce.cc.tools.data2sql.splitter.LdifOptions;

import java.util.Collections;
import java.util.List;

public class MappingOptions
{
    // @formatter:off
    @Getter @Setter private LdifOptions          ldif                = new LdifOptions();
    @Getter @Setter private CsvOptions           csv                 = new CsvOptions();

    @Getter @Setter private List<String>         errorCountOnlyFor   = Collections.emptyList();
    @Getter @Setter private List<List<String>>   quoteOutputData     = Collections.emptyList();
    @Getter @Setter private boolean              shortErrorMsg       = false;
    @Getter @Setter private boolean              dataOnly            = false;
    // @formatter:on

    public MappingOptions()
    {
        // empty Constructor for Jackson
    }

    public String quoteData( String data )
    {
        String qData = data;

        for ( List<String> q : quoteOutputData )
        {
            if ( q.size() == 2 )
            {
                qData = qData.replace( q.get( 0 ), q.get( 1 ) );
            }
        }

        return qData;
    }
}
