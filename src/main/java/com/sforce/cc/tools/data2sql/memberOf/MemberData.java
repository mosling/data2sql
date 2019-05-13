package com.sforce.cc.tools.data2sql.memberOf;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MemberData
{
    @Getter List<String> uniqueMember = new ArrayList<>();
    @Getter List<String> memberOf     = new ArrayList<>();
}
