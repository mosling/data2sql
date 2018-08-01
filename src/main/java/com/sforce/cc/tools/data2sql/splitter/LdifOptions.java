package com.sforce.cc.tools.data2sql.splitter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties( ignoreUnknown = true )
public class LdifOptions
{
    @Getter @Setter private String               tableNameAttribute  = "dn";
    @Getter @Setter private List<String>         fkEndings           = Collections.emptyList();
    @Getter @Setter private List<String>         ignoredAttributes   = Collections.emptyList();
    @Getter @Setter private Map<String, Boolean> referenceAttributes = Collections.emptyMap();
    @Getter @Setter private List<String>         metaAttributes      = Collections.emptyList();
    @Getter @Setter private List<String>         ignoredNodes        = Collections.emptyList();

    public LdifOptions()
    {
        // default constructor for jackson
    }

    boolean isLdifLink( String n )
    {
        if ( !fkEndings.isEmpty() )
        {
            for ( String s : fkEndings )
            {
                if ( n.endsWith( s ) )
                    return true;
            }
        }

        return false;
    }
}
