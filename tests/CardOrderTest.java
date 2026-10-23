package com.jeremykenedy.firetv.ui;
import java.util.*;
public final class CardOrderTest {
 private static String key(char c){char[] x=new char[64];Arrays.fill(x,c);return new String(x);}
 private static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  String feed=key('a'),other=key('b'),one=key('1'),two=key('2'),three=key('3'),newItem=key('4');
  List<String> published=Arrays.asList(one,two,three);Set<String> saved=new HashSet<>();
  saved=CardOrderRules.move(saved,other,published,three,-1);Set<String> untouched=new HashSet<>(saved);
  saved=CardOrderRules.move(saved,feed,published,two,-1);
  check(CardOrderRules.ordered(published,saved,feed).equals(Arrays.asList(two,one,three)));
  check(saved.containsAll(untouched));
  check(CardOrderRules.ordered(Arrays.asList(three,one,newItem,two),saved,feed).equals(Arrays.asList(two,one,three,newItem)));
  check(CardOrderRules.ordered(Arrays.asList(one,newItem),saved,feed).equals(Arrays.asList(one,newItem)));
  saved=CardOrderRules.move(saved,feed,published,two,1);
  check(CardOrderRules.ordered(published,saved,feed).equals(published));
  for(String record:saved)check(CardOrderRules.valid(record)&&!record.contains("intent"));
  check(!CardOrderRules.valid(feed+":9999:"+one));
  try{CardOrderRules.move(saved,feed,published,newItem,-1);throw new AssertionError();}catch(IllegalArgumentException expected){}
 }
}
